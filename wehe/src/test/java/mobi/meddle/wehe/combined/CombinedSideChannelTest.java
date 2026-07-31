package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

/**
 * Tests for CombinedSideChannel (mobi.meddle.wehe.combined.CombinedSideChannel, main sources) -
 * the JSON/string control-message exchange with the measurement server. This is the largest and
 * most central class in this package, so it gets the deepest bug-hunting attention.
 * <p>
 * Strategy: SSLSocketFactory.createSocket(ip, port) is mocked (Mockito can mock the abstract
 * class directly) to hand back a real loopback java.net.Socket, so the rest of the class (which
 * only calls plain Socket/DataOutputStream/DataInputStream methods) runs unmodified against a
 * real local TCP connection - no network, but real socket/stream semantics.
 * <p>
 * Uses Robolectric (matching the convention already used by ResultsViewModelTest.kt in this
 * project for org.json-heavy code) because org.json.JSONObject/JSONArray are part of the Android
 * SDK stub jar and are not usable in a plain local unit test without Robolectric's shadow
 * implementation backing them with the real org.json engine.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class CombinedSideChannelTest {

    private ServerSocket serverSocket;
    private Socket serverSideSocket;

    @After
    public void tearDown() throws IOException {
        if (serverSideSocket != null && !serverSideSocket.isClosed()) {
            serverSideSocket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    private CombinedSideChannel newChannel(int id, boolean isTcp) throws IOException {
        serverSocket = new ServerSocket(0);
        int port = serverSocket.getLocalPort();
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        when(factory.createSocket(anyString(), anyInt())).thenAnswer(inv -> new Socket("127.0.0.1", port));

        CombinedSideChannel channel = new CombinedSideChannel(id, factory, "127.0.0.1", port, isTcp);
        serverSideSocket = serverSocket.accept();
        serverSideSocket.setSoTimeout(3000);
        return channel;
    }

    /** Reads one length-prefixed object off the wire, matching sendObject()'s format. */
    private String readObject() throws IOException {
        DataInputStream in = new DataInputStream(serverSideSocket.getInputStream());
        byte[] lenBytes = new byte[10];
        in.readFully(lenBytes);
        int len = Integer.parseInt(new String(lenBytes, StandardCharsets.UTF_8));
        byte[] payload = new byte[len];
        in.readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }

    /** Writes one length-prefixed object onto the wire, matching receiveObject()'s expected format. */
    private void writeObject(String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        OutputStream out = serverSideSocket.getOutputStream();
        out.write(String.format(Locale.US, "%010d", payloadBytes.length).getBytes(StandardCharsets.UTF_8));
        out.write(payloadBytes);
        out.flush();
    }

    // ---------------------------------------------------------------------
    // constructor / getId() / closeSideChannelSocket()
    // ---------------------------------------------------------------------

    @Test
    public void getId_returnsIdPassedToConstructor() throws IOException {
        CombinedSideChannel channel = newChannel(42, true);
        assertThat(channel.getId()).isEqualTo(42);
    }

    @Test
    public void closeSideChannelSocket_doesNotThrow_evenIfCalledTwice() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        channel.closeSideChannelSocket();
        channel.closeSideChannelSocket(); // Socket.close() is idempotent; catches IOException anyway
    }

    // ---------------------------------------------------------------------
    // declareID / sendChangeSpec / sendIperf / sendDone / sendTimeSlices (send-only, pure formatting)
    // ---------------------------------------------------------------------

    @Test
    public void declareID_sendsSemicolonJoinedFieldsInDeclaredOrder() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        channel.declareID("replayName", "endOfTest", "randomId", "historyCount",
                "testID", "extraString", "realIP", "1.2.3");

        String received = readObject();
        // order per declareID()'s `args` array (CombinedSideChannel.java lines 108-109):
        // randomId, testID, replayName, extraString, historyCount, endOfTest, realIP, appVersion
        assertThat(received).isEqualTo("randomId;testID;replayName;extraString;historyCount;endOfTest;realIP;1.2.3");
    }

    @Test
    public void sendChangeSpec_formatsBracketedCsvMessage() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        channel.sendChangeSpec(5, "drop", "packet");

        assertThat(readObject()).isEqualTo("[5, drop, packet]");
    }

    @Test
    public void sendIperf_alwaysSendsNoIperfLiteral() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        channel.sendIperf();

        assertThat(readObject()).isEqualTo("NoIperf");
    }

    @Test
    public void sendDone_sendsDonePrefixedDuration() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        channel.sendDone(12.5);

        assertThat(readObject()).isEqualTo("DONE;12.5");
    }

    @Test
    public void sendTimeSlices_sendsJsonArrayOfLists() throws IOException, org.json.JSONException {
        CombinedSideChannel channel = newChannel(1, true);

        ArrayList<ArrayList<Double>> data = new ArrayList<>();
        ArrayList<Double> throughputs = new ArrayList<>();
        throughputs.add(1.5);
        throughputs.add(2.5);
        ArrayList<Double> slices = new ArrayList<>();
        slices.add(3.0);
        slices.add(6.0);
        data.add(throughputs);
        data.add(slices);

        channel.sendTimeSlices(data);

        String received = readObject();
        JSONArray parsed = new JSONArray(received);
        assertThat(parsed.length()).isEqualTo(2);
        JSONArray parsedThroughputs = parsed.getJSONArray(0);
        assertThat(parsedThroughputs.getDouble(0)).isEqualTo(1.5);
        assertThat(parsedThroughputs.getDouble(1)).isEqualTo(2.5);
        JSONArray parsedSlices = parsed.getJSONArray(1);
        assertThat(parsedSlices.getDouble(0)).isEqualTo(3.0);
        assertThat(parsedSlices.getDouble(1)).isEqualTo(6.0);
    }

    @Test
    public void sendMobileStats_falseFlag_sendsNoMobileStatsLiteral_andNeverTouchesContext() throws IOException {
        // The `else` branch (CombinedSideChannel.java line 195) never dereferences `context`, so
        // passing null here is safe and proves that.
        CombinedSideChannel channel = newChannel(1, true);

        channel.sendMobileStats("false", null);

        assertThat(readObject()).isEqualTo("NoMobileStats");
    }

    @Test
    public void sendMobileStats_trueFlag_sendsWillSendMarkerThenDeviceInfoJson() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        Context context = ApplicationProvider.getApplicationContext();

        channel.sendMobileStats("true", context);

        assertThat(readObject()).isEqualTo("WillSendMobileStats");
        String deviceInfoJson = readObject();
        JSONObject parsed;
        try {
            parsed = new JSONObject(deviceInfoJson);
        } catch (Exception e) {
            throw new AssertionError("second object sent was not valid JSON: " + deviceInfoJson, e);
        }
        assertThat(parsed.has("manufacturer")).isTrue();
        assertThat(parsed.has("os")).isTrue();
        assertThat(parsed.has("locationInfo")).isTrue();
    }

    // ---------------------------------------------------------------------
    // ask4Permission()
    // ---------------------------------------------------------------------

    @Test
    public void ask4Permission_granted_splitsIntoExpectedFields() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("1;1.2.3.4;5");

        String[] result = channel.ask4Permission();

        assertThat(result).asList().containsExactly("1", "1.2.3.4", "5").inOrder();
    }

    @Test
    public void ask4Permission_denied_splitsIntoExpectedFields() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("0;errorcode42");

        String[] result = channel.ask4Permission();

        assertThat(result).asList().containsExactly("0", "errorcode42").inOrder();
    }

    // ---------------------------------------------------------------------
    // receiveSenderCount()
    // ---------------------------------------------------------------------

    @Test
    public void receiveSenderCount_validNumber_parsesCorrectly() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("7");

        assertThat(channel.receiveSenderCount()).isEqualTo(7);
    }

    @Test
    public void receiveSenderCount_malformed_tcp_fallsBackToZero() throws IOException {
        // CombinedSideChannel.java lines 268-282: on NumberFormatException, isTcp==true forces 0.
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("not-a-number");

        assertThat(channel.receiveSenderCount()).isEqualTo(0);
    }

    @Test
    public void receiveSenderCount_malformed_notTcp_fallsBackToDefaultOne() throws IOException {
        // Same malformed input, but isTcp==false: the catch block does NOT reset senderCount, so
        // it keeps whatever it was initialized to (1) before the failed parse attempt - this is a
        // slightly surprising "default" (any garbage for a UDP side channel silently becomes "1"
        // sender rather than being treated as an error).
        CombinedSideChannel channel = newChannel(1, false);
        writeObject("also-not-a-number");

        assertThat(channel.receiveSenderCount()).isEqualTo(1);
    }

    // ---------------------------------------------------------------------
    // receivePortMappingNonBlock() - the deepest JSON parsing logic in this package
    // ---------------------------------------------------------------------

    @Test
    public void receivePortMappingNonBlock_wellFormedJson_parsesNestedStructure() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        String json = "{\"tcp\":{\"008.249.245.246\":{\"00080\":[\"\",80]}},"
                + "\"udp\":{\"010.110.063.089\":{\"49882\":[\"1.2.3.4\",49882]}}}";
        writeObject(json);

        java.util.HashMap<String, java.util.HashMap<String, java.util.HashMap<String,
                mobi.meddle.wehe.data.model.ServerInstance>>> result = channel.receivePortMappingNonBlock();

        assertThat(result).containsKey("tcp");
        assertThat(result).containsKey("udp");
        mobi.meddle.wehe.data.model.ServerInstance tcpInstance =
                result.get("tcp").get("008.249.245.246").get("00080");
        assertThat(tcpInstance.server).isEqualTo("");
        assertThat(tcpInstance.port).isEqualTo("80");

        mobi.meddle.wehe.data.model.ServerInstance udpInstance =
                result.get("udp").get("010.110.063.089").get("49882");
        assertThat(udpInstance.server).isEqualTo("1.2.3.4");
        assertThat(udpInstance.port).isEqualTo("49882");
    }

    @Test
    public void receivePortMappingNonBlock_emptyJsonObject_returnsEmptyMap() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("{}");

        java.util.HashMap<String, java.util.HashMap<String, java.util.HashMap<String,
                mobi.meddle.wehe.data.model.ServerInstance>>> result = channel.receivePortMappingNonBlock();

        assertThat(result).isEmpty();
    }

    @Test
    public void receivePortMappingNonBlock_malformedJson_swallowsExceptionAndReturnsWhateverWasParsedSoFar()
            throws IOException {
        // LATENT BUG: receivePortMappingNonBlock() (CombinedSideChannel.java lines 225-259) catches
        // JSONException internally and only logs it (lines 255-257) - it never rethrows, and the
        // method's signature only declares `throws IOException`. So malformed JSON from the server
        // (a truncated/garbled port-mapping response) does NOT surface as an error to the caller at
        // all: the caller gets back a HashMap that silently contains only whatever top-level keys
        // were parsed before the JSON broke, with NO way to distinguish "the server legitimately
        // sent an empty/partial mapping" from "parsing blew up halfway through". Here the JSON is
        // invalid from the very first character, so nothing at all is parsed, and the method
        // returns a plain empty map - masquerading as a valid (if empty) response.
        CombinedSideChannel channel = newChannel(1, true);
        writeObject("{not valid json at all");

        java.util.HashMap<String, java.util.HashMap<String, java.util.HashMap<String,
                mobi.meddle.wehe.data.model.ServerInstance>>> result = channel.receivePortMappingNonBlock();

        assertThat(result).isEmpty(); // silently "succeeds" instead of signalling a parse failure
    }

    // ---------------------------------------------------------------------
    // getResult()
    // ---------------------------------------------------------------------

    @Test
    public void getResult_falseRequested_sendsResultNo_andLoopsUntilOK() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        // server side: read the request, then reply with a decoy message before "OK"
        // (run synchronously since we write both replies up-front before calling getResult()).
        Thread serverThread = new Thread(() -> {
            try {
                assertThat(readObject()).isEqualTo("Result;No");
                writeObject("please-wait");
                writeObject("OK");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();

        boolean result = channel.getResult("false");

        assertThat(result).isFalse();
    }

    @Test
    public void getResult_trueRequested_sendsResultYes_returnsTrueRegardlessOfServerReply() throws IOException {
        CombinedSideChannel channel = newChannel(1, true);

        Thread serverThread = new Thread(() -> {
            try {
                assertThat(readObject()).isEqualTo("Result;Yes");
                writeObject("anything-at-all");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();

        boolean result = channel.getResult("true");

        assertThat(result).isTrue();
    }

    @Test
    public void getResult_falseRequested_streamClosedBeforeOK_throwsIOException_doesNotHang() throws Exception {
        // Unlike CombinedNotifierThread's receiveKbytes(), CombinedSideChannel's receiveKbytes()
        // (lines 396-414) correctly rethrows on premature stream end, so getResult("false")'s
        // `while (!str.equals("OK"))` loop (lines 322-327) terminates via a thrown IOException
        // instead of hanging forever if the server never sends "OK".
        CombinedSideChannel channel = newChannel(1, true);

        Thread serverThread = new Thread(() -> {
            try {
                readObject(); // "Result;No"
                writeObject("still-not-ok");
                serverSideSocket.close(); // ends the stream before ever sending "OK"
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        serverThread.start();

        assertThrows(IOException.class, () -> channel.getResult("false"));
    }

    // ---------------------------------------------------------------------
    // notifierCreator()
    // ---------------------------------------------------------------------

    @Test
    public void notifierCreator_returnsUsableNotifierThread() throws IOException {
        CombinedSideChannel channel = newChannel(1, false);

        CombinedNotifierThread notifier = channel.notifierCreator(new mobi.meddle.wehe.data.model.UDPReplayInfoBean());

        assertThat(notifier).isNotNull();
    }
}
