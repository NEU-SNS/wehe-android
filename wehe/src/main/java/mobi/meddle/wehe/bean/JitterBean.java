package mobi.meddle.wehe.bean;

import java.util.ArrayList;

/**
 * Tracks payloads sent to and received from the server for each UDP replay.
 * Currently, doesn't seem super useful, as the ArrayLists are only being added to, not used.
 */
public class JitterBean {
    public final ArrayList<String> sentJitter; //times since replay start in seconds of when payloads were sent
    public final ArrayList<byte[]> sentPayload; //the payloads sent to the server
    public final ArrayList<String> rcvdJitter; //times since replay start in seconds of when payloads were received
    public final ArrayList<byte[]> rcvdPayload; //the payloads received from the server

    public JitterBean() {
        this.sentJitter = new ArrayList<>();
        this.sentPayload = new ArrayList<>();
        this.rcvdJitter = new ArrayList<>();
        this.rcvdPayload = new ArrayList<>();
    }
}
