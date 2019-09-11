package mobi.meddle.wehe.bean;

public class SocketInstance {
    private String IP = null;
    private int port;
    private String CSPair = null;

    public SocketInstance(String iP, int port, String cSPair) {
        // @@@ a little confused, this class doesn't have a father class, why call super()?
        super();
        IP = iP;
        this.port = port;
        CSPair = cSPair;
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
