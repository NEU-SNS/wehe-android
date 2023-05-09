package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.util.Config;

// @@@ Adrian add this

/**
 * Sends TCP replay packets to the server and receives the TCP throughputs.
 */
class CTCPClientThread implements Runnable {
    private final CombinedQueue queue;
    private final CTCPClient client;
    private final CombinedAnalyzerTask analyzerTask;
    private final RequestSet RS;
    private final Semaphore sendSema;
    private final Semaphore recvSema;
    private final int tolerance;
    private boolean addInfo = false;

    /**
     * Constructor.
     *
     * @param client       the TCP connection to the server
     * @param RS           the packet to send to the server
     * @param queue        the caller CombinedQueue - used for abort info
     * @param sendSema     Semaphore for sending packets
     * @param recvSema     Semaphore for receiving packets
     * @param tolerance    if the number of bytes received subtracted from the number of bytes
     *                     expected to be received is less than the tolerance, then something is
     *                     wrong, and an exception is thrown
     * @param analyzerTask the class containing the throughput data
     */
    CTCPClientThread(CTCPClient client, RequestSet RS, CombinedQueue queue, Semaphore sendSema,
                     Semaphore recvSema, int tolerance, CombinedAnalyzerTask analyzerTask) {
        this.client = client;
        this.analyzerTask = analyzerTask;
        this.RS = RS;
        this.queue = queue;
        this.sendSema = sendSema;
        this.recvSema = recvSema;
        this.tolerance = tolerance;
    }

    void timeout() {
        if (client != null && client.socket != null) {
            try {
                client.socket.close();
            } catch (IOException e) {
                Log.w("TCPClientThread", "Issue closing TCP socket", e);
            }
        }
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

            // Get Input/Output stream for socket
            DataOutputStream dataOutputStream = new DataOutputStream(
                    client.socket.getOutputStream());

            // convert payload to string format
            String tmp = new String(RS.getPayload(), StandardCharsets.UTF_8);

            if (client.addHeader && addInfo) {
                if (client.replayName.endsWith("-random")) {
                    // cook the custom info
                    String customInfo = String.format("X-rr;%s;%s;%s;X-rr", client.publicIP,
                            Config.get(client.replayName), client.CSPair);

                    byte[] customInfoByte = customInfo.getBytes();
                    byte[] newPayload;

                    // check the length of the payload
                    if (RS.getPayload().length > customInfoByte.length) {
                        newPayload = new byte[RS.getPayload().length];
                        Log.i("Sending", "adding header for random replay");
                        System.arraycopy(customInfoByte, 0, newPayload, 0,
                                customInfoByte.length);
                        System.arraycopy(RS.getPayload(), customInfoByte.length,
                                newPayload, customInfoByte.length,
                                RS.getPayload().length - customInfoByte.length);
                    } else {
                        Log.w("Sending", "payload length shorter than header, replace payload");
                        newPayload = customInfoByte;
                    }

                    dataOutputStream.write(newPayload);

                } else if (tmp.length() >= 3
                        && tmp.substring(0, 3).trim().equalsIgnoreCase("GET")) {
                    // cook the custom info
                    String customInfo = String.format("\r\nX-rr: %s;%s;%s\r\n",
                            client.publicIP, Config.get(client.replayName), client.CSPair);

                    if (tmp.getBytes().length != RS.getPayload().length) {
                        Log.e("Sending", "length of new byte array: " + tmp.getBytes().length
                                + " length of original payload: " + RS.getPayload().length);
                    }

                    String[] parts = tmp.split("\r\n", 2);
                    Log.i("Sending", "adding header for normal replay");
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

            sendSema.release();

            // Notify waiting Queue thread to start processing next packet and receive response
            if (RS.getResponse_len() > 0) {
                DataInputStream dataInStream = new DataInputStream(client.socket.getInputStream());

                int totalRead = 0;

                byte[] buffer = new byte[RS.getResponse_len()];
                while (totalRead < buffer.length) {
                    // @@@ offset is wrong?
                    int bufSize = 4096;
                    int bytesRead = dataInStream.read(buffer, totalRead,
                            Math.min(buffer.length - totalRead, bufSize));

                    if (bytesRead < 0 && buffer.length - totalRead < this.tolerance) {
                        Log.w("Receiving", "A few bytes missing, ignore and proceed");
                        break;
                    }

                    if (bytesRead < 0) {
                        Log.e("Receiving", "Not enough bytes! totalRead: "
                                + totalRead + " expected: " + buffer.length);

                        String data = new String(buffer, StandardCharsets.UTF_8);

                        if (data.length() >= 12 && data.substring(0, 12).trim()
                                .equalsIgnoreCase("SuspiciousClientIP!")) {
                            Log.e("TCPClientThread", "IP flipping detected");
                            throw new SocketException("IP flipping detected");
                        } else {
                            throw new SocketException("Traffic Manipulation Detected (Type 2)");
                        }
                    }

                    analyzerTask.bytesRead += bytesRead; //add to throughput data
                    totalRead += bytesRead;
                }

                String data = new String(buffer, StandardCharsets.UTF_8);
                if (data.length() >= 12 && data.substring(0, 12).trim()
                        .equalsIgnoreCase("SuspiciousClientIP!")) {
                    Log.e("TCPClientThread", data);
                    throw new SocketException("Traffic Manipulation Detected (Type 1)");
                }

                // adrian: manually free buffer
                Log.d("Finished", "receiving " + RS.getResponse_len() + " bytes " + System.nanoTime());
            } else {
                Log.d("Receiving", "skipped " + System.nanoTime());
            }
        } catch (SocketTimeoutException e) {
            Log.w("TCPClientThread", "Socket time out! Nothing has been sent or received"
                    + " for 30 seconds", e);
            synchronized (queue) {
                queue.ABORT = true;
                // make sure that this is not caused by other issues
                if (queue.abort_reason == null) {
                    queue.abort_reason = "Replay Aborted: replay socket error";
                }
            }
        } catch (SocketException e) {
            Log.w("TCPClientThread", "The maximum time to run a replay may have been"
                    + " reached. However, other reasons exist.", e);
            synchronized (queue) {
                queue.ABORT = true;
                if (queue.abort_reason == null) {
                    queue.abort_reason = "error_proxy";
                }
            }
        } catch (Exception e) {
            Log.e("TCPClientThread", "something bad happened!", e);
            // abort replay if bad things happened!
            synchronized (queue) {
                queue.ABORT = true;
                queue.abort_reason = "Replay Aborted: replay socket error";
            }
        } finally {
            recvSema.release();
            synchronized (queue) {
                --queue.threads;
            }
        }
    }
}
