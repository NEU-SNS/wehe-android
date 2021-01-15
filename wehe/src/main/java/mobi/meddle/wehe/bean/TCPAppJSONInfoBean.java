package mobi.meddle.wehe.bean;

import java.util.ArrayList;

public class TCPAppJSONInfoBean {

    private ArrayList<RequestSet> Q;
    private ArrayList<String> csPairs;
    private String replayName;
    private ApplicationBean appBean;

    public TCPAppJSONInfoBean() {
        Q = new ArrayList<>();
        csPairs = new ArrayList<>();
        replayName = null;
        appBean = new ApplicationBean();
    }

    public ArrayList<RequestSet> getQ() {
        return Q;
    }

    public void setQ(ArrayList<RequestSet> q) {
        Q = q;
    }

    public ArrayList<String> getCsPairs() {
        return csPairs;
    }

    public void setCsPairs(ArrayList<String> csPairs) {
        this.csPairs = csPairs;
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

}
