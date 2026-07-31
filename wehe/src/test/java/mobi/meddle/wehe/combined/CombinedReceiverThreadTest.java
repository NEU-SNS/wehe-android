package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import mobi.meddle.wehe.data.model.JitterBean;
import mobi.meddle.wehe.data.model.UDPReplayInfoBean;

/**
 * Tests for CombinedReceiverThread (mobi.meddle.wehe.combined.CombinedReceiverThread, main
 * sources).
 * <p>
 * run() is a real blocking select() loop meant to live on a background Thread, so it is tested
 * with a real java.nio DatagramChannel bound on loopback and a real background Thread (per the
 * suggested strategy of testing thread lifecycle with real Thread objects and short timeouts,
 * rather than mocking Thread/Selector/DatagramChannel).
 */
public class CombinedReceiverThreadTest {

    private DatagramChannel channel;
    private DatagramSocket sender;
    private Thread bgThread;

    @After
    public void tearDown() throws IOException, InterruptedException {
        if (bgThread != null && bgThread.isAlive()) {
            bgThread.interrupt();
            bgThread.join(2000);
        }
        if (sender != null && !sender.isClosed()) {
            sender.close();
        }
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    @Test
    public void run_receivesUdpPacket_updatesAnalyzerTaskAndJitterBean() throws Exception {
        channel = DatagramChannel.open();
        channel.socket().bind(new InetSocketAddress("127.0.0.1", 0));

        UDPReplayInfoBean bean = new UDPReplayInfoBean();
        bean.addSocket(channel);
        JitterBean jitterBean = new JitterBean();
        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(10, false, 5, false);

        CombinedReceiverThread receiver = new CombinedReceiverThread(bean, jitterBean, analyzerTask);

        // send the packet BEFORE starting the receiver so the first selector.select(1000) call
        // returns almost immediately instead of waiting out the full 1-second timeout.
        byte[] payload = "udp-payload-1234".getBytes();
        sender = new DatagramSocket();
        sender.send(new DatagramPacket(payload, payload.length,
                new InetSocketAddress("127.0.0.1", channel.socket().getLocalPort())));

        bgThread = new Thread(receiver, "receiver-test-thread");
        bgThread.start();

        long deadline = System.currentTimeMillis() + 3000;
        while (jitterBean.rcvdJitter.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        receiver.keepRunning = false;
        bgThread.join(2000);

        assertThat(jitterBean.rcvdJitter).hasSize(1);
        assertThat(jitterBean.rcvdPayload).hasSize(1);
        assertThat(jitterBean.rcvdPayload.get(0)).isEqualTo(payload);
        assertThat(analyzerTask.bytesRead).isEqualTo(payload.length);
        // rcvdJitter entries must be parseable non-negative second-values (String.valueOf(double))
        double elapsed = Double.parseDouble(jitterBean.rcvdJitter.get(0));
        assertThat(elapsed).isAtLeast(0.0);
    }

    @Test
    public void run_keepRunningFalseFromTheStart_returnsImmediatelyWithoutProcessingAnything() {
        UDPReplayInfoBean bean = new UDPReplayInfoBean();
        JitterBean jitterBean = new JitterBean();
        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(10, false, 5, false);

        CombinedReceiverThread receiver = new CombinedReceiverThread(bean, jitterBean, analyzerTask);
        receiver.keepRunning = false;

        receiver.run(); // while(keepRunning) is false from the first check -> returns right away

        assertThat(jitterBean.rcvdJitter).isEmpty();
        assertThat(analyzerTask.bytesRead).isEqualTo(0);
    }
}
