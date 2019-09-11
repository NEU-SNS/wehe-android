package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import mobi.meddle.wehe.bean.UDPReplayInfoBean;


public final class CombinedNotifierThread implements Runnable {

    // changes of Arash
    public volatile boolean doneSending;
    // SocketChannel channel = null;
    // private int TIME_OUT = 1000;
    // private int senderCount = 0;
    private UDPReplayInfoBean udpReplayInfoBean = null;
    private DataInputStream dataInputStream = null;
    private int inProcess = 0;
    private int total = 0;

    CombinedNotifierThread(UDPReplayInfoBean udpReplayInfoBean,
                           Socket socket) {
        super();
        this.udpReplayInfoBean = udpReplayInfoBean;
        this.doneSending = false;

        if (socket.isConnected()) {
            try {
                DataOutputStream dataOutputStream = new DataOutputStream(
                        socket.getOutputStream());
                dataInputStream = new DataInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            Log.d("Notifier", "socket not connected!");
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("CombinedNotifierThread (Thread)");
        try {
            // Selector selector = Selector.open();
            // channel.register(selector, SelectionKey.OP_READ);

            while (true) {
                if (dataInputStream.available() > 0) {

                    int objLen = 10;
                    byte[] data = receiveObject(objLen);
                    String[] Notf = new String(data).split(";");
                    if (Notf[0].equalsIgnoreCase("STARTED")) {
                        inProcess += 1;
                        total += 1;
                        // udpReplayInfoBean.offerCloseQ(Notf[1]);
                        Log.d("Notifier", "received STARTED!");
                        // closeQ.add(Notf[1]);
                    } else if (Notf[0].equalsIgnoreCase("DONE")) {
                        inProcess -= 1;
                        // udpReplayInfoBean.decrement();
                        Log.d("Notifier", "received DONE!");
                    } else {
                        Log.d("Notifier", "WTF???");
                        break;
                    }
                } else {
                    Thread.sleep(500);
                }

                if (doneSending) {
                    if (inProcess == 0) {
                        //selector.close();
                        Log.d("Notifier",
                                "Done notifier! total: " + total
                                        + " udpSenderCount: "
                                        + udpReplayInfoBean.getSenderCount());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            Log.d("Notifier", "receive data error!");
            e.printStackTrace();
        }

        Log.d("Notifier", "received all packets!");

    }

    private byte[] receiveObject(int objLen) {
        byte[] recvObjSizeBytes = receiveKbytes(objLen);
        // Log.d("Obj", new String(recvObjSizeBytes));
        int recvObjSize = Integer.parseInt((new String(recvObjSizeBytes)));
        // Log.d("Obj", String.valueOf(recvObjSize));
        return receiveKbytes(recvObjSize);
    }

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
            }
            /*
             * if (k - totalRead < bytesRead) bytesRead = k - totalRead;
             */
            totalRead += bytesRead;
        }
        return b;
    }

}
