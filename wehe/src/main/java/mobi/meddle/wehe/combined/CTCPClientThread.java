package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.util.Config;


// @@@ Adrian add this

class CTCPClientThread implements Runnable {

    private final CombinedQueue queue;
    private CTCPClient client;
    private CombinedAnalyzerTask analyzerTask;
    private RequestSet RS = null;
    private Semaphore sendSema;
    private Semaphore recvSema;
    private long timeOrigin = 0;
    private int tolerance = 0;
    private boolean addInfo = false;

    CTCPClientThread(CTCPClient client, RequestSet RS,
                     CombinedQueue queue, Semaphore sendSema, Semaphore recvSema,
                     long timeOrigin, int tolerance, CombinedAnalyzerTask analyzerTask) {
        this.client = client;
        this.analyzerTask = analyzerTask;
        this.RS = RS;
        this.queue = queue;
        this.sendSema = sendSema;
        this.recvSema = recvSema;
        this.timeOrigin = timeOrigin;
        this.tolerance = tolerance;
    }

    /**
     * Steps: 1- Send out the payload 2- Set send_event to notify you are done
     * sending 3- Receive response (if any) 4- Set self.event to notify you are
     * done receiving
     */
    @Override
    public void run() {
        Thread.currentThread().setName("CTCPClientThread (Thread)");
        try {

            if (client.socket == null) {
                client.createSocket();
                addInfo = true;
            }

			/*if (!client.socket.isConnected())
				Log.w("TCPClientThread", "socket not connected!");*/

            // Get Input/Output stream for socket
            DataOutputStream dataOutputStream = new DataOutputStream(
                    client.socket.getOutputStream());

			/*Log.d("Sending", "payload " + RS.getPayload().length +
					" bytes, expecting " + RS.getResponse_len() + " bytes ");*/

            // convert payload to string format
            String tmp = new String(RS.getPayload(), StandardCharsets.UTF_8);

			/*Log.d("Sending", "length of string: " + tmp.length()
					+ " length of payload: " + RS.getPayload().length);*/
			/*if (tmp.length() >= 20)
				Log.d("Sending", "First 20 bytes: " + tmp.substring(0, 20));
			else
				Log.d("Sending", "Short content: " + tmp);*/
            if (client.addHeader && addInfo) {
                if (client.replayName.endsWith("-random")) {
                    // cook the custom info
                    String customInfo = String.format("X-rr;%s;%s;%s;X-rr",
                            client.publicIP, Config.get(client.replayName),
                            client.CSPair);

                    byte[] customInfoByte = customInfo.getBytes();
                    byte[] newPayload;

                    // check the length of the payload
                    if (RS.getPayload().length > customInfoByte.length) {
                        newPayload = new byte[RS.getPayload().length];
                        Log.d("Sending", "adding header for random replay");
                        System.arraycopy(customInfoByte, 0, newPayload, 0,
                                customInfoByte.length);
                        System.arraycopy(RS.getPayload(),
                                customInfoByte.length, newPayload,
                                customInfoByte.length, RS.getPayload().length
                                        - customInfoByte.length);
                    } else {
                        Log.w("Sending",
                                "payload length shorter than header, replace payload");
                        newPayload = customInfoByte;
                    }

                    dataOutputStream.write(newPayload);

                } else if (tmp.length() >= 3
                        && tmp.substring(0, 3).trim().equalsIgnoreCase("GET")) {
                    // cook the custom info
                    String customInfo = String.format("\r\nX-rr: %s;%s;%s\r\n",
                            client.publicIP, Config.get(client.replayName),
                            client.CSPair);

                    if (tmp.getBytes().length != RS.getPayload().length)
                        Log.e("Sending",
                                "length of new byte array: "
                                        + tmp.getBytes().length
                                        + " length of original payload: "
                                        + RS.getPayload().length);

                    String[] parts = tmp.split("\r\n", 2);
                    Log.d("Sending", "adding header for normal replay");
                    tmp = parts[0] + customInfo + parts[1];

                    dataOutputStream.write(tmp.getBytes());

                } else {
                    Log.w("Sending", "first packet not touched! content:\n"
                            + tmp + "\ndst port: " + client.socket.getPort());
                    dataOutputStream.write(RS.getPayload());
                }

            } else {
                // send payload directly
                dataOutputStream.write(RS.getPayload());
            }

			/*Log.d("Sent", "payload " + RS.getPayload().length +
					" bytes, expecting " + RS.getResponse_len() + " bytes ");*/

            sendSema.release();

            // Notify waiting Queue thread to start processing next packet
            if (RS.getResponse_len() > 0) {
                DataInputStream dataInputStream = new DataInputStream(
                        client.socket.getInputStream());

                int totalRead = 0;

				/*Log.d("Receiving", String.valueOf(RS.getResponse_len()) + " bytes"
						+ " start at time " +
						String.valueOf((System.nanoTime() - timeOrigin) / 1000000000));*/

                byte[] buffer = new byte[RS.getResponse_len()];
                while (totalRead < buffer.length) {
                    // @@@ offset is wrong?
                    int bufSize = 4096;
                    int bytesRead = dataInputStream.read(buffer, totalRead,
                            Math.min(buffer.length - totalRead, bufSize));
					/*Log.i("Receiving", "Read " + bytesRead + " bytes out of "
							+ buffer.length);*/

                    // Log.d("Payload " + RS.getResponse_len(),
                    // String.valueOf(buffer));
                    // int bytesRead = dataInputStream.read(buffer);
                    // Log.d("Received " + RS.getResponse_len(),
                    // String.valueOf(bytesRead));

                    if (bytesRead < 0 && buffer.length - totalRead < this.tolerance) {
                        Log.w("Receiving", "A few bytes missing, ignore and proceed");
                        break;
                    }

                    if (bytesRead < 0) {
                        Log.e("Receiving", "Not enough bytes! totalRead: "
                                + totalRead + " expected: " + buffer.length);

                        String data = new String(buffer, StandardCharsets.UTF_8);

                        if (data.length() >= 12
                                && data.substring(0, 12).trim()
                                .equalsIgnoreCase("WhoTheFAreU?")) {
                            Log.e("TCPClientThread", "IP flipping detected");
                            throw new SocketException(
                                    "IP flipping detected");
                        } else
                            throw new SocketException(
                                    "Traffic Manipulation Detected (Type 2)");
                        // String data = new String(buffer, "UTF-8");
                        // Log.w("Receiving", data);
                    }

                    analyzerTask.bytesRead += bytesRead;
                    totalRead += bytesRead;
                }

                String data = new String(buffer, StandardCharsets.UTF_8);
                if (data.length() >= 12
                        && data.substring(0, 12).trim()
                        .equalsIgnoreCase("WhoTheFAreU?")) {
                    Log.e("TCPClientThread", data);
                    throw new SocketException(
                            "Traffic Manipulation Detected (Type 1)");
                }
				/*else
					Log.d("Receiving", "content for " + buffer.length + "\n" + data);*/

                // adrian: increase current pointer
				/*synchronized (recvQueueBean) {
					recvQueueBean.current ++;
					recvQueueBean.notifyAll();
				}*/

                // adrian: manually free buffer
                Log.d("Finished",
                        "receiving " + RS.getResponse_len()
                                + " bytes");
            } else {
                Log.d("Receiving", "skipped");
            }
        } catch (SocketTimeoutException e) {
            Log.w("TCPClientThread", "Socket time out!");
            synchronized (queue) {
                queue.ABORT = true;
                // make sure that this is not caused by other issues
                if (queue.abort_reason == null)
                    queue.abort_reason = "Replay Aborted: replay socket error";
            }

        } catch (SocketException e) {
            synchronized (queue) {
                queue.ABORT = true;
                if (queue.abort_reason == null)
                    queue.abort_reason = "error_proxy";
            }
            e.printStackTrace();
        } catch (Exception e) {
            Log.e("TCPClientThread", "something bad happened!");
            // abort replay if bad things happened!
            synchronized (queue) {
                queue.ABORT = true;
                queue.abort_reason = "Replay Aborted: replay socket error";
            }
            e.printStackTrace();
        } finally {
            recvSema.release();
            synchronized (queue) {
                --queue.threads;
            }

        }
    }
}
