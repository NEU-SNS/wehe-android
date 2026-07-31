package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import mobi.meddle.wehe.data.model.ServerInstance;

/**
 * Tests for CUDPClient (mobi.meddle.wehe.combined.CUDPClient, main sources).
 * <p>
 * CUDPClient wraps a real java.nio DatagramChannel/Selector. Rather than mock nio internals
 * (notoriously awkward with Mockito), these tests exercise the class against real loopback UDP
 * sockets, per the suggested "real-but-local" strategy for this package.
 */
public class CUDPClientTest {

    private DatagramSocket receiver;
    private CUDPClient client;

    @After
    public void tearDown() {
        if (receiver != null && !receiver.isClosed()) {
            receiver.close();
        }
        if (client != null && client.channel != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    public void createSocket_opensNonBlockingChannel() {
        client = new CUDPClient("127.0.0.1"); // probe packet to 127.0.0.1:100 is harmless, no listener needed

        client.createSocket();

        assertThat(client.channel).isNotNull();
        assertThat(client.channel.isOpen()).isTrue();
        assertThat(client.channel.isBlocking()).isFalse();
    }

    @Test
    public void sendUDPPacket_deliversPayloadToDestination() throws IOException {
        receiver = new DatagramSocket(0); // bind an actual UDP listener on loopback
        client = new CUDPClient("127.0.0.1");
        client.createSocket();

        byte[] payload = "hello-udp".getBytes();
        ServerInstance instance = new ServerInstance("127.0.0.1", String.valueOf(receiver.getLocalPort()));

        client.sendUDPPacket(payload, instance);

        byte[] buf = new byte[64];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        receiver.setSoTimeout(2000);
        receiver.receive(packet);

        byte[] received = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), received, 0, packet.getLength());
        assertThat(received).isEqualTo(payload);
    }

    @Test
    public void sendUDPPacket_parsesPortFromServerInstanceString() throws IOException {
        // instance.port is a String (ServerInstance.kt); CUDPClient.sendUDPPacket() does
        // Integer.parseInt(instance.port) with no try/catch (CUDPClient.java line 76) - a
        // malformed port string would throw an uncaught NumberFormatException. This test
        // documents the happy path parses correctly; the next test documents the failure mode.
        receiver = new DatagramSocket(0);
        client = new CUDPClient("127.0.0.1");
        client.createSocket();

        ServerInstance instance = new ServerInstance("127.0.0.1", String.valueOf(receiver.getLocalPort()));
        client.sendUDPPacket("x".getBytes(), instance);

        DatagramPacket packet = new DatagramPacket(new byte[16], 16);
        receiver.setSoTimeout(2000);
        receiver.receive(packet); // no exception -> port string was parsed correctly
    }

    @Test
    public void sendUDPPacket_malformedPortString_throwsUncheckedNumberFormatException() {
        // BUG-ish latent risk: CUDPClient.sendUDPPacket() (CUDPClient.java line 76) calls
        // Integer.parseInt(instance.port) with no surrounding try/catch. If the server ever sends
        // a malformed/non-numeric port in the port-mapping JSON (parsed in
        // CombinedSideChannel.receivePortMappingNonBlock() via String.valueOf(pair.get(1)) with no
        // validation), this throws an uncaught NumberFormatException instead of failing gracefully
        // like the rest of this package's I/O methods (which catch and log).
        client = new CUDPClient("127.0.0.1");
        client.createSocket();

        ServerInstance instance = new ServerInstance("127.0.0.1", "not-a-port");
        assertThrows(NumberFormatException.class, () -> client.sendUDPPacket("x".getBytes(), instance));
    }

    @Test
    public void close_closesChannel() {
        client = new CUDPClient("127.0.0.1");
        client.createSocket();

        client.close();

        assertThat(client.channel.isOpen()).isFalse();
    }

    @Test
    public void close_whenChannelNeverCreated_throwsNPE() {
        // Same defensive gap as CTCPClient.close() (see CTCPClientTest): CUDPClient.channel
        // defaults to null (CUDPClient.java line 21), and close() (line 85-91) does
        // `channel.close()` guarded only by `catch (IOException e)`. If createSocket() was never
        // called, close() throws an uncaught NullPointerException.
        client = new CUDPClient("127.0.0.1");
        assertThrows(NullPointerException.class, () -> client.close());
        client = null; // avoid tearDown() trying to close it again
    }

    @Test
    public void constructor_alwaysProducesUsableClient_evenWithEmptyPublicIP() {
        // Constructor only opens a Selector - it doesn't touch publicIP at all until
        // createSocket() runs, so an empty string shouldn't fail construction itself.
        client = new CUDPClient("");
        assertThat(client).isNotNull();
    }
}
