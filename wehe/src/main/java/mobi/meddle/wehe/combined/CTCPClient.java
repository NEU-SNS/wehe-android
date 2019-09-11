package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;

public class CTCPClient /* implements Runnable */ {
    public String CSPair = null;
    public Socket socket = null;
    public String publicIP = null;
    public boolean addHeader = false;
    public String replayName = null;
    public SocketChannel sc = null;
    // TODO: Check proper usage later
    public AtomicBoolean flag = new AtomicBoolean();
    private String destIP = null;
    private int destPort;
    /*
     * private int port; private int NATPort;
     */
    private String id = null;
    // use this to signal client thread to add header for the first packet

    public CTCPClient(String cSPair, String destIP, int destPort,
                      String randomID, String replayName, String publicIP,
                      boolean addHeader) {
        super();
        CSPair = cSPair;
        this.destIP = destIP;
        this.destPort = destPort;
        this.id = randomID;
        this.replayName = replayName;
        this.publicIP = publicIP;
        this.addHeader = addHeader;
    }

    /**
     * Steps: 1- Create and connect TCP socket 2- Identifies itself --> tells
     * server what's replaying (replay_name and c_s_pair)
     */
    public void createSocket() {
        try {
            socket = new Socket();
            InetSocketAddress endPoint = new InetSocketAddress(destIP, destPort);
            socket.setTcpNoDelay(true);
            socket.setReuseAddress(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(30000);
            socket.connect(endPoint);

            // this.identify();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Before anything, client needs to identify itself to the server and tell
     * which c_s_pair it will be replaying.
     *
     * @throws IOException
     */
	/*public void identify() throws Exception {
		Log.d("Replay", id + ";" + this.CSPair + ";" + replayName);
		byte[] message = (id + ";" + this.CSPair + ";" + replayName).getBytes();
		sendObject(message);

	}*/

	/*private void sendObject(byte[] buf) throws Exception {
		DataOutputStream dataOutputStream = new DataOutputStream(
				socket.getOutputStream());
		dataOutputStream.writeBytes(String.format("%010d", buf.length));
		dataOutputStream.write(buf);
	}*/
    public void close() {
        try {
            Log.d("Client", "Socket is now closed");
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean equals(Object o) {
        return this.CSPair.equals(((CTCPClient) o).CSPair);
    }

    @Override
    public int hashCode() {
        return CSPair.hashCode();
    }
}
