package mobi.meddle.wehe.combined;

import android.util.Log;

import java.util.ArrayList;
import java.util.TimerTask;

import mobi.meddle.wehe.constant.Consts;

/**
 * Created by Kirill Voloshin on 12/7/17.
 * <p>
 * Handles creation of the throughput data points.
 */
public class CombinedAnalyzerTask extends TimerTask {
    int bytesRead = 0; //the throughput for a data point
    private int executionCount = 0; //number of times the run() method has run
    private final ArrayList<Double> throughputs = new ArrayList<>(); //list of bytes read
    private final ArrayList<Double> slices = new ArrayList<>(); //list of slices (in seconds)
    private final ArrayList<Double> avgThroughputs = new ArrayList<>(); //list of avgThroughputs (megabits)
    private final double intervalDuration; //the sample rate, in seconds/samples

    /**
     * Constructor. Calculates the interval to determine length of one data point. Replay time is
     * limited to 45 seconds for TCP, 40 sec for UDP, 30 sec for ports.
     *
     * @param replayTime         time to run a replay in seconds
     * @param numberOfTimeSlices number of samples taken per replay
     */
    public CombinedAnalyzerTask(double replayTime, boolean isTCP, int numberOfTimeSlices,
                                boolean runPortTests) {
        super();
        double time = replayTime;
        if (Consts.TIMEOUT_ENABLED) {
            if (runPortTests) { //port test
                time = Math.min(time, Consts.REPLAY_PORT_TIMEOUT);
            } else if (isTCP) {//TCP app test
                time = Math.min(time, Consts.REPLAY_APP_TIMEOUT);
            } else { //UDP app test
                time = Math.min(time, Consts.REPLAY_APP_TIMEOUT - 5);
            }
        }
        this.intervalDuration = time / (double) numberOfTimeSlices;
        Log.d("IntervalCalc", "Time is " + time
                + " and slices are " + numberOfTimeSlices);
    }

    //creates a throughput data point, is run every intervalDuration seconds
    @Override
    public void run() {
        executionCount++;

        double xput = bytesRead; //throughput data point
        double slice = (double) executionCount * intervalDuration; //number of seconds into replay
        bytesRead = 0;

        throughputs.add(xput);
        slices.add(slice);
    }

    /**
     * Returns an interval to run the run() method above.
     *
     * @return interval in milliseconds
     */
    public long getInterval() {
        Log.d("INtervalll", " is " + intervalDuration);
        return Math.round(intervalDuration * 1000);
    }

    /**
     * Calculates average throughputs.
     *
     * @return the average throughputs and the slices
     */
    public ArrayList<ArrayList<Double>> getAverageThroughputsAndSlices() {
        ArrayList<ArrayList<Double>> averageThroughputsAndSlices = new ArrayList<>();
        for (int i = 0; i < throughputs.size(); i++) {
            double mBitsRead = throughputs.get(i) / 125000; //convert bytes to megabits
            double averageThroughput = mBitsRead / intervalDuration; //Mbps
            avgThroughputs.add(averageThroughput);
        }
        averageThroughputsAndSlices.add(avgThroughputs);
        averageThroughputsAndSlices.add(slices);
        return averageThroughputsAndSlices;
    }

    /**
     * Calculate the average throughput (in megabits) for a replay.
     * @return average throughput
     */
    public double getAvgThroughput() {
        double sum = 0;
        for (double avgThroughput : avgThroughputs) {
            sum += avgThroughput;
        }
        return sum / avgThroughputs.size();
    }
}
