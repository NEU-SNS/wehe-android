package mobi.meddle.wehe.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.res.AssetManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@RunWith(MockitoJUnitRunner.Silent.class)  // Changed to Silent runner
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

//    @Test
//    public void testReadConfigFile_FileNotFound() throws IOException {
//        when(mockAssetManager.open("missing.properties")).thenThrow(new IOException());
//
//        // Should not throw exception, just log the error
//        Config.readConfigFile("missing.properties", mockContext);
//
//        assertThrows(NullPointerException.class, () -> Config.get("nonexistent"));
//    }

    @Test
    public void testSetAndGet() {
        Config.set("testKey", "testValue");
        assertEquals("testValue", Config.get("testKey"));
    }

    @Test
    public void testGet_NonExistentKey() {
        assertThrows(NullPointerException.class, () -> Config.get("invalidKey"));
    }
}