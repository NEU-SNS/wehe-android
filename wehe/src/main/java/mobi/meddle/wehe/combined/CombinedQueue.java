package mobi.meddle.wehe.combined;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.bean.UpdateUIBean;


/**
 * This loads and de-serializes all necessary objects. Complicated. I'll have to
 * think what I did here. May be comments in python client can be helpful.
 */
public class CombinedQueue {

    // for indicating abortion!
    public volatile boolean ABORT = false;
    public volatile String abort_reason = null;
    // public volatile boolean done = false;
    int threads = 0;
    private CombinedAnalyzerTask analyzerTask;
    // public AtomicBoolean flag = new AtomicBoolean();
    private ArrayList<RequestSet> Q = null;
    private long timeOrigin;
    private long jitterTimeOrigin;
    private Semaphore sendSema = null;
    // ArrayList<Thread> threadList = new ArrayList<Thread>();
    private Map<CTCPClient, Semaphore> recvSemaMap = new HashMap<>();
    private ArrayList<Thread> cThreadList = new ArrayList<>();
    // for jitter
    private JitterBean jitterBean = null;

    public CombinedQueue(ArrayList<RequestSet> q, JitterBean jitterBean, CombinedAnalyzerTask analyzerTask) {
        super();
        this.Q = q;
        this.jitterBean = jitterBean;
        this.sendSema = new Semaphore(1);
        this.analyzerTask = analyzerTask;
        // this.flag.set(false);
    }

    /**
     * Python Client comments For every TCP packet: 1- Wait until client.event
     * is set --> client is not receiving a response 2- Send tcp payload [and
     * receive response] by calling next 3- Wait until send_event is set -->
     * sending is done
     *
     * @param timing
     * @throws Exception
     */
    public void run(UpdateUIBean updateUIBean, int iteration, int size,
                    HashMap<String, CTCPClient> CSPairMapping,
                    HashMap<String, CUDPClient> udpPortMapping,
                    UDPReplayInfoBean udpReplayInfoBean,
                    HashMap<String, HashMap<String, ServerInstance>> udpServerMapping,
                    Boolean timing, String server) {
        this.timeOrigin = System.nanoTime();
        this.jitterTimeOrigin = System.nanoTime();


        try {
            // for calculating packets
            int i = 1;
            // for jitter
            int m = 0;
            int len = this.Q.size();
            // @@@ start all the treads here
            for (RequestSet RS : this.Q) {

                if (RS.getResponse_len() == -1) {
                    // adrian: sending udp is done in queue thread, no need to
                    // start
                    // new threads for udp since there is only one port
                    Log.d("Replay", "Sending udp packet " + i + "/" + len
                            + " at time " + (System.nanoTime() - timeOrigin)
                            / 1000000);
                    nextUDP(RS, udpPortMapping, udpReplayInfoBean,
                            udpServerMapping, timing, server, m);
                    m++;

                    // adrian: for updating progress bar
                    int div = 100 / size;
                    int offset = (iteration - 1) * div;
                    updateUIBean.setProgress(offset + ((i++ * div) / len));

                } else {
                    Semaphore recvSema = getRecvSemaLock(CSPairMapping.get(RS
                            .getc_s_pair()));
                    // Log.d("Replay", "waiting to get receive semaphore!");
                    recvSema.acquire();
                    // Log.d("Replay", "got the receive semaphore!");

                    Log.d("Replay", "Sending tcp packet " + i + "/" + len
                            + " at time " + (System.nanoTime() - timeOrigin)
                            / 1000000);

                    // adrian: for updating progress bar
                    int div = 100 / size;
                    int offset = (iteration - 1) * div;
                    updateUIBean.setProgress(offset + ((i++ * div) / len));

                    // adrian: every time when calling next we create and start
                    // a new thread
                    // adrian: here we start different thread according to the
                    // type of RS
                    nextTCP(CSPairMapping.get(RS.getc_s_pair()), RS, timing,
                            sendSema, recvSema);

                    sendSema.acquire();

                }

                if (ABORT) {
                    Log.d("Queue", "replay aborted!");
                    break;
                }
            }

            Log.d("Queue", "waiting for all threads to die!");
            for (Thread t : cThreadList)
                t.join();

            Log.d("Queue",
                    "Finished executing all Threads "
                            + (System.nanoTime() - timeOrigin) / 1000000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    // adrian: this is the semaphore for receiving packet
    private Semaphore getRecvSemaLock(CTCPClient client) {
        Semaphore l = recvSemaMap.get(client);
        if (l == null) {
            l = new Semaphore(1);
            recvSemaMap.put(client, l);
        }
        return l;
    }

    /**
     * Call the client thread which will send the payload and receive the
     * response for RequestSet
     *
     * @param client
     * @param RS
     * @param timing
     */
    private void nextTCP(CTCPClient client, RequestSet RS, Boolean timing,
                         Semaphore sendSema, Semaphore recvSema) {

        // package this TCPClient into a TCPClientThread, then put it into a
        // thread
        CTCPClientThread clientThread = new CTCPClientThread(client, RS, this,
                sendSema, recvSema, timeOrigin, 100, analyzerTask);
        Thread cThread = new Thread(clientThread);

        // if timing is set to be true, wait until expected Time to send
        // this packet
        if (timing) {
            double expectedTime = timeOrigin + RS.getTimestamp() * 1000000000;
            if (System.nanoTime() < expectedTime) {
                long waitTime = Math.round(expectedTime - System.nanoTime()) / 1000000;
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        cThread.start();
        // threadList.add(cThread);
        ++threads;
        // Log.d("nextTCP", "number of thread: " + String.valueOf(threads));
        cThreadList.add(cThread);
    }

    private void nextUDP(RequestSet RS,
                         HashMap<String, CUDPClient> udpPortMapping,
                         UDPReplayInfoBean udpReplayInfoBean,
                         HashMap<String, HashMap<String, ServerInstance>> udpServerMapping,
                         Boolean timing, String server, int m) {
        String c_s_pair = RS.getc_s_pair();
        String clientPort = c_s_pair.substring(16, 21);
        String dstIP = c_s_pair.substring(22, 37);
        String dstPort = c_s_pair.substring(38, 43);
        /*
         * String destIP = c_s_pair.substring(c_s_pair.lastIndexOf('-') + 1,
         * c_s_pair.lastIndexOf(".")); String destPort =
         * c_s_pair.substring(c_s_pair.lastIndexOf('.') + 1, c_s_pair.length());
         */
        // Log.d("nextUDP", "dstIP: " + dstIP + " dstPort: " + dstPort);
        ServerInstance destAddr = udpServerMapping.get(dstIP).get(dstPort);

        if (destAddr.server.trim().equals(""))
            destAddr.server = server;

        CUDPClient client = udpPortMapping.get(clientPort);

        if (client.channel == null) {
            client.createSocket();
            udpReplayInfoBean.addSocket(client.channel);
            // Log.d("nextUDP", "read senderCount: " +
            // udpReplayInfoBean.getSenderCount());

        }

        if (timing) {
            double expectedTime = timeOrigin + RS.getTimestamp() * 1000000000;
            if (System.nanoTime() < expectedTime) {
                long waitTime = Math
                        .round((expectedTime - System.nanoTime()) / 1000000);
                // Log.d("Time", String.valueOf(waitTime));
                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // update sentJitter
        long currentTime = System.nanoTime();
        synchronized (jitterBean) {
            jitterBean.sentJitter
                    .add(String
                            .valueOf((double) (currentTime - jitterTimeOrigin) / 1000000000));
            jitterBean.sentPayload.add(RS.getPayload());
        }
        jitterTimeOrigin = currentTime;

        // adrian: send packet
        try {
            client.sendUDPPacket(RS.getPayload(), destAddr);
        } catch (Exception e) {
            Log.d("sendUDP", "something bad happened!");
            e.printStackTrace();
            ABORT = true;
            abort_reason = "Replay Aborted: " + e.getMessage();
        }

    }

}
