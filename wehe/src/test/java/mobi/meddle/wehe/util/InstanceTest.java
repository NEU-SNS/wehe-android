package mobi.meddle.wehe.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class InstanceTest {

    private Instance instance;

    @Before
    public void setUp() {
        // Create a new instance of the Instance class before each test
        instance = new Instance("Server1", "admin", "ssh-key-123");
    }

    @Test
    public void testConstructorAndGetters() {
        // Verify the initial values set by the constructor
        assertEquals("Server1", instance.getName());
        assertEquals("admin", instance.getUsername());
        assertEquals("ssh-key-123", instance.getSsh_key());
    }

    @Test
    public void testSetName() {
        // Change the name and verify the updated value
        instance.setName("Server2");
        assertEquals("Server2", instance.getName());
    }

    @Test
    public void testSetUsername() {
        // Change the username and verify the updated value
        instance.setUsername("root");
        assertEquals("root", instance.getUsername());
    }

    @Test
    public void testSetSshKey() {
        // Change the SSH key and verify the updated value
        instance.setSsh_key("ssh-key-456");
        assertEquals("ssh-key-456", instance.getSsh_key());
    }

    @Test
    public void testToString() {
        // Verify the toString method
        String expected = "Instance [name=Server1, username=admin, ssh_key=ssh-key-123]";
        assertEquals(expected, instance.toString());
    }
}
