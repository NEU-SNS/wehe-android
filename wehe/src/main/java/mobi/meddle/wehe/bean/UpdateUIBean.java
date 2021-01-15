package mobi.meddle.wehe.bean;

/**
 * Helps keeps progress bar updated. Packets are sent in CombinedQueue.java. As packets are being
 * sent, CombinedQueue calls setProgress with the progress. A thread in runTest() in ReplayActivity
 * runs every 1/2 second to call getProgress to update the progress bar in onProgressUpdate() in
 * ReplayActivity.
 */
public class UpdateUIBean {
    private int progress;

    public UpdateUIBean() {
        this.progress = 0;
    }

    public synchronized int getProgress() {
        return progress;
    }

    /**
     * Set progress.
     *
     * @param progress a number between 0 and 100 (inclusive) that represents percent progress bar
     *                 should cover
     */
    public synchronized void setProgress(int progress) {
        this.progress = progress;
    }
}
