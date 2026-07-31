package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * DeviceInfoBean's constructor does heavy direct Android-framework interaction (TelephonyManager,
 * ConnectivityManager, LocationManager, permission checks, Build fields) - Robolectric gives us a
 * real (shadowed) Context/Android runtime rather than having to mock every framework call
 * piece by piece.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DeviceInfoBeanTest {

    @Test
    public void constructor_populatesManufacturerAndModelFromBuild() {
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        assertEquals(android.os.Build.MANUFACTURER, bean.manufacturer);
        assertEquals(android.os.Build.MODEL, bean.model);
    }

    @Test
    public void constructor_populatesLocationNonNull() {
        // Regardless of permission state, DeviceInfoBean always ends up assigning some
        // android.location.Location object (real location or a "unknown" placeholder) -
        // it should never leave `location` null.
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        assertNotNull(bean.location);
    }

    @Test
    public void constructor_populatesNetworkTypeNonNull() {
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        assertNotNull(bean.networkType);
    }

    @Test
    public void constructor_populatesCarrierNameNonNull() {
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        // TelephonyManager.getNetworkOperatorName() never returns null (empty string if unknown)
        assertNotNull(bean.carrierName);
    }

    @Test
    public void constructor_setsCellInfoToFailedPlaceholder() {
        // DeviceInfoBean.java line 137 unconditionally sets cellInfo = "FAILED" and there is no
        // code anywhere in the constructor that ever changes it - so cellInfo is always
        // "FAILED" regardless of real device signal. This looks like dead/placeholder code -
        // documenting the current, seemingly-unfinished behavior.
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        assertEquals("FAILED", bean.cellInfo);
    }

    @Test
    public void constructor_osStringFollowsExpectedFormat() {
        Context context = ApplicationProvider.getApplicationContext();
        DeviceInfoBean bean = new DeviceInfoBean(context);

        // os is a private field; reflectively confirm the documented `String.format` (line 89-90)
        // was applied and produced the expected shape, since there's no getter for it.
        String expected = String.format("INCREMENTAL:%s, RELEASE:%s, SDK_INT:%s",
                android.os.Build.VERSION.INCREMENTAL, android.os.Build.VERSION.RELEASE,
                android.os.Build.VERSION.SDK_INT);

        try {
            java.lang.reflect.Field osField = DeviceInfoBean.class.getDeclaredField("os");
            osField.setAccessible(true);
            assertEquals(expected, osField.get(bean));
        } catch (ReflectiveOperationException e) {
            fail("Could not reflectively access DeviceInfoBean.os: " + e);
        }
    }

    @Test(expected = NullPointerException.class)
    public void constructor_throwsNpeWhenContextIsNull() {
        // Constructor is annotated @NonNull but that annotation is not enforced at runtime by the
        // JVM/Android by itself - it's only a lint/IDE hint unless the build explicitly injects
        // null-checks. DeviceInfoBean immediately calls context.getSystemService(...), so passing
        // null blows up with NPE rather than any clearer validation error. This documents the
        // real runtime behavior instead of assuming @NonNull enforces anything.
        new DeviceInfoBean(null);
    }
}
