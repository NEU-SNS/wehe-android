package mobi.meddle.wehe.bean;

import androidx.annotation.NonNull;

/**
 * @author rajesh
 * Represents a packet in a replay to be sent
 * Fields in this class correspond with the fields in the replay files in the assets directory
 */
public class RequestSet {
    private String c_s_pair; //client-server pair, in the form {client_IP}.{client_port}-{server_IP}.{server_port}
    private double timestamp; //time when packet should be sent
    private byte[] payload; //the stuff to send
    // adrian: for tcp
    private int response_len; //expected length of response to a TCP packet being sent
    private String response_hash; //expected hash of response
    // adrian: for udp
    private boolean end;

    public RequestSet() {
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

    public boolean isUDP() {
        return getResponse_len() == -1;
    }

    @NonNull
    @Override
    public String toString() {
        return "RequestSet [c_s_pair=" + c_s_pair + ", response_len="
                + response_len + " , timestamp=" + timestamp + "]";
    }
}
