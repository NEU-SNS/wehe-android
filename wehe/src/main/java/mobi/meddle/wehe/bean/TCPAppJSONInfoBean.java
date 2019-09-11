package mobi.meddle.wehe.bean;

import java.util.ArrayList;

public class TCPAppJSONInfoBean {

    private ArrayList<RequestSet> Q = null;
    private ArrayList<String> csPairs = null;
    private String replayName = null;
    private ApplicationBean appBean = null;

    public TCPAppJSONInfoBean() {
        Q = new ArrayList<RequestSet>();
        csPairs = new ArrayList<String>();
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
