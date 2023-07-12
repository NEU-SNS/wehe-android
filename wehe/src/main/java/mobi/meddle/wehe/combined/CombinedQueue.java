package mobi.meddle.wehe.combined;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.bean.UpdateUIBean;
import mobi.meddle.wehe.constant.Consts;

/**
 * This loads and de-serializes all necessary objects. Complicated. I'll have to
 * think what I did here. May be comments in python client can be helpful.
 * <p>
 * What this does according to another guy who got bored during the pandemic and decided to add more
 * documentation to this project: prepares the packets to be sent to the server. For UDP, calls
 * CUDPClient to send packets. For TCP, calls CTCPClientThread, which calls CTCPClient to send the
 * packets.
 */
public class CombinedQueue {
    volatile boolean ABORT = false; // for indicating abortion!
    volatile String abort_reason = null;
    private final ArrayList<CombinedAnalyzerTask> analyzerTasks; //class that tracks throughput data
    private final ArrayList<RequestSet> q; //packets to send to server
    private long timeOrigin; //start time of replay
    private final ArrayList<Long> jitterTimeOrigins = new ArrayList<>(); //start time of a UDP packet
    private final Semaphore sendSema; //for TCP
    private final Map<CTCPClient, Semaphore> recvSemaMap = new HashMap<>(); //for TCP
    int threads = 0; //number of TCP threads currently active
    private final ArrayList<Thread> cThreadList = new ArrayList<>(); //list of TCP threads
    private final ArrayList<JitterBean> jitterBeans; // for jitter
    private final boolean isUDP;
    private final int timeout;
    private final ArrayList<Timer> timers = new ArrayList<>();
    private final Semaphore stopSema; // semaphore needed when trying to stop the replays

    /**
     * Constructor.
     *
     * @param q             the list of packets to send to the server
     * @param jitterBeans   beans to keep track of UDP packets sent to and received from the server
     * @param analyzerTasks the class that keeps track of the throughput data
     * @param timeout       max number of seconds a TCP replay can send packets (5 sec less for UDP)
     */
    public CombinedQueue(ArrayList<RequestSet> q, ArrayList<JitterBean> jitterBeans,
                         ArrayList<CombinedAnalyzerTask> analyzerTasks, int timeout) {
        this.q = q;
        this.jitterBeans = jitterBeans;
        this.sendSema = new Semaphore(1);
        this.analyzerTasks = analyzerTasks;
        this.isUDP = q.size() > 0 && q.get(0).isUDP();
        this.timeout = isUDP ? timeout - 5 : timeout;
        this.stopSema = new Semaphore(1);
    }

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
     *                           of progress bar
     * @param CSPairMappings     maps of cs pairs to TCP connections for a replay
     * @param udpPortMappings    maps of client ports to UDP connections for a replay
     * @param udpReplayInfoBeans beans for UDP info - used to add a socket
     * @param udpServerMappings  the UDP IP-to-port mappings from the giant list received from the
     *                           server in receivePortMappingNonBlock() from CombinedSideChannel
     * @param timing             true if the packets should be sent at the specified recorded time;
     *                           false if packets should be sent as fast as possible
     * @param servers            the IP address of the server - used as the server if the server
     *                           field in the ServerInstance is blank
     * @param caller             the caller AsyncTask - used to stop sending packets if user cancels
     */
    public void run(UpdateUIBean updateUIBean, int numReplays,
                    ArrayList<HashMap<String, CTCPClient>> CSPairMappings,
                    ArrayList<HashMap<String, CUDPClient>> udpPortMappings,
                    ArrayList<UDPReplayInfoBean> udpReplayInfoBeans,
                    ArrayList<HashMap<String, HashMap<String, ServerInstance>>> udpServerMappings,
                    Boolean timing, ArrayList<String> servers, AsyncTask<String, String, Void> caller) {
        long curTime = System.nanoTime();
        this.timeOrigin = curTime;
        for (int i = 0; i < jitterBeans.size(); i++) {
            this.jitterTimeOrigins.add(curTime);
        }

        // @@@ start all the treads here
        ArrayList<Thread> tests = new ArrayList<>();
        final int[] id_global = {-1};
        Runnable sendPckts = new Runnable() { //begin packet sending
            @Override
            public void run() {
                id_global[0]++;
                int id = id_global[0];
                int i = 1; // for calculating packets
                int numPackets = q.size(); // for jitter
                int numParallelTests = servers.size(); //num tests running at same time
                //amount to increase progress bar every time a packet sends
                double prgBarInc = 100.0 / (numReplays * numPackets * numParallelTests);
                double currentTime; //time in seconds since timeOrigin
                int timeLeft = 30; //time in seconds until timeout for sending packets
                for (RequestSet RS : q) { //send packets one at a time to the server (RS is a packet)
                    //TODO: find a better way to cancel than from passing in entire AsyncTask
                    // Currently, this method is blocking, so code in AsyncTask in Replay Activity can't
                    // cancel this for loop from sending packets to the server
                    if (caller.isCancelled() || ABORT) {
                        Log.i("Queue", "Channel " + id + ": replay aborted!");
                        break;
                    }
                    currentTime = (double) (System.nanoTime() - timeOrigin) / 1000000000.0; //nano sec to sec

                    //check for timeout sending packets
                    if (Consts.TIMEOUT_ENABLED && currentTime > timeout) {
                        Log.i("Queue", "Channel " + id + ": " + timeout
                                + " second timeout reached for replay at time " + System.nanoTime());
                        break;
                    }

                    updateUIBean.addProgress(prgBarInc); //update progress bar
                    try {
                        if (isUDP) { //UDP
                            // adrian: sending udp is done in queue thread, no need to start
                            // new threads for udp since there is only one port
                            Log.i("Replay", "Channel " + id + ": Sending udp packet "
                                    + i++ + "/" + numPackets + " at " + currentTime
                                    + " seconds since start of replay");
                            nextUDP(id, RS, udpPortMappings.get(id), udpReplayInfoBeans.get(id),
                                    udpServerMappings.get(id), timing, servers.get(id));
                        } else { //TCP
                            //calculate time left to send - this becomes new timeout for socket for
                            //receiving response
                            if (Consts.TIMEOUT_ENABLED) {
                                timeLeft = timeout - (int) currentTime;
                                if (timeLeft <= 0) {
                                    timeLeft = 1;
                                }
                            }
                            Semaphore recvSema = getRecvSemaLock(CSPairMappings.get(id).get(RS.getc_s_pair()));
                            recvSema.acquire();

                            Log.i("Replay", "Channel " + id + ": Sending tcp packet "
                                    + i++ + "/" + numPackets + " at " + currentTime
                                    + " seconds since start of replay");

                            // adrian: every time when calling next we create and start a new thread
                            // adrian: here we start different thread according to the type of RS
                            stopSema.acquire();
                            nextTCP(CSPairMappings.get(id).get(RS.getc_s_pair()), RS, timing,
                                    sendSema, recvSema, timeLeft, analyzerTasks.get(id));
                            stopSema.release();

                            sendSema.acquire();
                        }
                    } catch (InterruptedException e) {
                        Log.e("Replay", "Error sending packet", e);
                    }

                }
            }
        };

        //each server gets its own thread; if normal tests, there's only one server, but tomography
        //tests require multiple servers, running the same tests at the same time
        for (int i = 0; i < servers.size(); i++) {
            Thread test = new Thread(sendPckts);
            test.start();
            tests.add(test);
        }

        try {
            for (Thread t : tests) { //wait for all packets to be send to all servers before continuing
                if (Consts.TIMEOUT_ENABLED) {
                    t.join(timeout * 1000L);
                } else {
                    t.join();
                }
            }
        } catch (InterruptedException e) {
            Log.e("Queue", "Can't join test threads", e);
        }

        //all tests done
        Log.i("Queue", "waiting for all threads to die!" + System.nanoTime());

        int timeLeft;
        if (Consts.TIMEOUT_ENABLED) { //make sure joining thread doesn't wait past timeout
            double currentTime = (double) (System.nanoTime() - timeOrigin) / 1000000000.0; //nano sec to sec
            timeLeft = timeout - (int) currentTime;
            if (timeLeft <= 0) {
                timeLeft = 1;
            }
        }

        try {
            for (Thread t : cThreadList) {
                if (Consts.TIMEOUT_ENABLED) {
                    t.join(timeLeft * 1000); //make sure thread isn't waiting to join forever
                } else {
                    t.join();
                }
            }

            Log.i("Queue", "Finished executing all Threads "
                    + (double) (System.nanoTime() - timeOrigin) / 1000000000.0 + " sec " + System.nanoTime());
        } catch (NullPointerException | InterruptedException e) {
            Log.e("Queue", "Can't join thread", e);
        }

    }

    public void stopTimers() {
        try {
            stopSema.acquire();
            for (Timer t : timers) {
                t.cancel();
            }
            stopSema.release();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // adrian: this is the semaphore for receiving packet
    @NonNull
    private Semaphore getRecvSemaLock(CTCPClient client) {
        Semaphore l = recvSemaMap.get(client);
        if (l == null) {
            l = new Semaphore(1);
            recvSemaMap.put(client, l);
        }
        return l;
    }

    /**
     * Call the client thread which will send the next TCP payload and receive the
     * response for RequestSet.
     *
     * @param client       the TCP connection to the server
     * @param rs           the packet to send
     * @param timing       true if the packet should be sent at the specified time, false if the packet
     *                     should be sent as soon as possible
     * @param sendSema     Semaphore for sending packets
     * @param recvSema     Semaphore for receiving packets
     * @param timeLeft     number of seconds left until timeout sending packets
     * @param analyzerTask the class that keeps track of the throughput data
     */
    private void nextTCP(CTCPClient client, RequestSet rs, @NonNull Boolean timing,
                         Semaphore sendSema, Semaphore recvSema, int timeLeft,
                         CombinedAnalyzerTask analyzerTask) {
        // package this TCPClient into a TCPClientThread, then put it into a thread
        CTCPClientThread clientThread = new CTCPClientThread(client, rs, this,
                sendSema, recvSema, 100, analyzerTask);
        Thread cThread = new Thread(clientThread);

        // if timing is set to be true, wait until expected Time to send this packet
        if (timing) {
            double expectedTime = timeOrigin + rs.getTimestamp() * 1000000000;
            if (System.nanoTime() < expectedTime) {
                int waitTime = (int) (Math.round(expectedTime - System.nanoTime()) / 1000000); //ms
                timeLeft -= (waitTime / 1000);
                if (timeLeft <= 0) {
                    timeLeft = 1;
                }
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        Log.w("nextTCP", "Sleep interrupted", e);
                    }
                }
            }
        }

        cThread.start();
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() { //set timer to timeout the thread if max time has been reached for replay
                clientThread.timeout();
            }
        }, timeLeft * 1000L);
        ++threads;
        // Log.d("nextTCP", "number of thread: " + String.valueOf(threads));
        cThreadList.add(cThread);
        timers.add(t);
    }

    /**
     * Sends the next UDP packet.
     *
     * @param id                the id of current test
     * @param rs                the next UDP packet
     * @param udpPortMapping    map of client ports to UDP connections for the replay
     * @param udpReplayInfoBean bean containing info about the replay - used to add a socket
     * @param udpServerMapping  the UDP IP-to-port mappings from the giant list received from the
     *                          server in receivePortMappingNonBlock() from CombinedSideChannel
     * @param timing            true if the packet should be sent at the specified time;
     *                          false if the packet should be sent ASAP
     * @param server            the IP address of the server - used as the server if the server
     *                          field in the ServerInstance is blank
     * @throws InterruptedException for Thread.sleep() when waiting to send packet if timing is true
     */
    private void nextUDP(int id, @NonNull RequestSet rs, HashMap<String, CUDPClient> udpPortMapping,
                         UDPReplayInfoBean udpReplayInfoBean,
                         @NonNull HashMap<String, HashMap<String, ServerInstance>> udpServerMapping,
                         Boolean timing, String server) throws InterruptedException {
        //get the client/server IP and port info from the cs pair
        String c_s_pair = rs.getc_s_pair();
        String client_ip_port = c_s_pair.split("-")[0];
        String server_ip_port = c_s_pair.split("-")[1];
        String clientPort = client_ip_port.substring(client_ip_port.lastIndexOf(".") + 1);
        String dstPort = server_ip_port.substring(server_ip_port.lastIndexOf(".") + 1);
        String dstIP = server_ip_port.substring(0, server_ip_port.lastIndexOf("."));
        Log.d("nextUDP", "dstIP: " + dstIP + " dstPort: " + dstPort);
        //get the server
        ServerInstance destAddr = Objects.requireNonNull(udpServerMapping.get(dstIP)).get(dstPort);

        assert destAddr != null;
        if (destAddr.server.trim().equals("")) {
            destAddr.server = server;
        }

        //get the correct connection to the server
        CUDPClient client = udpPortMapping.get(clientPort);

        assert client != null;
        if (client.channel == null) {
            client.createSocket();
            udpReplayInfoBean.addSocket(client.channel);
            // Log.d("nextUDP", "read senderCount: " + udpReplayInfoBean.getSenderCount());
        }

        //wait for the correct time to send packet if timing is true
        if (timing) {
            double expectedTime = timeOrigin + rs.getTimestamp() * 1000000000;
            if (System.nanoTime() < expectedTime) {
                long waitTime = Math.round((expectedTime - System.nanoTime()) / 1000000);
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        throw new InterruptedException();
                    }
                }
            }
        }

        // update sentJitter
        long currentTime = System.nanoTime();
        synchronized (jitterBeans.get(id)) {
            jitterBeans.get(id).sentJitter.add(String
                    .valueOf((double) (currentTime - jitterTimeOrigins.get(id)) / 1000000000));
            jitterBeans.get(id).sentPayload.add(rs.getPayload());
        }
        jitterTimeOrigins.set(id, currentTime);

        // adrian: send packet
        try {
            client.sendUDPPacket(rs.getPayload(), destAddr);
        } catch (Exception e) {
            Log.w("sendUDP", "something bad happened!", e);
            ABORT = true;
            abort_reason = "Replay Aborted: " + e.getMessage();
        }
    }
}
