package mobi.meddle.wehe.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.res.AssetManager;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(MockitoJUnitRunner.Silent.class)  // Changed to Silent runner
@FixMethodOrder(MethodSorters.NAME_ASCENDING) // deterministic order, needed by the leak-proof test below
public class ConfigTest {

    @Mock
    private Context mockContext;
    @Mock
    private AssetManager mockAssetManager;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockContext.getAssets()).thenReturn(mockAssetManager);
    }

    @Test
    public void testReadConfigFile_Success() throws IOException {
        // Mock property file content
        String fakeConfig = "key1=value1\nkey2=value2";
        InputStream inputStream = new ByteArrayInputStream(fakeConfig.getBytes());

        // Mock AssetManager behavior
        when(mockAssetManager.open("config.properties")).thenReturn(inputStream);

        // Call readConfigFile()
        Config.readConfigFile("config.properties", mockContext);

        // Verify property values
        assertEquals("value1", Config.get("key1"));
        assertEquals("value2", Config.get("key2"));
    }

    /**
     * readConfigFile() catches IOException internally and only logs it (see
     * Config.java readConfigFile, the catch(IOException e) block) - it never rethrows. So calling
     * readConfigFile() with a source that throws IOException must NOT throw, and must leave the
     * properties store untouched for the key that was never loaded. A subsequent get() on a key
     * that was never successfully loaded still throws NullPointerException because get() does
     * Objects.requireNonNull(properties.get(key)) and Properties.get() returns null for missing
     * keys.
     */
    @Test
    public void testReadConfigFile_FileNotFound_DoesNotThrow_AndGetStillNPEs() throws IOException {
        when(mockAssetManager.open("missing.properties")).thenThrow(new IOException("asset not found"));

        // Should not throw - IOException is caught and logged inside readConfigFile()
        Config.readConfigFile("missing.properties", mockContext);

        // The key was never loaded into the (shared, static) properties store, so get() throws NPE
        assertThrows(NullPointerException.class,
                () -> Config.get("keyThatWasNeverLoadedBecauseFileWasMissing"));
    }

    @Test
    public void testSetAndGet() {
        Config.set("testKey", "testValue");
        assertEquals("testValue", Config.get("testKey"));
    }

    @Test
    public void testGet_NonExistentKey() {
        assertThrows(NullPointerException.class, () -> Config.get("invalidKey"));
    }

    /**
     * DESIGN RISK: Config.properties is a single `static final` Properties instance shared by the
     * whole test JVM process (Config.java line 19). There is no reset/clear API, so any key set by
     * one test (via set() or a successful readConfigFile()) remains visible to every other test
     * that runs afterwards in the same JVM - across test *methods* and even across test *classes*
     * that share the same JVM/classloader (e.g. a single Gradle test worker).
     *
     * This pair of tests proves the leak deterministically: the class is annotated with
     * @FixMethodOrder(MethodSorters.NAME_ASCENDING) so "testZ_leak..." always runs after
     * "testM_leak...within the same run, with no @Before/@After clearing state in between (there
     * is no way to clear it - Config exposes no reset method). testZ observes a key it never set,
     * proving the static leak. We deliberately do not add a reset in production code; that would
     * hide the very risk being documented.
     */
    @Test
    public void testM_leakSetsKeyForLaterTestToObserve() {
        Config.set("leakedKeyFromLeakTest", "leakedValue");
        assertEquals("leakedValue", Config.get("leakedKeyFromLeakTest"));
    }

    @Test
    public void testZ_leakKeyIsStillVisibleFromEarlierTest() {
        // This test never calls Config.set("leakedKeyFromLeakTest", ...) itself. If Config.get()
        // still returns "leakedValue" here, that value can only have survived from
        // testM_leakSetsKeyForLaterTestToObserve, proving state leaks across test methods because
        // `properties` is static and never reset.
        assertEquals("leakedValue", Config.get("leakedKeyFromLeakTest"));
    }
}