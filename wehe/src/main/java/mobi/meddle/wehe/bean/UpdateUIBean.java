package mobi.meddle.wehe.bean;

/**
 * Helps keeps progress bar updated. Packets are sent in CombinedQueue.java. As packets are being
 * sent, CombinedQueue calls addProgress with the progress. A thread in runTest() in ReplayActivity
 * runs every 1/2 second to call getProgress to update the progress bar in onProgressUpdate() in
 * ReplayActivity.
 */
public class UpdateUIBean {
    private double progress;

    public UpdateUIBean() {
        this.progress = 0;
    }

    /**
     * Get current progress percentage. Android's progress bar API requires an int. Take the ceiling
     * so that last packet rounds progress from 99.xxx% to 100%.
     *
     * @return current progress
     */
    public synchronized int getProgress() {
        return (int) Math.ceil(progress);
    }

    /**
     * Add progress.
     *
     * @param progress a number between 0 and 100 (inclusive) that represents percent progress bar
     *                 should cover
     */
    public synchronized void addProgress(double progress) {
        this.progress += progress;
    }

    /**
     * Clears the progress of the progress bar.
     */
    public synchronized void clearProgress() {
        this.progress = 0;
    }

    /**
     * Set progress bar to 50% after 1st replay; set to 100% after 2nd replay.
     *
     * @param iteration replay number (1 for first replay; 2 for second replay)
     */
    public synchronized void finishProgress(int iteration) {
        if (iteration == 1) {
            this.progress = 50;
        } else {
            this.progress = 100;
        }
    }
}

