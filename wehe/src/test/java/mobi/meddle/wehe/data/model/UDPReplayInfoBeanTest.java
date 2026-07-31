package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.nio.channels.DatagramChannel;

public class UDPReplayInfoBeanTest {

    private UDPReplayInfoBean bean;

    @Before
    public void setUp() {
        bean = new UDPReplayInfoBean();
    }

    @Test
    public void newInstance_hasEmptySocketListAndZeroSenderCount() {
        assertNotNull(bean.getUdpSocketList());
        assertTrue(bean.getUdpSocketList().isEmpty());
        assertEquals(0, bean.getSenderCount());
    }

    @Test
    public void setSenderCount_updatesValue() {
        bean.setSenderCount(5);
        assertEquals(5, bean.getSenderCount());

        bean.setSenderCount(-3);
        assertEquals(-3, bean.getSenderCount());

        bean.setSenderCount(0);
        assertEquals(0, bean.getSenderCount());
    }

    @Test
    public void addSocket_addsToUdpSocketList() throws Exception {
        DatagramChannel channel = DatagramChannel.open();
        try {
            bean.addSocket(channel);
            assertEquals(1, bean.getUdpSocketList().size());
            assertSame(channel, bean.getUdpSocketList().get(0));
        } finally {
            channel.close();
        }
    }

    @Test
    public void addSocket_allowsNull() {
        // addSocket() has no null-check, so it silently accepts a null channel; documenting the
        // current (permissive) behavior rather than assuming validation exists.
        bean.addSocket(null);
        assertEquals(1, bean.getUdpSocketList().size());
        assertNull(bean.getUdpSocketList().get(0));
    }

    @Test
    public void addSocket_multipleTimesPreservesOrder() throws Exception {
        DatagramChannel c1 = DatagramChannel.open();
        DatagramChannel c2 = DatagramChannel.open();
        try {
            bean.addSocket(c1);
            bean.addSocket(c2);
            assertEquals(2, bean.getUdpSocketList().size());
            assertSame(c1, bean.getUdpSocketList().get(0));
            assertSame(c2, bean.getUdpSocketList().get(1));
        } finally {
            c1.close();
            c2.close();
        }
    }
}
