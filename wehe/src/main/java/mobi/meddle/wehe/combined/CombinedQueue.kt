package mobi.meddle.wehe.combined

import android.util.Log
import kotlinx.coroutines.isActive
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.JitterBean
import mobi.meddle.wehe.data.model.RequestSet
import mobi.meddle.wehe.data.model.ServerInstance
import mobi.meddle.wehe.data.model.UDPReplayInfoBean
import mobi.meddle.wehe.data.model.UpdateUIBean
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.Semaphore
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * This loads and de-serializes all necessary objects. Complicated. I'll have to
 * think what I did here. May be comments in python client can be helpful.
 *
 *
 * What this does according to another guy who got bored during the pandemic and decided to add more
 * documentation to this project: prepares the packets to be sent to the server. For UDP, calls
 * CUDPClient to send packets. For TCP, calls CTCPClientThread, which calls CTCPClient to send the
 * packets.
 */
class CombinedQueue(//packets to send to server
    private val q: ArrayList<RequestSet>, // for jitter
    private var jitterBeans: ArrayList<JitterBean>,
    //class that tracks throughput data
    private val analyzerTasks: ArrayList<CombinedAnalyzerTask>, timeout: Int
) {
    @Volatile
    var ABORT: Boolean = false // for indicating abortion!

    @Volatile
    var abort_reason: String? = null
    private var timeOrigin: Long = 0 //start time of replay
    private val jitterTimeOrigins = ArrayList<Long>() //start time of a UDP packet
    private val sendSema: Semaphore = Semaphore(1) //for TCP
    private val recvSemaMap: MutableMap<CTCPClient?, Semaphore> = HashMap() //for TCP
    var threads: Int = 0 //number of TCP threads currently active
    private val cThreadList = ArrayList<Thread>() //list of TCP threads
    private val isUDP: Boolean = q.size > 0 && q[0].isUDP()
    private val timeout: Int = if (isUDP) timeout - 5 else timeout
    private val timers = ArrayList<Timer>()
//
//    fun setAbort() {
//        ABORT = true
//    }

    /**
     * This method is where the packets begin sending to the servers (throughputs can finally now
     * be collected).
     * Python Client comments For every TCP packet: 1- Wait until client.event
     * is set --> client is not receiving a response 2- Send tcp payload [and
     * receive response] by calling next 3- Wait until send_event is set -->
     * sending is done
     *
     * @param updateUIBean       bean to update progress bar
     * @param numReplays         number of replays in the test - used to correctly determine position
     * of progress bar
     * @param CSPairMappings     maps of cs pairs to TCP connections for a replay
     * @param udpPortMappings    maps of client ports to UDP connections for a replay
     * @param udpReplayInfoBeans beans for UDP info - used to add a socket
     * @param udpServerMappings  the UDP IP-to-port mappings from the giant list received from the
     * server in receivePortMappingNonBlock() from CombinedSideChannel
     * @param timing             true if the packets should be sent at the specified recorded time;
     * false if packets should be sent as fast as possible
     * @param servers            the IP address of the server - used as the server if the server
     * field in the ServerInstance is blank
     */
    fun run(
        updateUIBean: UpdateUIBean, numReplays: Int,
        CSPairMappings: ArrayList<HashMap<String, CTCPClient>>,
        udpPortMappings: ArrayList<HashMap<String, CUDPClient>>,
        udpReplayInfoBeans: ArrayList<UDPReplayInfoBean>,
        udpServerMappings: ArrayList<HashMap<String, HashMap<String, ServerInstance>>>,
        timing: Boolean, servers: ArrayList<String?>, coroutineScope: CoroutineContext
    ) {
        val curTime = System.nanoTime()
        this.timeOrigin = curTime
        for (i in jitterBeans.indices) {
            jitterTimeOrigins.add(curTime)
        }

        // @@@ start all the treads here
        val tests = ArrayList<Thread>()
        val id_global = intArrayOf(-1)
        val sendPckts = Runnable()
        //begin packet sending
        {
            id_global[0]++
            val id = id_global[0]
            var i = 1 // for calculating packets
            val numPackets = q.size // for jitter
            val numParallelTests = servers.size //num tests running at same time
            //amount to increase progress bar every time a packet sends
            val prgBarInc = 100.0 / (numReplays * numPackets * numParallelTests)
            var currentTime: Double //time in seconds since timeOrigin
            var timeLeft = 30 //time in seconds until timeout for sending packets
            for (RS in q) { //send packets one at a time to the server (RS is a packet)
                //TODO: find a better way to cancel than from passing in entire AsyncTask
                // Currently, this method is blocking, so code in AsyncTask in Replay Activity can't
                // cancel this for loop from sending packets to the server
                if (!coroutineScope.isActive || ABORT) {
                    Log.i("Queue", "Channel $id: replay aborted!")
                    break
                }
                currentTime =
                    (System.nanoTime() - timeOrigin).toDouble() / 1000000000.0 //nano sec to sec

                //check for timeout sending packets
                if (Consts.TIMEOUT_ENABLED && currentTime > timeout) {
                    Log.i(
                        "Queue", ("Channel " + id + ": " + timeout
                                + " second timeout reached for replay at time " + System.nanoTime())
                    )
                    break
                }

                updateUIBean.addProgress(prgBarInc) //update progress bar
                try {
                    if (isUDP) { //UDP
                        // adrian: sending udp is done in queue thread, no need to start
                        // new threads for udp since there is only one port
                        Log.i(
                            "Replay", ("Channel " + id + ": Sending udp packet "
                                    + i++ + "/" + numPackets + " at " + currentTime
                                    + " seconds since start of replay")
                        )
                        nextUDP(
                            id, RS, udpPortMappings[id], udpReplayInfoBeans[id],
                            udpServerMappings[id], timing, servers[id]
                        )
                    } else { //TCP
                        //calculate time left to send - this becomes new timeout for socket for
                        //receiving response
                        if (Consts.TIMEOUT_ENABLED) {
                            timeLeft = timeout - currentTime.toInt()
                            if (timeLeft <= 0) {
                                timeLeft = 1
                            }
                        }
                        val recvSema = getRecvSemaLock(CSPairMappings[id][RS.cSPair])
                        recvSema.acquire()

                        Log.i(
                            "Replay", ("Channel " + id + ": Sending tcp packet "
                                    + i++ + "/" + numPackets + " at " + currentTime
                                    + " seconds since start of replay")
                        )

                        // adrian: every time when calling next we create and start a new thread
                        // adrian: here we start different thread according to the type of RS
                        nextTCP(
                            CSPairMappings[id][RS.cSPair], RS, timing,
                            sendSema, recvSema, timeLeft, analyzerTasks[id]
                        )

                        sendSema.acquire()
                    }
                } catch (e: InterruptedException) {
                    Log.e("Replay", "Error sending packet", e)
                }
            }
        }

        //each server gets its own thread; if normal tests, there's only one server, but tomography
        //tests require multiple servers, running the same tests at the same time
        for (i in servers.indices) {
            val test = Thread(sendPckts)
            test.start()
            tests.add(test)
        }

        try {
            for (t in tests) { //wait for all packets to be send to all servers before continuing
                if (Consts.TIMEOUT_ENABLED) {
                    t.join(timeout * 1000L)
                } else {
                    t.join()
                }
            }
        } catch (e: InterruptedException) {
            Log.e("Queue", "Can't join test threads", e)
        }

        //all tests done
        Log.i("Queue", "waiting for all threads to die!" + System.nanoTime())

        var timeLeft = 1

        if (Consts.TIMEOUT_ENABLED) { //make sure joining thread doesn't wait past timeout
            val currentTime =
                (System.nanoTime() - timeOrigin).toDouble() / 1000000000.0 //nano sec to sec
            timeLeft = timeout - currentTime.toInt()
            if (timeLeft <= 0) {
                timeLeft = 1
            }
        }

        try {
            for (t in cThreadList) {
                if (Consts.TIMEOUT_ENABLED) {
                    t.join((timeLeft * 1000).toLong()) //make sure thread isn't waiting to join forever
                } else {
                    t.join()
                }
            }

            Log.i(
                "Queue", ("Finished executing all Threads "
                        + (System.nanoTime() - timeOrigin).toDouble() / 1000000000.0 + " sec " + System.nanoTime())
            )
        } catch (e: NullPointerException) {
            Log.e("Queue", "Can't join thread", e)
        } catch (e: InterruptedException) {
            Log.e("Queue", "Can't join thread", e)
        }
    }

    fun stopTimers() {
        synchronized(timers) {
            for (t in timers) {
                t.cancel()
            }
        }
    }

    // adrian: this is the semaphore for receiving packet
    private fun getRecvSemaLock(client: CTCPClient?): Semaphore {
        var l = recvSemaMap[client]
        if (l == null) {
            l = Semaphore(1)
            recvSemaMap[client] = l
        }
        return l
    }

    /**
     * Call the client thread which will send the next TCP payload and receive the
     * response for RequestSet.
     *
     * @param client       the TCP connection to the server
     * @param rs           the packet to send
     * @param timing       true if the packet should be sent at the specified time, false if the packet
     * should be sent as soon as possible
     * @param sendSema     Semaphore for sending packets
     * @param recvSema     Semaphore for receiving packets
     * @param timeLeft     number of seconds left until timeout sending packets
     * @param analyzerTask the class that keeps track of the throughput data
     */
    private fun nextTCP(
        client: CTCPClient?, rs: RequestSet, timing: Boolean,
        sendSema: Semaphore, recvSema: Semaphore, timeLeft: Int,
        analyzerTask: CombinedAnalyzerTask
    ) {
        // package this TCPClient into a TCPClientThread, then put it into a thread
        var timeLeft = timeLeft
        val clientThread = CTCPClientThread(
            client, rs, this,
            sendSema, recvSema, 100, analyzerTask
        )
        val cThread = Thread(clientThread)

        // if timing is set to be true, wait until expected Time to send this packet
        if (timing) {
            val expectedTime = timeOrigin + rs.timestamp * 1000000000
            if (System.nanoTime() < expectedTime) {
                val waitTime = (Math.round(expectedTime - System.nanoTime()) / 1000000).toInt() //ms
                timeLeft -= (waitTime / 1000)
                if (timeLeft <= 0) {
                    timeLeft = 1
                }
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime.toLong())
                    } catch (e: InterruptedException) {
                        Log.w("nextTCP", "Sleep interrupted", e)
                    }
                }
            }
        }

        cThread.start()
        val t = Timer()
        t.schedule(object : TimerTask() {
            override fun run() { //set timer to timeout the thread if max time has been reached for replay
                clientThread.timeout()
            }
        }, timeLeft * 1000L)
        ++threads
        // Log.d("nextTCP", "number of thread: " + String.valueOf(threads));
        cThreadList.add(cThread)
        synchronized(timers) {
            timers.add(t)
        }
    }

    /**
     * Sends the next UDP packet.
     *
     * @param id                the id of current test
     * @param rs                the next UDP packet
     * @param udpPortMapping    map of client ports to UDP connections for the replay
     * @param udpReplayInfoBean bean containing info about the replay - used to add a socket
     * @param udpServerMapping  the UDP IP-to-port mappings from the giant list received from the
     * server in receivePortMappingNonBlock() from CombinedSideChannel
     * @param timing            true if the packet should be sent at the specified time;
     * false if the packet should be sent ASAP
     * @param server            the IP address of the server - used as the server if the server
     * field in the ServerInstance is blank
     * @throws InterruptedException for Thread.sleep() when waiting to send packet if timing is true
     */
    @Throws(InterruptedException::class)
    private fun nextUDP(
        id: Int, rs: RequestSet, udpPortMapping: java.util.HashMap<String, CUDPClient>,
        udpReplayInfoBean: UDPReplayInfoBean,
        udpServerMapping: java.util.HashMap<String, java.util.HashMap<String, ServerInstance>>,
        timing: Boolean, server: String?
    ) {
        //get the client/server IP and port info from the cs pair
        val c_s_pair = rs.cSPair
        val client_ip_port = c_s_pair?.split("-".toRegex())?.dropLastWhile { it.isEmpty() }
            ?.toTypedArray()?.get(0)
        val server_ip_port = c_s_pair?.split("-".toRegex())?.dropLastWhile { it.isEmpty() }
            ?.toTypedArray()?.get(1)
        val clientPort = client_ip_port?.substring(client_ip_port.lastIndexOf(".") + 1)
        val dstPort = server_ip_port?.substring(server_ip_port.lastIndexOf(".") + 1)
        val dstIP = server_ip_port?.substring(0, server_ip_port.lastIndexOf("."))
        Log.d("nextUDP", "dstIP: $dstIP dstPort: $dstPort")
        //get the server
        val destAddr = checkNotNull(
                udpServerMapping[dstIP]?.get(dstPort)
        )

        if (destAddr.server.trim { it <= ' ' } == "") {
            if (server != null) {
                destAddr.server = server
            }
        }

        //get the correct connection to the server
        val client = checkNotNull(udpPortMapping[clientPort])

        if (client.channel == null) {
            client.createSocket()
            udpReplayInfoBean.addSocket(client.channel)
            // Log.d("nextUDP", "read senderCount: " + udpReplayInfoBean.getSenderCount());
        }

        //wait for the correct time to send packet if timing is true
        if (timing) {
            val expectedTime = timeOrigin + rs.timestamp * 1000000000
            if (System.nanoTime() < expectedTime) {
                val waitTime = Math.round((expectedTime - System.nanoTime()) / 1000000)
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime)
                    } catch (e: InterruptedException) {
                        throw InterruptedException()
                    }
                }
            }
        }

        // update sentJitter
        val currentTime = System.nanoTime()
        synchronized(jitterBeans[id]) {
            jitterBeans[id].sentJitter.add(((currentTime - jitterTimeOrigins[id]).toDouble() / 1000000000).toString())
            jitterBeans[id].sentPayload.add(rs.payload)
        }
        jitterTimeOrigins[id] = currentTime

        // adrian: send packet
        try {
            client.sendUDPPacket(rs.payload, destAddr)
        } catch (e: Exception) {
            Log.w("sendUDP", "something bad happened!", e)
            ABORT = true
            abort_reason = "Replay Aborted: " + e.message
        }
    }
}
