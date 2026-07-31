package mobi.meddle.wehe.data.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class CombinedAppJSONInfoBeanTest {

    private CombinedAppJSONInfoBean bean;

    @Before
    public void setUp() {
        bean = new CombinedAppJSONInfoBean();
    }

    @Test
    public void constructor_initializesEmptyListsAndNullReplayName() {
        assertNotNull(bean.getQ());
        assertTrue(bean.getQ().isEmpty());
        assertNotNull(bean.getUdpClientPorts());
        assertTrue(bean.getUdpClientPorts().isEmpty());
        assertNotNull(bean.getTcpCSPs());
        assertTrue(bean.getTcpCSPs().isEmpty());
        assertNull(bean.getReplayName());
    }

    @Test
    public void isTCP_falseWhenTcpCSPsEmpty() {
        assertFalse(bean.isTCP());
    }

    @Test
    public void isTCP_trueWhenTcpCSPsNonEmpty() {
        ArrayList<String> csps = new ArrayList<>();
        csps.add("1.2.3.4.1000-5.6.7.8.2000");
        bean.setTcpCSPs(csps);
        assertTrue(bean.isTCP());
    }

    @Test
    public void setQ_updatesQAndGetQReturnsSameReference() {
        ArrayList<RequestSet> requests = new ArrayList<>();
        RequestSet rs = new RequestSet("a-b", 1.0, new byte[]{1}, 10, "hash", false);
        requests.add(rs);

        bean.setQ(requests);

        assertSame(requests, bean.getQ());
        assertEquals(1, bean.getQ().size());
        assertSame(rs, bean.getQ().get(0));
    }

    @Test
    public void setUdpClientPorts_updatesValue() {
        ArrayList<String> ports = new ArrayList<>();
        ports.add("5000");
        bean.setUdpClientPorts(ports);
        assertSame(ports, bean.getUdpClientPorts());
    }

    @Test
    public void setReplayName_updatesValue() {
        bean.setReplayName("netflix");
        assertEquals("netflix", bean.getReplayName());
    }

    @Test
    public void isTCP_falseAgainAfterSettingEmptyListExplicitly() {
        bean.setTcpCSPs(new ArrayList<>());
        assertFalse(bean.isTCP());
    }
}
