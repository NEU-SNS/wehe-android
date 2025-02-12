package mobi.meddle.wehe.bean;

import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Contains information about a UDP replay.
 */
public class UDPReplayInfoBean {

    private ArrayList<DatagramChannel> udpSocketList = new ArrayList<>();
    private int senderCount = 0;
    private Queue<String> closeQ = new LinkedList<>();

    public synchronized Queue<String> getCloseQ() {
        return closeQ;
    }

    public synchronized void setCloseQ(Queue<String> closeQ) {
        this.closeQ = closeQ;
    }

    public synchronized ArrayList<DatagramChannel> getUdpSocketList() {
        return udpSocketList;
    }

    public synchronized void setUdpSocketList(ArrayList<DatagramChannel> udpSocketList) {
        this.udpSocketList = udpSocketList;
    }

    public synchronized int getSenderCount() {
        return senderCount;
    }

    public synchronized void setSenderCount(int senderCount) {
        this.senderCount = senderCount;
    }

    public synchronized void decrement() {
        senderCount -= 1;
    }

    public synchronized void addSocket(DatagramChannel channel) {
        udpSocketList.add(channel);
    }

    public synchronized void offerCloseQ(String str) {
        closeQ.offer(str);
    }

    public synchronized String pollCloseQ() {
        return closeQ.poll();
    }

}
