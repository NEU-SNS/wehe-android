package mobi.meddle.wehe.util;

import static org.junit.Assert.*;
import org.junit.Test;

public class InstanceManagerTest {

    @Test
    public void testGetInstance_ExistingKey() {
        Instance instance = InstanceManager.getInstance("rajesh");

        assertNotNull("Instance should not be null for existing key", instance);
        assertEquals("54.200.20.20", instance.getName());
        assertEquals("rajesh", instance.getUsername());
        assertEquals("~", instance.getSsh_key());
    }

    @Test
    public void testGetInstance_NonExistingKey() {
        Instance instance = InstanceManager.getInstance("non_existent");

        assertNull("Instance should be null for a non-existing key", instance);
    }
}
