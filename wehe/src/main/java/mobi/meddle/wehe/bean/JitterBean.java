package mobi.meddle.wehe.bean;

import java.util.ArrayList;


public class JitterBean {

    public ArrayList<String> sentJitter;
    public ArrayList<byte[]> sentPayload;
    public ArrayList<String> rcvdJitter;
    public ArrayList<byte[]> rcvdPayload;

    public JitterBean() {
        super();
        this.sentJitter = new ArrayList<>();
        this.sentPayload = new ArrayList<>();
        this.rcvdJitter = new ArrayList<>();
        this.rcvdPayload = new ArrayList<>();
    }

}
