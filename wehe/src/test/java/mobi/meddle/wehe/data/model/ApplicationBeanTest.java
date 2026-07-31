package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * ApplicationBean is Parcelable and its round-trip logic (writeToParcel / the private
 * Parcel constructor) needs a real android.os.Parcel, which requires Robolectric rather than a
 * plain mock (Parcel.obtain() needs the Android runtime).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ApplicationBeanTest {

    @Test
    public void defaultConstructor_hasExpectedDefaults() {
        ApplicationBean bean = new ApplicationBean();

        assertEquals(0.0, bean.area_test, 0.0);
        assertEquals(0.0, bean.ks2pVal, 0.0);
        assertEquals(0.0, bean.ks2pRatio, 0.0);
        assertEquals(0.0, bean.originalThroughput, 0.0);
        assertEquals(0.0, bean.randomThroughput, 0.0);
        assertNull(bean.getName());
        assertEquals("Waiting to start", bean.getStatus());
        assertEquals("", bean.getError());
        assertEquals(0, bean.getTime());
        assertEquals(-1, bean.getHistoryCount());
        assertEquals(0, bean.getSize());
        assertNull(bean.getDataFile());
        assertNull(bean.getRandomDataFile());
        assertNull(bean.getImage());
        assertFalse(bean.isEnglishOnly());
        assertFalse(bean.isFrenchOnly());
        assertFalse(bean.getArcepNeedsAlerting());
        assertNull(bean.getCategory());
        assertFalse(bean.isTomography());
        assertEquals("", bean.getDifferentiationNetwork());
        assertFalse(bean.isAlertFCC());
    }

    @Test
    public void gettersAndSetters_roundTripCorrectly() {
        ApplicationBean bean = new ApplicationBean();

        bean.setName("Netflix");
        bean.setStatus("Running");
        bean.setError("timeout");
        bean.setTime(120);
        bean.setHistoryCount(42);
        bean.setSize(10);
        bean.setDataFile("data.gz");
        bean.setRandomDataFile("random.gz");
        bean.setImage("netflix.png");
        bean.setEnglishOnly(true);
        bean.setFrenchOnly(true);
        bean.setArcepNeedsAlerting(true);
        bean.setCategory(ApplicationBean.Category.VIDEO);
        bean.setTomography(true);
        bean.setDifferentiationNetwork("cellular");
        bean.setAlertFCC(true);

        assertEquals("Netflix", bean.getName());
        assertEquals("Running", bean.getStatus());
        assertEquals("timeout", bean.getError());
        assertEquals(120, bean.getTime());
        assertEquals(42, bean.getHistoryCount());
        assertEquals(10, bean.getSize());
        assertEquals("data.gz", bean.getDataFile());
        assertEquals("random.gz", bean.getRandomDataFile());
        assertEquals("netflix.png", bean.getImage());
        assertTrue(bean.isEnglishOnly());
        assertTrue(bean.isFrenchOnly());
        assertTrue(bean.getArcepNeedsAlerting());
        assertEquals(ApplicationBean.Category.VIDEO, bean.getCategory());
        assertTrue(bean.isTomography());
        assertEquals("cellular", bean.getDifferentiationNetwork());
        assertTrue(bean.isAlertFCC());
    }

    @Test
    public void describeContents_returnsZero() {
        assertEquals(0, new ApplicationBean().describeContents());
    }

    @Test
    public void creatorNewArray_createsArrayOfRequestedSize() {
        ApplicationBean[] array = ApplicationBean.CREATOR.newArray(3);
        assertEquals(3, array.length);
    }

    @Test
    public void parcelRoundTrip_preservesFieldsThatAreActuallyWritten() {
        ApplicationBean bean = new ApplicationBean();
        bean.setName("Netflix");
        bean.setDataFile("data.gz");
        bean.setSize(10);
        bean.setHistoryCount(42);
        bean.setImage("netflix.png");
        bean.setTime(120);
        bean.setRandomDataFile("random.gz");
        bean.originalThroughput = 5.5;
        bean.randomThroughput = 6.5;

        Parcel parcel = Parcel.obtain();
        try {
            bean.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            ApplicationBean restored = ApplicationBean.CREATOR.createFromParcel(parcel);

            assertEquals("Netflix", restored.getName());
            assertEquals("data.gz", restored.getDataFile());
            assertEquals(10, restored.getSize());
            assertEquals(42, restored.getHistoryCount());
            assertEquals("netflix.png", restored.getImage());
            assertEquals(120, restored.getTime());
            assertEquals("random.gz", restored.getRandomDataFile());
            assertEquals(5.5, restored.originalThroughput, 0.0);
            assertEquals(6.5, restored.randomThroughput, 0.0);
        } finally {
            parcel.recycle();
        }
    }

    /**
     * BUG: writeToParcel()/the private Parcel constructor (ApplicationBean.java lines 216-227 and
     * 58-72) only serialize 10 of the bean's ~19 fields: name, dataFile, size, historyCount,
     * isSelected, image, time, randomDataFile, originalThroughput, randomThroughput. Fields like
     * status, error, englishOnly, frenchOnly, arcepNeedsAlerting, cat (Category), isTomography,
     * differentiationNetwork, alertFCC, area_test, ks2pVal, and ks2pRatio are NEVER written to the
     * Parcel and NEVER restored - they silently reset to their class-default values on the
     * receiving side of any Parcelable transfer (e.g. passing an ApplicationBean between
     * Activities/Fragments via an Intent/Bundle). This test proves the data loss concretely.
     */
    @Test
    public void parcelRoundTrip_silentlyDropsFieldsNotIncludedInWriteToParcel() {
        ApplicationBean bean = new ApplicationBean();
        bean.setStatus("Running");
        bean.setError("some error");
        bean.setEnglishOnly(true);
        bean.setFrenchOnly(true);
        bean.setArcepNeedsAlerting(true);
        bean.setCategory(ApplicationBean.Category.MUSIC);
        bean.setTomography(true);
        bean.setDifferentiationNetwork("wifi");
        bean.setAlertFCC(true);
        bean.area_test = 1.23;
        bean.ks2pVal = 4.56;
        bean.ks2pRatio = 7.89;

        Parcel parcel = Parcel.obtain();
        try {
            bean.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            ApplicationBean restored = ApplicationBean.CREATOR.createFromParcel(parcel);

            // None of these values survive the round trip - they all fall back to defaults.
            assertEquals("Waiting to start", restored.getStatus());
            assertEquals("", restored.getError());
            assertFalse(restored.isEnglishOnly());
            assertFalse(restored.isFrenchOnly());
            assertFalse(restored.getArcepNeedsAlerting());
            assertNull(restored.getCategory());
            assertFalse(restored.isTomography());
            assertEquals("", restored.getDifferentiationNetwork());
            assertFalse(restored.isAlertFCC());
            assertEquals(0.0, restored.area_test, 0.0);
            assertEquals(0.0, restored.ks2pVal, 0.0);
            assertEquals(0.0, restored.ks2pRatio, 0.0);
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void categoryEnum_valuesContainsAllExpectedConstants() {
        ApplicationBean.Category[] values = ApplicationBean.Category.values();
        assertEquals(5, values.length);
        assertEquals(ApplicationBean.Category.VIDEO, ApplicationBean.Category.valueOf("VIDEO"));
        assertEquals(ApplicationBean.Category.MUSIC, ApplicationBean.Category.valueOf("MUSIC"));
        assertEquals(ApplicationBean.Category.CONFERENCING,
                ApplicationBean.Category.valueOf("CONFERENCING"));
        assertEquals(ApplicationBean.Category.SMALL_PORT,
                ApplicationBean.Category.valueOf("SMALL_PORT"));
        assertEquals(ApplicationBean.Category.LARGE_PORT,
                ApplicationBean.Category.valueOf("LARGE_PORT"));
    }
}
