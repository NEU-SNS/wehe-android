package mobi.meddle.wehe.util;

import static org.junit.Assert.*;

import org.junit.Test;

public class RandomStringTest {

    @Test
    public void testRandomStringLength() {
        RandomString randomString = new RandomString(10);
        String result = randomString.nextString();
        // Check that the random string has the correct length
        assertEquals(10, result.length());
    }

    @Test
    public void testRandomStringCharacters() {
        RandomString randomString = new RandomString(10);
        String result = randomString.nextString();

        // Check that each character is valid (either '0'-'9' or 'a'-'z')
        for (char c : result.toCharArray()) {
            assertTrue("Invalid character in random string: " + c,
                    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z'));
        }
    }

    @Test
    public void testRandomStringUniqueness() {
        RandomString randomString = new RandomString(10);

        // Generate two random strings and ensure they are not equal
        String result1 = randomString.nextString();
        String result2 = randomString.nextString();
        assertNotEquals(result1, result2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomStringLengthException() {
        new RandomString(0); // Should throw IllegalArgumentException
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomStringNegativeLengthThrows() {
        new RandomString(-1); // negative length must also be rejected, not just 0
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRandomStringLargeNegativeLengthThrows() {
        new RandomString(Integer.MIN_VALUE);
    }

    @Test
    public void testRandomStringLengthOneEdgeCase() {
        RandomString randomString = new RandomString(1);
        String result = randomString.nextString();
        assertEquals(1, result.length());
        char c = result.charAt(0);
        assertTrue("Invalid character in single-char random string: " + c,
                (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z'));
    }

    @Test
    public void testRandomStringLargeLength() {
        RandomString randomString = new RandomString(1000);
        String result = randomString.nextString();
        assertEquals(1000, result.length());
    }
}
