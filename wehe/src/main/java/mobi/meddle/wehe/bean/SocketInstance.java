package mobi.meddle.wehe.bean;

public class SocketInstance {
    private String IP;
    private int port;
    private String CSPair;

    public SocketInstance(String iP, int port, String cSPair) {
        this.IP = iP;
        this.port = port;
        this.CSPair = cSPair;
    }

    public String getIP() {
        return IP;
    }

    public void setIP(String iP) {
        IP = iP;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getCSPair() {
        return CSPair;
    }

    public void setCSPair(String cSPair) {
        CSPair = cSPair;
    }

}
