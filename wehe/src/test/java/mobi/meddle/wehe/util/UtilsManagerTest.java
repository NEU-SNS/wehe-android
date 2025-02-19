package mobi.meddle.wehe.util;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsManagerTest {

    // Test for getUnsignedInt method
    @Test
    public void testGetUnsignedInt() {
        int input = -1; // Example input (-1 as a signed integer)
        long expectedOutput = 0x00000000ffffffffL; // Unsigned equivalent of -1

        long result = UtilsManager.getUnsignedInt(input);

        assertEquals("The unsigned int conversion is incorrect", expectedOutput, result);
    }

    // Test for hexStringToByteArray method
    @Test
    public void testHexStringToByteArray() {
        String hexString = "4A6F686E"; // Example hex string for "John"
        byte[] expectedResult = new byte[]{0x4A, 0x6F, 0x68, 0x6E};

        byte[] result = UtilsManager.hexStringToByteArray(hexString);

        assertArrayEquals("Hex string to byte array conversion is incorrect", expectedResult, result);
    }

    // Test for hexStringToByteArray with empty string
    @Test
    public void testHexStringToByteArrayEmpty() {
        String hexString = "";
        byte[] expectedResult = new byte[]{};

        byte[] result = UtilsManager.hexStringToByteArray(hexString);

        assertArrayEquals("Empty hex string should return empty byte array", expectedResult, result);
    }

//    // Test for hexStringToByteArray with invalid input (odd-length string)
//    @Test(expected = StringIndexOutOfBoundsException.class)
//    public void testHexStringToByteArrayInvalidInput() {
//        String hexString = "4A6G"; // Invalid hex string (contains 'G')
//
//        UtilsManager.hexStringToByteArray(hexString);
//    }
}
