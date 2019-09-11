package mobi.meddle.wehe.combined;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import mobi.meddle.wehe.bean.ServerInstance;


public class CUDPClient /* implements Runnable */ {
    public DatagramChannel channel = null;
    public int port = 0;
    private String publicIP = null;
    private Selector selector;
    //private int TIME_OUT = 1000;

    public CUDPClient(String publicIP) {
        super();
        this.publicIP = publicIP;
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Steps: 1- Create and connect TCP socket 2- Identifies itself --> tells
     * server what's replaying (replay_name and c_s_pair)
     */
    void createSocket() {
        try {
            byte[] buffer = "".getBytes();
            InetSocketAddress endPoint = new InetSocketAddress(publicIP, 100);
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length,
                    endPoint);
            channel = DatagramChannel.open();
            channel.socket().send(packet);
            channel.configureBlocking(false);
            this.port = channel.socket().getLocalPort();
            Log.d("UDPClient", "port is " + port);
            // channel.socket().bind(new InetSocketAddress(this.publicIP,
            // this.port));

            // register channel to selector
            channel.register(selector, SelectionKey.OP_WRITE);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    void sendUDPPacket(byte[] payload, ServerInstance instance) {
        // Log.d("sendUDP", "server IP: " + instance.server + " port: " +
        // instance.port);
		/*channel.disconnect();
		channel.connect(new InetSocketAddress(instance.server, Integer
				.parseInt(instance.port)));*/

        // only try to send when buffer is available
        // TODO: is it possible for this to block forever?
        //Log.w("UDPClient", "nav_about to wait!");
        try {
            selector.select();
            // send the packet
            this.channel.send(ByteBuffer.wrap(payload), new InetSocketAddress(
                    instance.server, Integer.parseInt(instance.port)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
