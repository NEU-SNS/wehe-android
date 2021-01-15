package mobi.meddle.wehe.bean;

public class ServerInstance {
    public String server;
    public final String port;

    public ServerInstance(String server, String port) {
        this.server = server;
        this.port = port;
    }
}
