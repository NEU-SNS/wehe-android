package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class UpdateUIBeanTest {

    private UpdateUIBean bean;

    @Before
    public void setUp() {
        bean = new UpdateUIBean();
    }

    @Test
    public void newInstance_progressIsZero() {
        assertEquals(0, bean.getProgress());
    }

    @Test
    public void addProgress_ceilsFractionalUp() {
        // getProgress() takes Math.ceil() of the underlying double (UpdateUIBean.java line 23)
        // so that any nonzero fraction rounds UP, e.g. so the final packet nudges 99.x% to 100%.
        bean.addProgress(0.1);
        assertEquals(1, bean.getProgress());
    }

    @Test
    public void addProgress_exactIntegerDoesNotRoundUp() {
        bean.addProgress(50.0);
        assertEquals(50, bean.getProgress());
    }

    @Test
    public void addProgress_accumulatesAcrossMultipleCalls() {
        bean.addProgress(30.0);
        bean.addProgress(20.5);
        // 50.5 -> ceil -> 51
        assertEquals(51, bean.getProgress());
    }

    @Test
    public void addProgress_almostHundredRoundsUpToHundred() {
        bean.addProgress(99.999);
        assertEquals(100, bean.getProgress());
    }

    @Test
    public void clearProgress_resetsToZero() {
        bean.addProgress(75.0);
        assertEquals(75, bean.getProgress());

        bean.clearProgress();
        assertEquals(0, bean.getProgress());
    }

    @Test
    public void addProgress_negativeValueDecreasesProgress() {
        // addProgress() has no validation/clamping (UpdateUIBean.java line 32-34: just
        // `this.progress += progress`), so it's possible to push progress below 0 or above 100 -
        // documenting that there is no bounds enforcement in this class.
        bean.addProgress(10.0);
        bean.addProgress(-20.0);
        assertEquals(-10, bean.getProgress());
    }

    @Test
    public void addProgress_canExceedOneHundred() {
        bean.addProgress(150.0);
        assertEquals(150, bean.getProgress());
    }
}
