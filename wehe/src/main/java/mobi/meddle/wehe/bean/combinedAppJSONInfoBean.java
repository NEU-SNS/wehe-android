package mobi.meddle.wehe.bean;


import java.util.ArrayList;

public class combinedAppJSONInfoBean {

    private ArrayList<RequestSet> Q = null;
    private ArrayList<String> udpClientPorts = null;
    private ArrayList<String> tcpCSPs = null;
    private String replayName = null;
    private ApplicationBean appBean = null;

    public combinedAppJSONInfoBean() {
        Q = new ArrayList<>();
        udpClientPorts = new ArrayList<>();
        tcpCSPs = new ArrayList<>();
        replayName = null;
        appBean = new ApplicationBean();
    }

    public ArrayList<RequestSet> getQ() {
        return Q;
    }

    public void setQ(ArrayList<RequestSet> q) {
        Q = q;
    }

    public ArrayList<String> getTcpCSPs() {
        return tcpCSPs;
    }

    public void setTcpCSPs(ArrayList<String> tcpCSPs) {
        this.tcpCSPs = tcpCSPs;
    }

    public ArrayList<String> getUdpClientPorts() {
        return udpClientPorts;
    }

    public void setUdpClientPorts(ArrayList<String> udpClientPorts) {
        this.udpClientPorts = udpClientPorts;
    }

    public String getReplayName() {
        return replayName;
    }

    public void setReplayName(String replayName) {
        this.replayName = replayName;
    }

    public ApplicationBean getAppBean() {
        return appBean;
    }

    public void setAppBean(ApplicationBean appBean) {
        this.appBean = appBean;
    }

    public boolean isUDP() {
        return tcpCSPs.isEmpty();
    }

}
