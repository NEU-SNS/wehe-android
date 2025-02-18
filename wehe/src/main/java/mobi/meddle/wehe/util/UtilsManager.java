package mobi.meddle.wehe.util;
import androidx.annotation.NonNull;

/**
 * Serialize/Deserialize objects
 *
 */
public class UtilsManager {

    public static long getUnsignedInt(int x) {
        return x & 0x00000000ffffffffL;
    }

    @NonNull
    public static byte[] hexStringToByteArray(@NonNull String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character
                    .digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
