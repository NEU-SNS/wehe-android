package mobi.meddle.wehe.combined;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

/**
 * Client to connect to a server using a WebSocket (ws:// or wss://).
 * Two connections are made when using MLab servers: one is the Side Channel, which uses the regular
 * HTTPS connection; the other is through this WebSocket to authenticate this client. The connection
 * to the server through the WebSocket is opened at the beginning of the test and is maintained
 * throughout the test, but nothing is sent or received. The connection is closed when the test is
 * over. MLab automatically times out after 5 minutes, so a test must run within that period. A new
 * connection is made for each test.
 * The Tyrus library is used for the WebSocket implementation for this client.
 * TODO: get better WebSocket library?
 */
@ClientEndpoint
public class WebSocketConnection {
    private Session userSession = null;

    /**
     * Constructor which makes a connection to the client.
     *
     * @param serverURI the URI to connect to
     * @throws DeploymentException issues connecting to server - on older Android APIs, an error may
     *                             be thrown because of a failed SSL handshake. For those APIs, an
     *                             error message is displayed to the user telling them to use a
     *                             different server (implemented in ReplayActivity)
     */
    public WebSocketConnection(URI serverURI) throws DeploymentException, InterruptedException {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer(); //magic!
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final boolean[] success = {false};
        //hacky way to timeout websocket request for 2 seconds
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    container.connectToServer(WebSocketConnection.this, serverURI);
                    success[0] = true;
                } catch (DeploymentException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
        Thread.sleep(5000);
        if (!success[0]) {
            throw new DeploymentException("Could connect to WebSocket");
        }
        Log.i("WebSocket", "Connected to socket: " + serverURI.toString());
    }

    /**
     * Callback that is called when the WebSocket is opened. Android Studio might say this method is
     * unused, but it is, in fact, used by Tyrus.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(@NonNull Session userSession) {
        this.userSession = userSession;
        userSession.setMaxIdleTimeout(0); //no timeout caused by client
        Log.i("WebSocket", "WebSocket opened");
    }

    /**
     * Callback that is called when the WebSocket is closed. Android Studio might say this method is
     * unused, but it is, in fact, used by Tyrus.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason      the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, @NonNull CloseReason reason) {
        this.userSession = null;
        Log.i("WebSocket", "WebSocket closed");
        Log.d("WebSocket", "Close code: " + reason.getCloseCode() + " " + reason.getReasonPhrase());
    }

    /**
     * Close the WebSocket.
     */
    public void close() {
        try {
            if (userSession != null) {
                this.userSession.close();
            }
        } catch (IOException e) {
            Log.e("WebSocket", "Socket failed to close", e);
        }
    }

    /**
     * Determine if the connection to the server is open.
     *
     * @return true if connection is open; false otherwise
     */
    public boolean isOpen() {
        return userSession != null && userSession.isOpen();
    }
}
