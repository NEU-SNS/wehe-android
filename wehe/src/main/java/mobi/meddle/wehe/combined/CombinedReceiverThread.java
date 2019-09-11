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


public final class CombinedReceiverThread implements Runnable {

    // changes of Arash
    public volatile boolean keepRunning = true;
    private UDPReplayInfoBean udpReplayInfoBean = null;
    private int bufSize = 4096;
    private long jitterTimeOrigin = 0;
    private int TIME_OUT = 1000;

    // adrian: for jitter
    private JitterBean jitterBean = null;

    private CombinedAnalyzerTask analyzerTask;

    public CombinedReceiverThread(UDPReplayInfoBean udpReplayInfoBean,
                                  JitterBean jitterBean, CombinedAnalyzerTask analyzerTask) {
        super();
        this.udpReplayInfoBean = udpReplayInfoBean;
        this.jitterBean = jitterBean;
        this.analyzerTask = analyzerTask;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("CombinedReceiverThread (Thread)");
        this.jitterTimeOrigin = System.nanoTime();

        try {
            Selector selector = Selector.open();
            ByteBuffer buf = ByteBuffer.allocate(bufSize);
            //byte[] buff = new byte[bufSize];

            while (keepRunning) {

                /*
                 * Log.d("Receiver", "size of udpSocketList: " +
                 * udpReplayInfoBean.getUdpSocketList().size());
                 */

                for (DatagramChannel channel : udpReplayInfoBean
                        .getUdpSocketList()) {
                    channel.register(selector, SelectionKey.OP_READ);
                }

                // Log.d("Receiver", "senderCount: " +
                // udpReplayInfoBean.getSenderCount());
                if (selector.select(TIME_OUT) == 0) {
                    // Log.d("Receiver", "no socket has data");
                    continue;
                }
                // Log.d("Receiver", "got it!");

                Iterator<SelectionKey> selectedKeys = selector.selectedKeys()
                        .iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key = selectedKeys.next();
                    DatagramChannel tmpChannel = (DatagramChannel) key
                            .channel();

                    if (tmpChannel.receive(buf) != null) {
                        byte[] data = new byte[buf.position()];
                        buf.position(0);
                        buf.get(data);

                        analyzerTask.bytesRead += data.length;

                        // for receive jitter
                        long currentTime = System.nanoTime();

                        synchronized (jitterBean) {
                            jitterBean.rcvdJitter
                                    .add(String
                                            .valueOf((double) (currentTime - jitterTimeOrigin) / 1000000000));
                            jitterBean.rcvdPayload.add(data);
                            // Log.d("Receiver",
                            // String.valueOf(jitterBean.rcvdJitter.size()));
                        }
                        this.jitterTimeOrigin = currentTime;
                    }
                    selectedKeys.remove();
                }

                buf.clear();
            }

            selector.close();
        } catch (IOException e1) {
            Log.d("Receiver", "receiving udp packet error!");
            e1.printStackTrace();
        }

        Log.d("Receiver",
                "finished! Packets received: "
                        + jitterBean.rcvdJitter.size());
    }
}
