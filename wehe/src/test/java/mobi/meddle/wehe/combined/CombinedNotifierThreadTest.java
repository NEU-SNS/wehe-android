package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import mobi.meddle.wehe.data.model.UDPReplayInfoBean;

/**
 * Tests for CombinedNotifierThread (mobi.meddle.wehe.combined.CombinedNotifierThread, main
 * sources).
 * <p>
 * The wire protocol is: a 10-ASCII-digit length prefix, then that many payload bytes
 * (semicolon-delimited fields), mirroring CombinedSideChannel's sendObject()/receiveObject().
 * Real loopback TCP sockets are used so the DataInputStream plumbing is exercised for real.
 * <p>
 * NOTE on scope: receiveKbytes() (CombinedNotifierThread.java lines 119-139) has a serious latent
 * bug when the peer closes the stream before delivering the promised number of bytes - see the
 * class-level report for details. That scenario is deliberately NOT exercised here because
 * triggering it causes an unbounded busy-loop/hang (not a fast, bounded unit test); see the
 * final report for the analysis instead of a test that would hang the suite.
 */
public class CombinedNotifierThreadTest {

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Socket serverSideSocket;

    @After
    public void tearDown() throws IOException {
        if (clientSocket != null && !clientSocket.isClosed()) {
            clientSocket.close();
        }
        if (serverSideSocket != null && !serverSideSocket.isClosed()) {
            serverSideSocket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    private void connectLoopback() throws IOException {
        serverSocket = new ServerSocket(0);
        clientSocket = new Socket("127.0.0.1", serverSocket.getLocalPort());
        serverSideSocket = serverSocket.accept();
    }

    /** Writes the 10-digit-length-prefixed wire format used by receiveObject()/sendObject(). */
    private static void writeObject(OutputStream out, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        out.write(String.format(Locale.US, "%010d", payloadBytes.length).getBytes(StandardCharsets.UTF_8));
        out.write(payloadBytes);
        out.flush();
    }

    @Test
    public void run_processesStartedThenDone_andExitsWhenDoneSendingAndInProcessZero() throws IOException {
        connectLoopback();
        // Write both messages up-front so run() never needs to hit the Thread.sleep(500) branch.
        writeObject(serverSideSocket.getOutputStream(), "STARTED;replay1");
        writeObject(serverSideSocket.getOutputStream(), "DONE;replay1");

        CombinedNotifierThread notifier = new CombinedNotifierThread(new UDPReplayInfoBean(), clientSocket);
        notifier.doneSending = true; // loop only breaks once doneSending is true AND inProcess == 0

        notifier.run(); // must return promptly (no hang) once DONE is processed
    }

    @Test
    public void run_unrecognizedNotificationType_logsWtfAndBreaksImmediately() throws IOException {
        connectLoopback();
        writeObject(serverSideSocket.getOutputStream(), "SOMETHING_ELSE;x");

        CombinedNotifierThread notifier = new CombinedNotifierThread(new UDPReplayInfoBean(), clientSocket);

        notifier.run(); // the `else { Log.wtf(...); break; }` branch (line 71-73) exits the loop right away
    }

    @Test
    public void run_malformedObjectSizeHeader_throwsInternally_andRunReturnsViaOuterCatch() throws IOException {
        // receiveObject()'s Integer.parseInt(new String(recvObjSizeBytes)) (line 107) has no
        // try/catch of its own; a non-numeric 10-byte size header throws an uncaught
        // NumberFormatException that is only caught by run()'s outermost `catch (Exception e)`
        // (lines 89-91), silently ending the thread instead of the UDP replay flow being
        // notified in any structured way.
        connectLoopback();
        serverSideSocket.getOutputStream().write("not_a_num!".getBytes(StandardCharsets.UTF_8)); // 10 bytes, non-numeric
        serverSideSocket.getOutputStream().flush();

        CombinedNotifierThread notifier = new CombinedNotifierThread(new UDPReplayInfoBean(), clientSocket);

        notifier.run(); // must not throw out of run() itself - caught internally, thread just ends
    }

    @Test
    public void run_socketNeverConnected_dataInputStreamStaysNull_runReturnsWithoutHanging() {
        // When the Socket passed to the constructor isn't connected, dataInputStream is never
        // assigned (stays null - CombinedNotifierThread.java lines 38-46). run() then NPEs on
        // `dataInputStream.available()` on the very first loop iteration; that NPE is caught by
        // the same generic `catch (Exception e)` (lines 89-91), so run() still returns promptly
        // instead of looping forever - this is the one case where the broad catch-all actually
        // saves it from hanging.
        Socket neverConnected = new Socket(); // isConnected() == false

        CombinedNotifierThread notifier = new CombinedNotifierThread(new UDPReplayInfoBean(), neverConnected);

        notifier.run();
    }
}
