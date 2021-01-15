package mobi.meddle.wehe.combined;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import mobi.meddle.wehe.bean.ServerInstance;

/**
 * A UDP connection for a replay to send UDP packets.
 */
public class CUDPClient {
    DatagramChannel channel = null;
    private final String publicIP;
    private Selector selector;

    /**
     * Constructs a UDP connection.
     *
     * @param publicIP the user's public IP address
     */
    public CUDPClient(String publicIP) {
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
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, endPoint);
            channel = DatagramChannel.open();
            channel.socket().send(packet);
            channel.configureBlocking(false);
            int port = channel.socket().getLocalPort();
            Log.d("UDPClient", "public IP is " + publicIP + "; port is " + port);

            // register channel to selector
            channel.register(selector, SelectionKey.OP_WRITE);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Send a UDP packet to the server.
     *
     * @param payload  the payload to send to the server
     * @param instance the server to send the payload to
     */
    void sendUDPPacket(byte[] payload, @NonNull ServerInstance instance) {
        // Log.d("sendUDP", "server IP: " + instance.server + " port: " + instance.port);
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

    /**
     * Close the connection to the server.
     */
    public void close() {
        try {
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
