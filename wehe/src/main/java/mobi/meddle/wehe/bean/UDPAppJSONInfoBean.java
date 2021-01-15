package mobi.meddle.wehe.bean;

import java.util.ArrayList;
import java.util.HashMap;

public class UDPAppJSONInfoBean {

    private ArrayList<RequestSet> Q;
    private HashMap<String, ArrayList<Integer>> csPairs;
    private String replayName;
    private ApplicationBean appBean;

    public UDPAppJSONInfoBean() {
        Q = new ArrayList<>();
        csPairs = new HashMap<>();
        replayName = null;
        appBean = new ApplicationBean();
    }

    public ArrayList<RequestSet> getQ() {
        return Q;
    }

    public void setQ(ArrayList<RequestSet> q) {
        Q = q;
    }

    public HashMap<String, ArrayList<Integer>> getCsPairs() {
        return csPairs;
    }

    public void setCsPairs(HashMap<String, ArrayList<Integer>> csPairs) {
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
