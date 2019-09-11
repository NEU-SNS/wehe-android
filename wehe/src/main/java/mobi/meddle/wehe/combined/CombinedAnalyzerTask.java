package mobi.meddle.wehe.combined;


import android.util.Log;

import java.util.ArrayList;
import java.util.TimerTask;

/**
 * Created by Kirill Voloshin on 12/7/17.
 */

public class CombinedAnalyzerTask extends TimerTask {

    int bytesRead = 0;

    private int executionCount = 0;

    private ArrayList<Double> throughputs = new ArrayList<>();
    private ArrayList<Double> slices = new ArrayList<>();

    // in seconds
    private double intervalDuration;

    public CombinedAnalyzerTask(double replayTime, int numberOfTimeSlices) {
        super();

        this.intervalDuration = replayTime / (double) numberOfTimeSlices;


    }

    @Override
    public void run() {
        executionCount++;

        double xput = (double) bytesRead;
        double slice = (double) executionCount * intervalDuration;
        bytesRead = 0;

        throughputs.add(xput);
        slices.add(slice);
    }

    // returns interval in milliseconds
    public long getInterval() {
        Log.d("INtervalll", " is " + intervalDuration);
        return Math.round(intervalDuration * 1000);
    }

    public ArrayList<ArrayList<Double>> getAverageThroughputsAndSlices() {
        ArrayList<ArrayList<Double>> averageThroughputsAndSlices = new ArrayList<>();
        ArrayList<Double> averageThroughputs = new ArrayList<>();
        for (int i = 0; i < throughputs.size(); i++) {
            double mbitsRead = throughputs.get(i) / 125000;
            double averageThroughput = mbitsRead / intervalDuration;
            averageThroughputs.add(averageThroughput);
        }
        averageThroughputsAndSlices.add(averageThroughputs);
        averageThroughputsAndSlices.add(slices);
        return averageThroughputsAndSlices;
    }
}
