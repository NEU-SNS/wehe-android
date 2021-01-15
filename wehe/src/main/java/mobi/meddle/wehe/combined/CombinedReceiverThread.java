package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;

/**
 * Receives the UDP throughputs.
 */
public final class CombinedReceiverThread implements Runnable {
    // changes of Arash
    public volatile boolean keepRunning = true;
    private final UDPReplayInfoBean udpReplayInfoBean;

    private final JitterBean jitterBean; // adrian: for jitter
    private final CombinedAnalyzerTask analyzerTask;

    /**
     * Constructor.
     *
     * @param udpReplayInfoBean bean containing information about a UDP replay - used to get sockets
     *                          to connect to
     * @param jitterBean        bean to track UDP packets sent to and received from server
     * @param analyzerTask      the class containing the throughput data
     */
    public CombinedReceiverThread(UDPReplayInfoBean udpReplayInfoBean,
                                  JitterBean jitterBean, CombinedAnalyzerTask analyzerTask) {
        this.udpReplayInfoBean = udpReplayInfoBean;
        this.jitterBean = jitterBean;
        this.analyzerTask = analyzerTask;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("CombinedReceiverThread (Thread)");
        long jitterTimeOrigin = System.nanoTime();

        try {
            Selector selector = Selector.open();
            int bufSize = 4096;
            ByteBuffer buf = ByteBuffer.allocate(bufSize);
            while (keepRunning) {
                // Log.d("Receiver", "size of udpSocketList: " +
                // udpReplayInfoBean.getUdpSocketList().size());

                for (DatagramChannel channel : udpReplayInfoBean.getUdpSocketList()) {
                    channel.register(selector, SelectionKey.OP_READ);
                }

                // Log.d("Receiver", "senderCount: " + udpReplayInfoBean.getSenderCount());
                int TIME_OUT = 1000; //run every 1 second
                int sks = selector.select(TIME_OUT);
                if (sks == 0) {
                    // Log.d("Receiver", "no socket has data");
                    continue;
                }
                // Log.d("Receiver", "got it!");

                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    DatagramChannel tmpChannel = (DatagramChannel) key.channel();

                    if (tmpChannel.receive(buf) != null) {
                        int bp = buf.position();
                        byte[] data = new byte[bp];
                        buf.position(0);
                        buf.get(data);

                        analyzerTask.bytesRead += data.length; //add to throughput data

                        // for receive jitter
                        long currentTime = System.nanoTime();

                        synchronized (jitterBean) {
                            jitterBean.rcvdJitter.add(String
                                    .valueOf((double) (currentTime - jitterTimeOrigin) / 1000000000));
                            jitterBean.rcvdPayload.add(data);
                            // Log.d("Receiver", String.valueOf(jitterBean.rcvdJitter.size()));
                        }
                        jitterTimeOrigin = currentTime;
                    }
                    selectedKeys.remove();
                }
                buf.clear();
            }
            selector.close();
        } catch (IOException e) {
            Log.w("Receiver", "receiving udp packet error!", e);
        }

        Log.d("Receiver", "finished! Packets received: " + jitterBean.rcvdJitter.size());
    }
}
