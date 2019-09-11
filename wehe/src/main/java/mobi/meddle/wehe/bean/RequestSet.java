package mobi.meddle.wehe.bean;

/**
 * @author rajesh RequestSet class for packet details
 */
public class RequestSet {
    private String c_s_pair;
    private double timestamp;
    private byte[] payload;
    // adrian: for tcp
    private int response_len;
    private String response_hash;
    // adrian: for udp
    private boolean end;

    // private int response_hash;
    public RequestSet() {
        super();
        this.c_s_pair = null;
        this.timestamp = 0;
        this.payload = null;
        this.response_len = -1;
        this.response_hash = null;
        this.end = false;
    }

    public String getc_s_pair() {
        return c_s_pair;
    }

    public void setc_s_pair(String c_s_pair) {
        this.c_s_pair = c_s_pair;
    }

    public String getResponse_hash() {
        return response_hash;
    }

    public void setResponse_hash(String response_hash) {
        this.response_hash = response_hash;
    }

    public int getResponse_len() {
        return response_len;
    }

    public void setResponse_len(int response_len) {
        this.response_len = response_len;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(double timestamp) {
        this.timestamp = timestamp;
    }

    public boolean getEnd() {
        return end;
    }

    public void setEnd(boolean end) {
        this.end = end;
    }

    @Override
    public String toString() {
        return "RequestSet [c_s_pair=" + c_s_pair + ", response_len="
                + response_len + " , timestamp=" + timestamp + "]";
    }

}