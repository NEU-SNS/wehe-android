package mobi.meddle.wehe.combined;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

import javax.net.ssl.SSLSocketFactory;

import mobi.meddle.wehe.bean.DeviceInfoBean;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;


public class CombinedSideChannel {
    private Socket socket = null;
    private String id = null;
    private DataOutputStream dataOutputStream = null;
    private DataInputStream dataInputStream = null;
    private int objLen = 10;

    public CombinedSideChannel(SSLSocketFactory sslSocketFactory, String ip, int port, String id) {
        this.id = id;
        try {
            socket = sslSocketFactory.createSocket(ip, port);
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(60000);

            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataInputStream = new DataInputStream(socket.getInputStream());
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getCarrierName(Context context) {
        DeviceInfoBean deviceInfoBean = new DeviceInfoBean(context);
        return deviceInfoBean.carrierName;
    }

    public void declareID(String replayName, String endOfTest, String testID,
                          String extraString, String historyCount, String realIP, String appVersion) {

        String[] args = {id, testID, replayName, extraString,
                historyCount, endOfTest, realIP, appVersion};

        String info = TextUtils.join(";", args);

        sendObject(info.getBytes());
        Log.d("declareID", info);

    }

    public void sendChangeSpec(Integer mpacNum, String action, String spec) {
        String message = "[" + mpacNum + ", " + action + ", " + spec + "]";
        sendObject(message.getBytes());
    }

    public void sendMobileStats(SSLSocketFactory sslSocketFactory, String ip, int port,
                                String sendMobileStat, Context context, String userID,
                                int historyCount, int testId) {
        if (sendMobileStat.equalsIgnoreCase("true")) {
            Log.d("sendMobileStats", "will send mobile stats!");
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
                        String.valueOf(deviceInfoBean.location.getLatitude()));
                locationInfo.put("longitude",
                        String.valueOf(deviceInfoBean.location.getLongitude()));

                deviceInfo.put("locationInfo", locationInfo);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            Log.d("sendMobileStats", deviceInfo.toString());

            sendObject("WillSendMobileStats".getBytes());
            sendObject(deviceInfo.toString().getBytes());
        } else {
            sendObject("NoMobileStats".getBytes());
        }
    }

    public void sendDone(double duration) {
        sendObject(("DONE;" + duration).getBytes());
    }

    private void sendObject(byte[] buf) {
        try {
            dataOutputStream.writeBytes(String.format(Locale.getDefault(), "%010d", buf.length));
            Log.d("SideChannelLog", "Sending buffer " + new String(buf));
            dataOutputStream.write(buf);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * private void getResult() {
     * sendObject("GiveMeResults".getBytes(), objLen); byte[] result =
     * receiveObject(objLen); }
     */

    public HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> receivePortMappingNonBlock() {
        HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> ports = new HashMap<>();
        byte[] data = receiveObject(objLen);

        /*
         * JSONObject jObject = new JSONObject(new String(data));
         * Iterator<String> keys = jObject.keys(); while (keys.hasNext()) {
         * HashMap<String, ServerInstance> tempHolder = new HashMap<String,
         * ServerInstance>(); String key = keys.next(); JSONObject firstLevel =
         * (JSONObject) jObject.get(key); Iterator<String> firstLevelKeys =
         * firstLevel.keys(); while(firstLevelKeys.hasNext()) { String key1 =
         * firstLevelKeys.next(); JSONArray pair =
         * firstLevel.getJSONArray(key1); tempHolder.put(key1, new
         * ServerInstance(String.valueOf(pair.get(0)),
         * String.valueOf(pair.get(1)))); } ports.put(key, tempHolder); }
         */
        String tempStr = new String(data);
        Log.d("receivePortMapping", "length: " + tempStr.length());
        JSONObject jObject = null;
        try {
            jObject = new JSONObject(tempStr);
            Iterator<String> keys = jObject.keys();
            while (keys.hasNext()) {
                HashMap<String, HashMap<String, ServerInstance>> tempHolder = new HashMap<String, HashMap<String, ServerInstance>>();
                String key = keys.next();
                JSONObject firstLevel = (JSONObject) jObject.get(key);
                Iterator<String> firstLevelKeys = firstLevel.keys();
                while (firstLevelKeys.hasNext()) {
                    HashMap<String, ServerInstance> tempHolder1 = new HashMap<String, ServerInstance>();
                    String key1 = firstLevelKeys.next();
                    JSONObject secondLevel = (JSONObject) firstLevel.get(key1);
                    Iterator<String> secondLevelKeys = secondLevel.keys();
                    while (secondLevelKeys.hasNext()) {
                        String key2 = secondLevelKeys.next();
                        JSONArray pair = secondLevel.getJSONArray(key2);
                        tempHolder1.put(key2,
                                new ServerInstance(String.valueOf(pair.get(0)),
                                        String.valueOf(pair.get(1))));
                    }
                    tempHolder.put(key1, tempHolder1);
                }
                ports.put(key, tempHolder);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ports;
    }

    public int receiveSenderCount() {
        byte[] data = receiveObject(objLen);
        String tempStr = new String(data);
        // ByteBuffer wrapped = ByteBuffer.wrap(data);
        Log.d("receiveSenderCount", "senderCount: " + Integer.valueOf(tempStr));
        return Integer.valueOf(tempStr);

    }

    private byte[] receiveObject(int objLen) {
        byte[] recvObjSizeBytes = receiveKbytes(objLen);
        // Log.d("Obj", new String(recvObjSizeBytes));
        int recvObjSize = Integer.parseInt(new String(recvObjSizeBytes));
        // Log.d("Obj", String.valueOf(recvObjSize));
        Log.d("SideChannelLog", "Receiving buffer " + new String(recvObjSizeBytes));
        return receiveKbytes(recvObjSize);
    }

    public String[] ask4Permission() {
        byte[] data = receiveObject(objLen);
        String tempPermission = new String(data);
        return tempPermission.split(";");
    }

    public void sendIperf() {
        Log.d("sendIperf", "always no iperf!");
        String noIperf = "NoIperf";
        sendObject(noIperf.getBytes());
    }

    public void sendTimeSlices(ArrayList<ArrayList<Double>> averageThroughputsAndSlices) {
        sendObject(new JSONArray(averageThroughputsAndSlices).toString().getBytes());
    }

    public CombinedNotifierThread notifierCreater(
            UDPReplayInfoBean udpReplayInfoBean) {
        return new CombinedNotifierThread(
                udpReplayInfoBean, this.socket);
    }

    public boolean getResult(String result) {
        if (result.trim().equalsIgnoreCase("false")) {
            sendObject("Result;No".getBytes());
            byte[] data = receiveObject(objLen);
            String str = new String(data);
            if (str.trim().equalsIgnoreCase("OK")) {
                Log.d("getResult", "return value abnormal! " + str);
                return true;
            }
            Log.d("getResult", "received result is: " + str);
        } else {
            sendObject("Result;Yes".getBytes());
            byte[] data = receiveObject(objLen);
            String str = new String(data);
            Log.d("getResult", "received result is: " + str);
        }
        return false;
    }

    public boolean getResult() {
        byte[] data = receiveObject(objLen);
        String str = new String(data);
        if (str.trim().equalsIgnoreCase("OK") || str.trim().contains("DONE")) {
            Log.d("getResult", "return value abnormal! " + str);
            return true;
        }
        Log.d("getResult", "received result is: " + str);
        return false;
    }

    public void closeSideChannelSocket() {
        try {
            socket.close();
            dataInputStream.close();
            dataOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * int fromByteArray(byte[] bytes) { return ByteBuffer.wrap(bytes).getInt();
     * }
     */

    /**
     * Rajesh's original code has bug, if message is more than 4096, this method
     * will return disordered byte
     * <p>
     * Fixed by adrian
     *
     * @param k
     * @return
     * @throws Exception
     */
    private byte[] receiveKbytes(int k) {
        int totalRead = 0;
        byte[] b = new byte[k];
        while (totalRead < k) {
            int bufSize = 4096;
            int bytesRead = 0;
            try {
                bytesRead = dataInputStream.read(b, totalRead,
                        Math.min(k - totalRead, bufSize));
                if (bytesRead < 0) {
                    throw new IOException("Data stream ended prematurely");
                }
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
            /*
             * if (k - totalRead < bytesRead) bytesRead = k - totalRead;
             */
            totalRead += bytesRead;
        }
        return b;
    }
}
