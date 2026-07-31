package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Tests for CombinedAnalyzerTask (mobi.meddle.wehe.combined.CombinedAnalyzerTask, main sources).
 * <p>
 * This class is pure computation (no I/O, no threads beyond extending TimerTask, which we invoke
 * directly via run() rather than actually scheduling it on a real Timer), so it is fully testable
 * without fakes/mocks.
 */
public class CombinedAnalyzerTaskTest {

    // ---- constructor / getInterval() ----

    @Test
    public void getInterval_tcpAppTest_cappedByReplayAppTimeout() {
        // Consts.TIMEOUT_ENABLED == true and Consts.REPLAY_APP_TIMEOUT == 45 (Consts.java).
        // replayTime (100) > REPLAY_APP_TIMEOUT (45) for isTCP=true, runPortTests=false, so time
        // used is min(100, 45) = 45. With 5 slices, intervalDuration = 45/5 = 9s = 9000ms.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(100, true, 5, false);
        assertThat(task.getInterval()).isEqualTo(9000L);
    }

    @Test
    public void getInterval_udpAppTest_cappedByReplayAppTimeoutMinusFive() {
        // isTCP=false, runPortTests=false -> time = min(replayTime, REPLAY_APP_TIMEOUT - 5) = min(100, 40) = 40.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(100, false, 4, false);
        assertThat(task.getInterval()).isEqualTo(10000L); // 40/4 = 10s
    }

    @Test
    public void getInterval_portTest_cappedByReplayPortTimeout() {
        // runPortTests=true -> time = min(replayTime, REPLAY_PORT_TIMEOUT) = min(100, 30) = 30.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(100, true, 3, true);
        assertThat(task.getInterval()).isEqualTo(10000L); // 30/3 = 10s
    }

    @Test
    public void getInterval_replayTimeShorterThanCap_usesReplayTimeDirectly() {
        // When replayTime is already below the relevant cap, the cap has no effect.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 5, false);
        assertThat(task.getInterval()).isEqualTo(2000L); // 10/5 = 2s
    }

    @Test
    public void getInterval_roundsToNearestMillisecond() {
        // intervalDuration = 10/3 = 3.333...s -> 3333.33...ms, Math.round -> 3333ms.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 3, false);
        assertThat(task.getInterval()).isEqualTo(3333L);
    }

    // ---- run() / getAverageThroughputsAndSlices() ----

    @Test
    public void run_accumulatesThroughputAndSliceAndResetsBytesRead() {
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 5, false); // interval = 2s
        task.bytesRead = 1000;

        task.run(); // 1st execution: executionCount -> 1, slice = 1 * 2.0 = 2.0

        assertThat(task.bytesRead).isEqualTo(0); // reset after run()

        task.bytesRead = 500;
        task.run(); // 2nd execution: executionCount -> 2, slice = 2 * 2.0 = 4.0

        ArrayList<ArrayList<Double>> result = task.getAverageThroughputsAndSlices();
        ArrayList<Double> avgThroughputs = result.get(0);
        ArrayList<Double> slices = result.get(1);

        assertThat(slices).containsExactly(2.0, 4.0).inOrder();

        // avgThroughput = (bytesRead / 125000) / intervalDuration
        // 1st: (1000 / 125000) / 2 = 0.004
        // 2nd: (500 / 125000) / 2 = 0.002
        assertThat(avgThroughputs).hasSize(2);
        assertThat(avgThroughputs.get(0)).isWithin(1e-9).of(0.004);
        assertThat(avgThroughputs.get(1)).isWithin(1e-9).of(0.002);
    }

    @Test
    public void getAverageThroughputsAndSlices_calledTwice_duplicatesEntries() {
        // BUG-ish latent risk: getAverageThroughputsAndSlices() (CombinedAnalyzerTask.java
        // lines 76-86) appends into the *same* avgThroughputs field every time it's called,
        // instead of recomputing/clearing first. Calling it twice therefore doubles the list
        // instead of returning the same/idempotent result - any caller invoking this more than
        // once per task instance would silently get corrupted/duplicated data.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 5, false);
        task.bytesRead = 1000;
        task.run();

        ArrayList<ArrayList<Double>> first = task.getAverageThroughputsAndSlices();
        assertThat(first.get(0)).hasSize(1);

        ArrayList<ArrayList<Double>> second = task.getAverageThroughputsAndSlices();
        // avgThroughputs list itself now has 2 entries (the first appended twice), demonstrating
        // the lack of idempotency; the slices list (not touched by this method) stays size 1.
        assertThat(second.get(0)).hasSize(2);
        assertThat(second.get(1)).hasSize(1);
    }

    @Test
    public void getAvgThroughput_averagesAcrossDataPoints() {
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 5, false); // interval = 2s
        task.bytesRead = 1000;
        task.run(); // avgThroughput #1 = 0.004
        task.bytesRead = 3000;
        task.run(); // avgThroughput #2 = 0.012

        task.getAverageThroughputsAndSlices(); // populates avgThroughputs field used by getAvgThroughput()

        double avg = task.getAvgThroughput();
        assertThat(avg).isWithin(1e-9).of((0.004 + 0.012) / 2);
    }

    @Test
    public void getAvgThroughput_noDataPoints_isNaN() {
        // avgThroughputs is empty (getAverageThroughputsAndSlices() was never called), so
        // sum/avgThroughputs.size() is 0.0/0 = NaN. Any caller of getAvgThroughput() before
        // getAverageThroughputsAndSlices() gets NaN silently rather than an exception.
        CombinedAnalyzerTask task = new CombinedAnalyzerTask(10, true, 5, false);
        assertThat(task.getAvgThroughput()).isNaN();
    }
}
