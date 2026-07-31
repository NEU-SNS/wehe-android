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

    @Test
    public void testGetUnsignedInt_Zero() {
        assertEquals(0L, UtilsManager.getUnsignedInt(0));
    }

    @Test
    public void testGetUnsignedInt_PositiveValue() {
        assertEquals(12345L, UtilsManager.getUnsignedInt(12345));
    }

    @Test
    public void testGetUnsignedInt_IntegerMaxValue() {
        // MAX_VALUE is already positive, so it should be unchanged
        assertEquals((long) Integer.MAX_VALUE, UtilsManager.getUnsignedInt(Integer.MAX_VALUE));
    }

    @Test
    public void testGetUnsignedInt_IntegerMinValue() {
        // Integer.MIN_VALUE = 0x80000000, interpreted unsigned = 2147483648
        long expected = 2147483648L;
        assertEquals(expected, UtilsManager.getUnsignedInt(Integer.MIN_VALUE));
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

    /**
     * BUG-ISH BEHAVIOR: an odd-length hex string throws StringIndexOutOfBoundsException.
     * UtilsManager.hexStringToByteArray (UtilsManager.java lines 15-23) allocates
     * `data = new byte[len / 2]` and then loops `for (i = 0; i < len; i += 2)`, reading
     * `s.charAt(i)` and `s.charAt(i + 1)` on every iteration. For an odd-length string (e.g. "4A6",
     * len=3), the last iteration has i=2, and s.charAt(i + 1) = s.charAt(3) is out of bounds
     * (valid indices are 0..2), so charAt throws StringIndexOutOfBoundsException *before* the
     * array store / bounds check on `data` even happens. This is a real latent bug: malformed
     * (odd-length) input from a hex string blows up with an unchecked runtime exception rather
     * than a clear validation error.
     */
    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testHexStringToByteArray_OddLength_ThrowsStringIndexOutOfBounds() {
        UtilsManager.hexStringToByteArray("4A6"); // length 3 (odd)
    }

    @Test(expected = StringIndexOutOfBoundsException.class)
    public void testHexStringToByteArray_SingleCharacter_Throws() {
        UtilsManager.hexStringToByteArray("4"); // length 1 (odd)
    }

    /**
     * BUG: hexStringToByteArray does NOT validate that characters are valid hex digits. It uses
     * Character.digit(c, 16), which returns -1 for any character that isn't a valid hex digit
     * (UtilsManager.java line 19-20). For an EVEN-length string containing an invalid character
     * like 'G', no exception is thrown at all - the method silently produces a garbage/wrong byte
     * instead of failing. This is a latent bug: callers get incorrect data silently rather than an
     * error signal for malformed hex input. Verified below: "4A6G" (len=4, even) does not throw,
     * and byte index 1 becomes (0x6 << 4) + (-1) = 0x5F instead of a valid hex-decoded value.
     */
    @Test
    public void testHexStringToByteArray_InvalidHexCharacter_EvenLength_DoesNotThrowAndProducesGarbageByte() {
        String hexString = "4A6G"; // 'G' is not a valid hex digit; length is even so no bounds issue
        byte[] result = UtilsManager.hexStringToByteArray(hexString);

        assertEquals(2, result.length);
        assertEquals((byte) 0x4A, result[0]);
        // Character.digit('6', 16) = 6, Character.digit('G', 16) = -1 => (6 << 4) + (-1) = 0x5F
        assertEquals((byte) 0x5F, result[1]);
    }

    @Test
    public void testHexStringToByteArray_LowercaseHexDigits() {
        String hexString = "4a6f"; // lowercase should be treated the same as uppercase
        byte[] expected = new byte[]{0x4A, 0x6F};
        assertArrayEquals(expected, UtilsManager.hexStringToByteArray(hexString));
    }
}
