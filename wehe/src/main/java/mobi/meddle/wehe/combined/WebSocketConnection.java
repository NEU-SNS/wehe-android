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
 * TODO: get better WebSocket library (this library doesn't work on android < 8)?
 */
@ClientEndpoint
public class WebSocketConnection {
    private Session userSession = null;
    private final int id; //id of this instance

    /**
     * Constructor which makes a connection to the client.
     *
     * @param id        id of WebSocket
     * @param serverURI the URI to connect to
     * @throws DeploymentException issues connecting to server - on older Android APIs, an error may
     *                             be thrown because of a failed SSL handshake. For those APIs, an
     *                             error message is displayed to the user telling them to use a
     *                             different server (implemented in ReplayActivity)
     */
    public WebSocketConnection(int id, URI serverURI) throws DeploymentException, InterruptedException {
        this.id = id;
        WebSocketContainer container = ContainerProvider.getWebSocketContainer(); //magic!
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final Boolean[] success = {null};
        //hacky way to timeout WebSocket request for 5 seconds
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    container.connectToServer(WebSocketConnection.this, serverURI);
                    success[0] = true;
                } catch (DeploymentException | IOException e) {
                    Log.e("WebSocket", "WebSocket " + id + ": Failed connecting to WebSocket", e);
                    success[0] = false;
                }
            }
        });
        //check every 500 ms to see if successfully connected to ws
        for (int i = 0; i < 10; i++) {
            if (success[0] != null) {
                break;
            }
            Thread.sleep(500);
        }
        if (success[0] == null || !success[0]) {
            throw new DeploymentException("Could not connect to WebSocket");
        }
        Log.i("WebSocket", "WebSocket " + id + ": Connected to socket: " + serverURI.toString());
    }

    /**
     * Get the WebSocket's ID number.
     *
     * @return id
     */
    public int getId() {
        return id;
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
        Log.i("WebSocket", "WebSocket " + id + " opened");
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
        Log.i("WebSocket", "WebSocket " + id + " closed");
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
            Log.e("WebSocket", "Socket " + id + " failed to close", e);
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
