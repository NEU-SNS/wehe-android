package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Tests for CTCPClient (mobi.meddle.wehe.combined.CTCPClient, main sources).
 * <p>
 * CTCPClient is a small holder around a java.net.Socket plus equals()/hashCode() based on
 * CSPair. createSocket()/close() are exercised here against a real loopback TCP server
 * (ServerSocket bound to port 0) rather than mocks, per the suggested strategy of preferring
 * real-but-local sockets over trying to mock java.net.Socket's internals.
 */
public class CTCPClientTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    @After
    public void tearDown() throws IOException {
        executor.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    // ---- equals()/hashCode() contract, based purely on CSPair ----

    @Test
    public void equals_sameCSPair_true() {
        CTCPClient a = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);
        CTCPClient b = new CTCPClient("csp1", "9.9.9.9", 443, "otherReplay", "1.1.1.1", true);

        // Deliberately different in every other field to prove equals() truly only looks at CSPair.
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void equals_differentCSPair_false() {
        CTCPClient a = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);
        CTCPClient b = new CTCPClient("csp2", "1.2.3.4", 80, "replay", "5.6.7.8", false);

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    public void equals_reflexive_sameInstance() {
        CTCPClient a = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);
        assertThat(a).isEqualTo(a);
    }

    @Test
    public void equals_null_false() {
        CTCPClient a = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);
        assertThat(a.equals(null)).isFalse();
    }

    @Test
    public void equals_differentType_false() {
        CTCPClient a = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);
        assertThat(a.equals("csp1")).isFalse();
    }

    // ---- createSocket(): real loopback connection ----

    @Test
    public void createSocket_connectsAndSetsSocketOptions() throws IOException, InterruptedException {
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp1", "127.0.0.1",
                serverSocket.getLocalPort(), "replay", "5.6.7.8", false);
        client.createSocket();

        try {
            Socket accepted = acceptFuture.get(5, TimeUnit.SECONDS);
            assertThat(accepted).isNotNull();
        } catch (Exception e) {
            throw new AssertionError("server never accepted the connection", e);
        }

        assertThat(client.socket).isNotNull();
        assertThat(client.socket.isConnected()).isTrue();
        assertThat(client.socket.getTcpNoDelay()).isTrue();
        assertThat(client.socket.getKeepAlive()).isTrue();
        assertThat(client.socket.getReuseAddress()).isTrue();
        assertThat(client.socket.getSoTimeout()).isEqualTo(30000);

        client.close();
    }

    @Test
    public void createSocket_connectionRefused_doesNotThrow_socketStaysNonNullButNotConnected() {
        // createSocket() catches every Exception internally (CTCPClient.java catch (Exception e)),
        // so an unreachable/refused destination must not propagate - it should just be logged.
        // Port 1 on loopback is essentially guaranteed to refuse a connection immediately.
        CTCPClient client = new CTCPClient("csp1", "127.0.0.1", 1, "replay", "5.6.7.8", false);

        client.createSocket();

        // socket field is assigned (`new Socket()`) before the connect() attempt, so it is
        // non-null even though the connection failed.
        assertThat(client.socket).isNotNull();
        assertThat(client.socket.isConnected()).isFalse();
    }

    // ---- close() ----

    @Test
    public void close_closesUnderlyingSocket() throws IOException {
        serverSocket = new ServerSocket(0);
        executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp1", "127.0.0.1",
                serverSocket.getLocalPort(), "replay", "5.6.7.8", false);
        client.createSocket();

        client.close();

        assertThat(client.socket.isClosed()).isTrue();
    }

    @Test
    public void close_calledTwice_doesNotThrow() throws IOException {
        // Socket.close() is documented as safe to call multiple times, and CTCPClient.close()
        // only catches IOException around it - confirm double-close really is silent.
        serverSocket = new ServerSocket(0);
        executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp1", "127.0.0.1",
                serverSocket.getLocalPort(), "replay", "5.6.7.8", false);
        client.createSocket();

        client.close();
        client.close(); // must not throw
    }

    @Test
    public void close_whenSocketNeverCreated_throwsNPE() {
        // BUG-ish latent risk: CTCPClient.socket defaults to null (CTCPClient.java line 20) and
        // close() (line 63-70) does `this.socket.close()` guarded only by `catch (IOException e)`.
        // If createSocket() was never called (e.g. caller error, or createSocket() itself never
        // assigns because of some future refactor), close() throws an uncaught
        // NullPointerException instead of failing gracefully like the rest of the class does.
        CTCPClient client = new CTCPClient("csp1", "1.2.3.4", 80, "replay", "5.6.7.8", false);

        assertThrows(NullPointerException.class, client::close);
    }
}
