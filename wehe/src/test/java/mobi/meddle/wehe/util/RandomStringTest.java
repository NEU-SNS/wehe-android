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
}
