package mobi.meddle.wehe.bean;

public class UpdateUIBean {

    private int progress;

    public UpdateUIBean() {
        super();
        this.progress = 0;
    }

    public synchronized int getProgress() {
        return progress;
    }

    public synchronized void setProgress(int progress) {
        this.progress = progress;
    }

}
