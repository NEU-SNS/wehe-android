package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import mobi.meddle.wehe.data.model.RequestSet;
import mobi.meddle.wehe.util.Config;

/**
 * Tests for CTCPClientThread (mobi.meddle.wehe.combined.CTCPClientThread, main sources).
 * <p>
 * CTCPClientThread.run() is invoked directly (not via a real Thread) so tests stay deterministic;
 * run() itself only does `Thread.currentThread().setName(...)`, which is harmless to call on the
 * test thread. Real loopback TCP sockets (ServerSocket bound to port 0) are used instead of
 * mocking java.net.Socket, per the suggested strategy for this package.
 */
public class CTCPClientThreadTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;

    @Before
    public void setUp() {
        // give every test a value for whatever replay name it uses, since Config.get() throws
        // NullPointerException on an unset key (Config.java line 63) and CTCPClientThread reads
        // Config.get(client.replayName) whenever addHeader && addInfo.
    }

    @After
    public void tearDown() throws IOException {
        executor.shutdownNow();
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    private RequestSet requestSet(String cSPair, byte[] payload, int responseLen) {
        return new RequestSet(cSPair, 0.0, payload, responseLen, null, false);
    }

    // ---------------------------------------------------------------------
    // Header-cooking logic (addHeader && addInfo path) - no response expected (responseLen = 0)
    // ---------------------------------------------------------------------

    @Test
    public void randomReplay_cooksCustomInfoHeader_replacingLeadingBytesOfPayload() throws Exception {
        Config.set("replayHeaderTest-random", "cfgVal1");
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-random", "127.0.0.1",
                serverSocket.getLocalPort(), "replayHeaderTest-random", "9.9.9.9", true);

        String expectedCustomInfo = String.format("X-rr;%s;%s;%s;X-rr", "9.9.9.9", "cfgVal1", "csp-random");
        byte[] customInfoBytes = expectedCustomInfo.getBytes();

        // original payload must be longer than the cooked header so the "replace leading bytes" branch runs
        byte[] originalPayload = new byte[customInfoBytes.length + 20];
        for (int i = 0; i < originalPayload.length; i++) {
            originalPayload[i] = (byte) ('A' + (i % 26));
        }

        RequestSet rs = requestSet("csp-random", originalPayload, 0);
        Semaphore sendSema = new Semaphore(0);
        Semaphore recvSema = new Semaphore(0);
        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(10, true, 5, false);

        CTCPClientThread thread = new CTCPClientThread(client, rs, queue, sendSema, recvSema, 100, analyzerTask);
        thread.run();

        Socket serverSide = acceptFuture.get(5, TimeUnit.SECONDS);
        byte[] received = readAvailable(serverSide, customInfoBytes.length + 20);

        byte[] expectedTail = new byte[originalPayload.length - customInfoBytes.length];
        System.arraycopy(originalPayload, customInfoBytes.length, expectedTail, 0, expectedTail.length);

        assertThat(received.length).isEqualTo(originalPayload.length);
        byte[] receivedHead = new byte[customInfoBytes.length];
        System.arraycopy(received, 0, receivedHead, 0, customInfoBytes.length);
        byte[] receivedTail = new byte[expectedTail.length];
        System.arraycopy(received, customInfoBytes.length, receivedTail, 0, expectedTail.length);

        assertThat(new String(receivedHead, StandardCharsets.UTF_8)).isEqualTo(expectedCustomInfo);
        assertThat(receivedTail).isEqualTo(expectedTail);
    }

    @Test
    public void randomReplay_payloadShorterThanCookedHeader_wholePayloadReplaced() throws Exception {
        Config.set("shortReplay-random", "v");
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-short", "127.0.0.1",
                serverSocket.getLocalPort(), "shortReplay-random", "1.2.3.4", true);

        String expectedCustomInfo = String.format("X-rr;%s;%s;%s;X-rr", "1.2.3.4", "v", "csp-short");
        byte[] customInfoBytes = expectedCustomInfo.getBytes();

        // original payload shorter than the cooked header -> "replace payload" branch (CTCPClientThread.java ~line 108)
        byte[] originalPayload = new byte[]{1, 2, 3};
        RequestSet rs = requestSet("csp-short", originalPayload, 0);

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        Socket serverSide = acceptFuture.get(5, TimeUnit.SECONDS);
        byte[] received = readAvailable(serverSide, customInfoBytes.length);

        // note: written length equals the cooked header's length, NOT the original 3-byte payload's length
        assertThat(received).isEqualTo(customInfoBytes);
    }

    @Test
    public void getRequest_insertsXrrHeaderRightAfterFirstLine() throws Exception {
        Config.set("getReplayTest", "cfgGetVal");
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-get", "127.0.0.1",
                serverSocket.getLocalPort(), "getReplayTest", "8.8.8.8", true);

        String original = "GET /path HTTP/1.1\r\nHost: example.com\r\n\r\n";
        RequestSet rs = requestSet("csp-get", original.getBytes(StandardCharsets.UTF_8), 0);

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        Socket serverSide = acceptFuture.get(5, TimeUnit.SECONDS);
        String[] parts = original.split("\r\n", 2);
        String expectedCustomInfo = String.format("\r\nX-rr: %s;%s;%s\r\n", "8.8.8.8", "cfgGetVal", "csp-get");
        String expected = parts[0] + expectedCustomInfo + parts[1];

        byte[] received = readAvailable(serverSide, expected.getBytes(StandardCharsets.UTF_8).length);
        assertThat(new String(received, StandardCharsets.UTF_8)).isEqualTo(expected);
    }

    @Test
    public void neitherRandomNorGet_writesPayloadUntouched() throws Exception {
        Config.set("plainReplayTest", "unused"); // not read on this branch, set anyway for safety
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-plain", "127.0.0.1",
                serverSocket.getLocalPort(), "plainReplayTest", "8.8.8.8", true);

        byte[] original = "not a GET request at all".getBytes(StandardCharsets.UTF_8);
        RequestSet rs = requestSet("csp-plain", original, 0);

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        Socket serverSide = acceptFuture.get(5, TimeUnit.SECONDS);
        byte[] received = readAvailable(serverSide, original.length);
        assertThat(received).isEqualTo(original);
    }

    @Test
    public void addHeaderFalse_writesPayloadDirectly_noConfigLookup() throws Exception {
        // addHeader = false means the `if (client.addHeader && addInfo)` branch never runs, so
        // Config.get(replayName) is never consulted - use a replay name with NO Config entry to
        // prove that.
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-noheader", "127.0.0.1",
                serverSocket.getLocalPort(), "replayNameNeverConfigured", "8.8.8.8", false);

        byte[] original = {10, 20, 30, 40};
        RequestSet rs = requestSet("csp-noheader", original, 0);

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run(); // must not throw NPE from Config.get() on an unconfigured key

        Socket serverSide = acceptFuture.get(5, TimeUnit.SECONDS);
        byte[] received = readAvailable(serverSide, original.length);
        assertThat(received).isEqualTo(original);
    }

    // ---------------------------------------------------------------------
    // sendSema / recvSema / queue.threads bookkeeping (finally block always runs)
    // ---------------------------------------------------------------------

    @Test
    public void run_alwaysReleasesSemaphoresAndDecrementsThreadCount_onSuccess() throws Exception {
        serverSocket = new ServerSocket(0);
        Future<Socket> acceptFuture = executor.submit(() -> serverSocket.accept());

        CTCPClient client = new CTCPClient("csp-book", "127.0.0.1",
                serverSocket.getLocalPort(), "bookReplay", "1.1.1.1", false);
        RequestSet rs = requestSet("csp-book", new byte[]{1}, 0);

        Semaphore sendSema = new Semaphore(0);
        Semaphore recvSema = new Semaphore(0);
        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        queue.setThreads(1);

        CTCPClientThread thread = new CTCPClientThread(client, rs, queue, sendSema, recvSema, 100,
                new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();
        acceptFuture.get(5, TimeUnit.SECONDS);

        assertThat(sendSema.tryAcquire()).isTrue(); // released exactly once by run()
        assertThat(recvSema.tryAcquire()).isTrue(); // released in the finally block
        assertThat(queue.getThreads()).isEqualTo(0); // decremented in the finally block
        assertThat(queue.getABORT()).isFalse();
    }

    // ---------------------------------------------------------------------
    // Response-reading logic (responseLen > 0). addHeader = false and a pre-connected socket keep
    // header-cooking out of the picture so only the receive loop is under test.
    // ---------------------------------------------------------------------

    @Test
    public void response_fullExpectedLengthReceived_noAbort() throws Exception {
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();

        CTCPClient client = new CTCPClient("csp-resp-ok", "127.0.0.1", 0, "respReplay", "1.1.1.1", false);
        client.socket = clientSideSocket; // pre-assigned -> addInfo stays false, header logic skipped

        int responseLen = 20;
        RequestSet rs = requestSet("csp-resp-ok", new byte[]{1, 2, 3}, responseLen);

        byte[] responseBytes = new byte[responseLen];
        for (int i = 0; i < responseLen; i++) {
            responseBytes[i] = (byte) ('a' + i);
        }
        serverSideSocket.getOutputStream().write(responseBytes);
        serverSideSocket.getOutputStream().flush();

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(10, true, 5, false);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, analyzerTask);
        thread.run();

        assertThat(queue.getABORT()).isFalse();
        assertThat(analyzerTask.bytesRead).isEqualTo(responseLen);
    }

    @Test
    public void response_startsWithSuspiciousClientIPLiteral_neverDetected_dueToTruncatedSubstringCompare()
            throws Exception {
        // REAL BUG: CTCPClientThread.java lines 181-182 (and the mirrored check at lines 167-168)
        // compare `data.substring(0, 12)` against the FULL literal "SuspiciousClientIP!", which is
        // 19 characters long. A 12-character substring can never equalsIgnoreCase() a 19-character
        // string, so this anti-spoofing/traffic-manipulation detection can never fire, no matter
        // what the server actually sends. This test proves the check is dead code: even though the
        // response starts with the exact literal the check is supposedly looking for, run()
        // completes with no exception and ABORT stays false.
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();

        CTCPClient client = new CTCPClient("csp-suspicious", "127.0.0.1", 0, "respReplay2", "1.1.1.1", false);
        client.socket = clientSideSocket;

        String suspicious = "SuspiciousClientIP!"; // 19 chars, matches the literal exactly
        String padded = suspicious + "................."; // pad well past 19 bytes
        int responseLen = padded.length();
        RequestSet rs = requestSet("csp-suspicious", new byte[]{1}, responseLen);

        serverSideSocket.getOutputStream().write(padded.getBytes(StandardCharsets.UTF_8));
        serverSideSocket.getOutputStream().flush();

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        // If the detection worked as the code's intent/log messages describe, ABORT would be true
        // here with abort_reason == "error_proxy" (SocketException path). Instead:
        assertThat(queue.getABORT()).isFalse();
        assertThat(queue.getAbort_reason()).isNull();
    }

    @Test
    public void response_prematureStreamEnd_beyondTolerance_throwsSocketException_setsAbortReason()
            throws Exception {
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();

        CTCPClient client = new CTCPClient("csp-short-resp", "127.0.0.1", 0, "respReplay3", "1.1.1.1", false);
        client.socket = clientSideSocket;

        int responseLen = 50;
        RequestSet rs = requestSet("csp-short-resp", new byte[]{1}, responseLen);

        // server sends fewer bytes than expected, then closes -> client read() returns -1 early
        serverSideSocket.getOutputStream().write(new byte[10]);
        serverSideSocket.getOutputStream().flush();
        serverSideSocket.close();

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        Semaphore recvSema = new Semaphore(0);
        // tolerance = 0 guarantees the shortfall (40 bytes missing) is never "within tolerance"
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), recvSema, 0, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        assertThat(queue.getABORT()).isTrue();
        assertThat(queue.getAbort_reason()).isEqualTo("error_proxy");
        // finally block still runs even though an exception was thrown mid-read
        assertThat(recvSema.tryAcquire()).isTrue();
    }

    @Test
    public void response_prematureStreamEnd_withinTolerance_ignoredAndProceedsNormally() throws Exception {
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        Socket serverSideSocket = serverSocket.accept();

        CTCPClient client = new CTCPClient("csp-tolerated", "127.0.0.1", 0, "respReplay4", "1.1.1.1", false);
        client.socket = clientSideSocket;

        int responseLen = 50;
        RequestSet rs = requestSet("csp-tolerated", new byte[]{1}, responseLen);

        // missing only 5 bytes out of 50; tolerance of 10 covers that shortfall
        serverSideSocket.getOutputStream().write(new byte[45]);
        serverSideSocket.getOutputStream().flush();
        serverSideSocket.close();

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 10, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        assertThat(queue.getABORT()).isFalse();
    }

    @Test
    public void response_socketTimeout_setsAbortReasonAndABORT() throws Exception {
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        serverSocket.accept(); // server accepts but never writes anything back

        clientSideSocket.setSoTimeout(100); // short timeout to keep the test fast

        CTCPClient client = new CTCPClient("csp-timeout", "127.0.0.1", 0, "respReplay5", "1.1.1.1", false);
        client.socket = clientSideSocket;

        RequestSet rs = requestSet("csp-timeout", new byte[]{1}, 100);

        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));
        thread.run();

        assertThat(queue.getABORT()).isTrue();
        assertThat(queue.getAbort_reason()).isEqualTo("Replay Aborted: replay socket error");
    }

    // ---------------------------------------------------------------------
    // timeout()
    // ---------------------------------------------------------------------

    @Test
    public void timeout_closesClientSocket() throws Exception {
        serverSocket = new ServerSocket(0);
        Socket clientSideSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        serverSocket.accept();

        CTCPClient client = new CTCPClient("csp-timeout-close", "127.0.0.1", 0, "r", "1.1.1.1", false);
        client.socket = clientSideSocket;

        RequestSet rs = requestSet("csp-timeout-close", new byte[]{1}, 0);
        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));

        thread.timeout();

        assertThat(clientSideSocket.isClosed()).isTrue();
    }

    @Test
    public void timeout_nullSocket_doesNotThrow() {
        CTCPClient client = new CTCPClient("csp-null", "127.0.0.1", 0, "r", "1.1.1.1", false);
        // client.socket is null by default
        RequestSet rs = requestSet("csp-null", new byte[]{1}, 0);
        CombinedQueue queue = new CombinedQueue(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 30);
        CTCPClientThread thread = new CTCPClientThread(client, rs, queue,
                new Semaphore(0), new Semaphore(0), 100, new CombinedAnalyzerTask(10, true, 5, false));

        thread.timeout(); // guarded by `client != null && client.socket != null` - must not throw
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    /** Reads up to {@code expectedLen} bytes (or until a short read-timeout elapses) from socket. */
    private static byte[] readAvailable(Socket socket, int expectedLen) throws IOException {
        socket.setSoTimeout(2000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (out.size() < expectedLen) {
            int n = socket.getInputStream().read(buf);
            if (n < 0) {
                break;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
