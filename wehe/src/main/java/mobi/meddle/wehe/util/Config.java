package mobi.meddle.wehe.util;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

/**
 * @author rajesh Configuration main class
 */
public class Config {

    static private final Properties properties = new Properties();

    /**
     * @param configFile Configuration file
     * @param context_c  Read properties file and put all of these key-value pairs in
     *                   properties
     */
    public static void readConfigFile(String configFile, @NonNull Context context_c) {
        AssetManager assetManager;
        InputStream inputStream;
        try {
            /*
             * getAssets() Return an AssetManager instance for your
             * application's package. AssetManager Provides access to an
             * application's raw asset files;
             */
            assetManager = context_c.getAssets();
            /*
             * Open an asset using ACCESS_STREAMING mode. This
             */
            inputStream = assetManager.open(configFile);
            /*
             * Loads properties from the specified InputStream,
             */
            properties.load(inputStream);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Replay", e.toString());
        }

    }

    /**
     * Put key value pair to properties object
     */
    public static void set(String key, String value) {
        properties.put(key, value);
    }

    /**
     * Get value for key from properties object
     */
    @NonNull
    public static String get(String key) {
        return Objects.requireNonNull(properties.get(key)).toString();
    }
}
