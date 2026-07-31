package mobi.meddle.wehe.combined;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import javax.websocket.CloseReason;
import javax.websocket.Session;

/**
 * Tests for WebSocketConnection (mobi.meddle.wehe.combined.WebSocketConnection, main sources).
 * <p>
 * WebSocketConnection's only constructor performs a real network handshake (via Tyrus'
 * WebSocketContainer) with a hand-rolled "poll every 500ms, up to 10 times" 5-second timeout
 * (WebSocketConnection.java lines 47-73) - that is fundamentally an integration-level concern
 * (needs a live WebSocket server) and is deliberately NOT exercised here; driving it against a
 * fake/unreachable URI would either need a real listening server or could take up to 5 real
 * seconds to fail, which does not belong in a fast unit test. See the final report.
 * <p>
 * The onOpen()/onClose()/isOpen()/close() lifecycle logic, however, is pure state-tracking around
 * a javax.websocket.Session and is fully testable without any network: a mock is created with
 * Mockito's default (Objenesis-based) instantiation, which bypasses the real constructor
 * entirely, then CALLS_REAL_METHODS is used so the *actual* onOpen/onClose/isOpen/close bodies
 * run against that never-network-touched instance.
 */
public class WebSocketConnectionTest {

    private WebSocketConnection newConnectionWithoutNetworking() {
        // bypasses the real (network-performing) constructor via Objenesis; unstubbed calls run
        // the real method bodies.
        return mock(WebSocketConnection.class, CALLS_REAL_METHODS);
    }

    @Test
    public void isOpen_falseBeforeOnOpenIsEverCalled() {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        assertThat(conn.isOpen()).isFalse();
    }

    @Test
    public void onOpen_setsSession_andIsOpenDelegatesToSession() {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);

        conn.onOpen(session);

        assertThat(conn.isOpen()).isTrue();
        verify(session).setMaxIdleTimeout(0); // onOpen() disables the session's own idle timeout
    }

    @Test
    public void isOpen_falseWhenSessionReportsClosed_evenAfterOnOpen() {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(false);

        conn.onOpen(session);

        assertThat(conn.isOpen()).isFalse();
    }

    @Test
    public void onClose_clearsSession_soIsOpenBecomesFalse() {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        conn.onOpen(session);
        assertThat(conn.isOpen()).isTrue();

        CloseReason reason = new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done");
        conn.onClose(session, reason);

        assertThat(conn.isOpen()).isFalse();
    }

    @Test
    public void close_whenSessionOpen_closesTheSession() throws Exception {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        Session session = mock(Session.class);
        conn.onOpen(session);

        conn.close();

        verify(session).close();
    }

    @Test
    public void close_whenNeverOpened_doesNotThrow() {
        // userSession is null (never set via onOpen()) - close() guards on `userSession != null`
        // (WebSocketConnection.java lines 114-121), so this must be a silent no-op.
        WebSocketConnection conn = newConnectionWithoutNetworking();

        conn.close(); // must not throw
    }

    @Test
    public void close_calledTwice_secondCallIsANoOp() throws Exception {
        WebSocketConnection conn = newConnectionWithoutNetworking();
        Session session = mock(Session.class);
        conn.onOpen(session);

        conn.close();
        conn.close(); // userSession field itself is never nulled out by close() (only onClose() does that)

        verify(session, org.mockito.Mockito.times(2)).close();
    }
}
