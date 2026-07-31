package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class JitterBeanTest {

    private JitterBean bean;

    @Before
    public void setUp() {
        bean = new JitterBean();
    }

    @Test
    public void constructor_initializesAllListsEmptyNotNull() {
        assertNotNull(bean.sentJitter);
        assertNotNull(bean.sentPayload);
        assertNotNull(bean.rcvdJitter);
        assertNotNull(bean.rcvdPayload);

        assertTrue(bean.sentJitter.isEmpty());
        assertTrue(bean.sentPayload.isEmpty());
        assertTrue(bean.rcvdJitter.isEmpty());
        assertTrue(bean.rcvdPayload.isEmpty());
    }

    @Test
    public void listsAreIndependentAcrossInstances() {
        JitterBean other = new JitterBean();
        bean.sentJitter.add("0.5");
        bean.sentPayload.add(new byte[]{1, 2, 3});
        bean.rcvdJitter.add("0.6");
        bean.rcvdPayload.add(new byte[]{4, 5, 6});

        // A second instance must not see mutations made to the first instance's lists
        assertTrue(other.sentJitter.isEmpty());
        assertTrue(other.sentPayload.isEmpty());
        assertTrue(other.rcvdJitter.isEmpty());
        assertTrue(other.rcvdPayload.isEmpty());
    }

    @Test
    public void listsAreMutableAndAcceptAddedEntries() {
        bean.sentJitter.add("1.234");
        bean.sentPayload.add(new byte[]{10, 20});
        bean.rcvdJitter.add("2.345");
        bean.rcvdPayload.add(new byte[]{30, 40});

        assertEquals(1, bean.sentJitter.size());
        assertEquals("1.234", bean.sentJitter.get(0));
        assertArrayEquals(new byte[]{10, 20}, bean.sentPayload.get(0));
        assertEquals(1, bean.rcvdJitter.size());
        assertEquals("2.345", bean.rcvdJitter.get(0));
        assertArrayEquals(new byte[]{30, 40}, bean.rcvdPayload.get(0));
    }

    @Test
    public void fieldsAreFinal_referenceDoesNotChangeButContentsAreMutable() {
        // sentJitter etc. are declared `public final`, meaning the list reference cannot be
        // reassigned from outside, but the list contents are still fully mutable - this test
        // documents that "final" here only protects the reference, not the collection state.
        java.util.ArrayList<String> ref = bean.sentJitter;
        bean.sentJitter.add("x");
        assertSame(ref, bean.sentJitter);
        assertEquals(1, bean.sentJitter.size());
    }
}
