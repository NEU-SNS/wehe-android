package mobi.meddle.wehe.combined;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

import mobi.meddle.wehe.bean.DeviceInfoBean;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;

/**
 * This side channel is used to communicate with the server in bytes mode and to
 * run traces; it send tcp and udp packets and receives the same from the server.
 */
public class CombinedSideChannel {
    private final Socket socket; //socket to server
    private final DataOutputStream dataOutputStream; //write to server
    private final DataInputStream dataInputStream; //read from server
    private final int objLen = 10; //length of response from server that tells the length of the actual response
    private final boolean isTcp;
    private final int id; //id of side channel of this instance - used for logs

    /**
     * Creates a socket and I/O streams to that socket
     *
     * @param id               id of channel
     * @param sslSocketFactory socket factory to make socket
     * @param ip               IP of the server
     * @param port             server port to connect to
     * @throws IOException     something went wrong connecting to socket or getting I/O stream
     */
    public CombinedSideChannel(int id, @NonNull SSLSocketFactory sslSocketFactory, String ip, int port,
                               boolean isTcp) throws IOException {
        this.id = id;
        socket = sslSocketFactory.createSocket(ip, port);
        socket.setTcpNoDelay(true);
        socket.setReuseAddress(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(60000);

        try {
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            throw new IOException("Channel " + id + ": Issue getting output stream");
        }
        try {
            dataInputStream = new DataInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new IOException("Channel " + id + ": Issue getting input stream");
        }

        this.isTcp = isTcp;
    }

    /**
     * Get ID number of SideChannel.
     *
     * @return ID number
     */
    public int getId() {
        return id;
    }

    /**
     * Get cellphone carrier name.
     *
     * @param context the context
     * @return the cell carrier name
     */
    public static String getCarrierName(Context context) {
        DeviceInfoBean deviceInfoBean = new DeviceInfoBean(context);
        return deviceInfoBean.carrierName;
    }

    /**
     * Send replay info to server.
     *
     * @param replayName   name of the replay
     * @param endOfTest    true if last replay in test; false otherwise
     * @param randomId     ID to identify the user's device
     * @param historyCount the test number
     * @param testID       the replay number in the test
     * @param extraString  string to send data that might need to be sent in the future
     * @param realIP       the user's IP address
     * @param appVersion   the version of the app
     */
    public void declareID(String replayName, String endOfTest, String randomId, String historyCount,
                          String testID, String extraString, String realIP, String appVersion) {
        Log.i("declareID", "Channel " + id + ": Declaring ID");
        String[] args = {randomId, testID, replayName, extraString,
                historyCount, endOfTest, realIP, appVersion};

        String info = TextUtils.join(";", args);

        sendObject(info.getBytes());
        Log.d("declareID", "Channel " + id + ": " + info);
    }

    /**
     * Notifies server if changes need to be made when processing a packet. Currently, doesn't seem
     * too useful.
     *
     * @param mpacNum the packet number to apply the change to
     * @param action  the action to change
     * @param spec    the thing that needs to be changed about the action
     */
    public void sendChangeSpec(Integer mpacNum, String action, String spec) {
        String message = "[" + mpacNum + ", " + action + ", " + spec + "]";
        sendObject(message.getBytes());
    }

    /**
     * Ask server for permission to run replay.
     *
     * @return string with permission encoding
     * if permission granted, server will send "1;[user_IP];[number_slices]"
     * else, server will send "0;[error_code]"
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    public String[] ask4Permission() throws IOException {
        byte[] data = receiveObject(objLen);
        String tempPermission = new String(data);
        return tempPermission.split(";");
    }

    /**
     * Send NoIperf. Currently, doesn't seem too useful.
     */
    public void sendIperf() {
        Log.i("sendIperf", "Channel " + id + ": always no iperf!");
        String noIperf = "NoIperf";
        sendObject(noIperf.getBytes());
    }

    /**
     * Send info about the device to the server.
     *
     * @param sendMobileStat string; either true or false to decide whether to send stats to server
     * @param context        the context
     */
    public void sendMobileStats(@NonNull String sendMobileStat, Context context) {
        if (sendMobileStat.equalsIgnoreCase("true")) {
            Log.i("sendMobileStats", "Channel " + id + ": will send mobile stats!");
            DeviceInfoBean deviceInfoBean = new DeviceInfoBean(context);
            JSONObject deviceInfo = new JSONObject();
            JSONObject osInfo = new JSONObject();
            JSONObject locationInfo = new JSONObject();

            try {
                deviceInfo.put("manufacturer", deviceInfoBean.manufacturer);
                deviceInfo.put("model", deviceInfoBean.model);

                osInfo.put("INCREMENTAL", Build.VERSION.INCREMENTAL);
                osInfo.put("RELEASE", Build.VERSION.RELEASE);
                osInfo.put("SDK_INT", Build.VERSION.SDK_INT);

                deviceInfo.put("os", osInfo);
                deviceInfo.put("carrierName", deviceInfoBean.carrierName);
                deviceInfo.put("networkType", deviceInfoBean.networkType);
                deviceInfo.put("cellInfo", deviceInfoBean.cellInfo);

                locationInfo.put("latitude",
                        String.format(Locale.US, "%.1f", deviceInfoBean.location.getLatitude()));
                locationInfo.put("longitude",
                        String.format(Locale.US, "%.1f", deviceInfoBean.location.getLongitude()));

                deviceInfo.put("locationInfo", locationInfo);
            } catch (JSONException e) {
                Log.e("sendMobileStats", "Channel " + id + ": JSON issue with mobile stats", e);
            }

            Log.d("sendMobileStats", "Channel " + id + ": " + deviceInfo.toString());

            sendObject("WillSendMobileStats".getBytes());
            sendObject(deviceInfo.toString().getBytes());
        } else {
            sendObject("NoMobileStats".getBytes());
        }
    }

    /**
     * Get a giant list of IP port mappings like this from the server:
     * {"tcp": {
     *   "008.249.245.246": {
     *     "00080": ["", 80]},
     *   "008.252.208.244": {
     *     "00443": ["", 443]}},
     *   ...
     * "udp": {
     *   "010.110.063.089": {
     *     "49882": ["", 49882]},
     *   "052.112.077.144": {
     *     "03480": ["", 3480]},
     *   "054.215.072.028": {
     *     "08801": ["", 8801]}
     *   ...
     * }}
     * ReplayActivity chooses the correct ServerInstance to use based on the server IP and port.
     * This is not really useful now, as the tests connect to only one IP and port during the test.
     * The tests used to connect to different IPs and ports to emulate real app traffic, as real
     * apps connect to multiple IPs and ports. Each packet in the replays has a CS_Pair, which tells
     * which IP and port to connect to. All CS_Pairs in a replay are now the same.
     *
     * @return mapping of IPs and ports like above
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    public HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> receivePortMappingNonBlock()
            throws IOException {
        HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> ports = new HashMap<>();
        byte[] data = receiveObject(objLen);
        String tempStr = new String(data);
        Log.d("receivePortMapping", "Channel " + id + ": length: " + tempStr.length());
        JSONObject jObject;
        try {
            jObject = new JSONObject(tempStr);
            Iterator<String> keys = jObject.keys();
            while (keys.hasNext()) {
                HashMap<String, HashMap<String, ServerInstance>> tempHolder = new HashMap<>();
                String key = keys.next();
                JSONObject firstLevel = (JSONObject) jObject.get(key);
                Iterator<String> firstLevelKeys = firstLevel.keys();
                while (firstLevelKeys.hasNext()) {
                    HashMap<String, ServerInstance> tempHolder1 = new HashMap<>();
                    String key1 = firstLevelKeys.next();
                    JSONObject secondLevel = (JSONObject) firstLevel.get(key1);
                    Iterator<String> secondLevelKeys = secondLevel.keys();
                    while (secondLevelKeys.hasNext()) {
                        String key2 = secondLevelKeys.next();
                        JSONArray pair = secondLevel.getJSONArray(key2);
                        tempHolder1.put(key2, new ServerInstance(String.valueOf(pair.get(0)),
                                String.valueOf(pair.get(1))));
                    }
                    tempHolder.put(key1, tempHolder1);
                }
                ports.put(key, tempHolder);
            }
        } catch (JSONException e) {
            Log.e("receivingPortMapping", "Channel " + id + ": Error reading JSON", e);
        }
        return ports;
    }

    /**
     * Receive sender count for UDP. Currently, doesn't seem too useful. Currently, it seems to
     * return 0 if test is TCP and 1 if test is UDP.
     *
     * @return sender count
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    public int receiveSenderCount() throws IOException {
        byte[] data = receiveObject(objLen);
        String tempStr = new String(data);
        Log.d("receiveSenderCount", "Channel " + id + ": senderCount: " + tempStr);
        int senderCount = 1;
        try { //very very rarely, sometimes server sends something that isn't an int, and we don't know why
            senderCount = Integer.parseInt(tempStr);
        } catch (NumberFormatException e) {
            Log.e("receiveSenderCount", "Channel " + id + ": senderCount: " + tempStr, e);
            if (isTcp) {
                senderCount = 0;
            }
        }
        return senderCount;
    }

    /**
     * Creates a CombinedNotifierThread. Might not be too useful now?
     *
     * @param udpReplayInfoBean bean with info about the UDP replay
     * @return a CombinedNotifierThread
     */
    public CombinedNotifierThread notifierCreator(UDPReplayInfoBean udpReplayInfoBean) {
        return new CombinedNotifierThread(udpReplayInfoBean, this.socket);
    }

    /**
     * Tell server that the client has finished sending packets for the replay.
     *
     * @param duration time in seconds it took to run replay
     */
    public void sendDone(double duration) {
        sendObject(("DONE;" + duration).getBytes());
    }

    /**
     * Send throughputs to the server.
     *
     * @param averageThroughputsAndSlices list of throughputs and slices
     */
    public void sendTimeSlices(ArrayList<ArrayList<Double>> averageThroughputsAndSlices) {
        sendObject(new JSONArray(averageThroughputsAndSlices).toString().getBytes());
    }

    /**
     * Tells server whether client wants a result.
     *
     * @param result "false" if client does not want result; otherwise, the client wants result
     * @return false if client does not want a result, true otherwise
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    public boolean getResult(@NonNull String result) throws IOException {
        if (result.trim().equalsIgnoreCase("false")) {
            sendObject("Result;No".getBytes());
            byte[] data;
            String str = "";
            while (!str.equals("OK")) {
                data = receiveObject(objLen);
                str = new String(data);
            }
            Log.d("getResult", "Channel " + id + ": received result is: " + str);
            return false;
        } else {
            sendObject("Result;Yes".getBytes());
            byte[] data = receiveObject(objLen);
            String str = new String(data);
            Log.d("getResult", "Channel " + id + ": received result is: " + str);
        }
        return true;
    }

    /**
     * Close communication channel with the server.
     */
    public void closeSideChannelSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            Log.w("sideChannelLog", "Channel " + id + ": Issue closing side channel", e);
        }
    }

    /**
     * Send stuff to the server.
     *
     * @param buf the bytes to send to the server
     */
    private void sendObject(@NonNull byte[] buf) {
        try {
            dataOutputStream.writeBytes(String.format(Locale.getDefault(), "%010d", buf.length));
            Log.d("SideChannelLog", "Channel " + id + ": Sending buffer " + String.format(Locale.getDefault(),
                    "%010d", buf.length) + "  " + new String(buf));
            dataOutputStream.write(buf);
        } catch (IOException e) {
            Log.e("SideChannelLog", "Channel " + id + ": Error sending object", e);
        }
    }

    /**
     * Receive stuff from the server.
     *
     * @param objLen Number of bytes to read to obtain the size of the stuff
     * @return response from server
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    @NonNull
    private byte[] receiveObject(int objLen) throws IOException {
        byte[] recvObjSizeBytes = receiveKbytes(objLen); //first receive how big stuff will be
        // Log.d("Obj", new String(recvObjSizeBytes));
        int recvObjSize = Integer.parseInt(new String(recvObjSizeBytes));
        // Log.d("Obj", String.valueOf(recvObjSize));
        Log.d("SideChannelLog", "Channel " + id + ": Receiving buffer " + new String(recvObjSizeBytes));
        return receiveKbytes(recvObjSize); //return the response
    }

    /**
     * Receive k byes from the server.
     * <p>
     * Rajesh's original code has bug, if message is more than 4096, this method
     * will return disordered byte
     * <p>
     * Fixed by adrian
     *
     * @param k number of bytes to receive
     * @return response from server, null if there is an issue getting response
     * @throws IOException Probably issue with socket connection or unexpected end of data stream
     */
    @NonNull
    private byte[] receiveKbytes(int k) throws IOException {
        int totalRead = 0;
        byte[] b = new byte[k];
        while (totalRead < k) {
            int bufSize = 4096;
            int bytesRead;
            try {
                bytesRead = dataInputStream.read(b, totalRead, Math.min(k - totalRead, bufSize));
                if (bytesRead < 0) {
                    throw new IOException("Channel " + id + ": Data stream ended prematurely");
                }
            } catch (IOException e) {
                Log.e("ReceiveBytes", "Channel " + id + ": Error receiving data", e);
                throw new IOException("Channel " + id + ": Something went wrong receiving bytes");
            }
            totalRead += bytesRead;
        }
        return b;
    }
}
