//package mobi.meddle.wehe.combined;
//
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//
//import java.net.URI;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicBoolean;
//
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//import okhttp3.WebSocket;
//import okhttp3.WebSocketListener;
//import okio.ByteString;
//
///**
// * Client to connect to a server using a WebSocket (ws:// or wss://).
// * Two connections are made when using MLab servers: one is the Side Channel, which uses the regular
// * HTTPS connection; the other is through this WebSocket to authenticate this client. The connection
// * to the server through the WebSocket is opened at the beginning of the test and is maintained
// * throughout the test, but nothing is sent or received. The connection is closed when the test is
// * over. MLab automatically times out after 5 minutes, so a test must run within that period. A new
// * connection is made for each test.
// * This implementation uses OkHttp's WebSocket functionality for better compatibility with Android.
// */
//public class WebSocketConnection {
//    private WebSocket webSocket = null;
//    private final OkHttpClient client;
//    private final int id; // id of this instance
//    private final AtomicBoolean isConnected = new AtomicBoolean(false);
//    private Exception connectionError = null;
//
//    /**
//     * Constructor which makes a connection to the client.
//     *
//     * @param id        id of WebSocket
//     * @param serverURI the URI to connect to
//     * @throws Exception issues connecting to server
//     */
//    public WebSocketConnection(int id, URI serverURI) throws Exception {
//        this.id = id;
//
//        // Create OkHttpClient with appropriate timeout settings and no-cache (for retry)
//        this.client = new OkHttpClient.Builder()
//                .connectTimeout(10, TimeUnit.SECONDS)
//                .readTimeout(5, TimeUnit.MINUTES)  // MLab timeout is 5 minutes
//                .writeTimeout(10, TimeUnit.SECONDS)
//                .retryOnConnectionFailure(true)
//                .pingInterval(30, TimeUnit.SECONDS) // Keep connection alive with pings
//                .build();
//
//        // Create request for WebSocket connection
//        Request request = new Request.Builder()
//                .url(serverURI.toString())
//                .build();
//
//        // Use CountDownLatch for connection synchronization
//        final CountDownLatch connectionLatch = new CountDownLatch(1);
//
//        // Create WebSocketListener for handling connection events
//        WebSocketListener webSocketListener = new WebSocketListener() {
//            @Override
//            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
//                Log.i("WebSocket", "WebSocket " + id + " opened");
//                isConnected.set(true);
//                connectionLatch.countDown();
//            }
//
//            @Override
//            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
//                Log.d("WebSocket", "WebSocket " + id + " received message: " + text);
//            }
//
//            @Override
//            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
//                Log.d("WebSocket", "WebSocket " + id + " received bytes");
//            }
//
//            @Override
//            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
//                Log.i("WebSocket", "WebSocket " + id + " closing: " + code + " " + reason);
//                webSocket.close(1000, null);
//            }
//
//            @Override
//            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
//                Log.i("WebSocket", "WebSocket " + id + " closed: " + code + " " + reason);
//                isConnected.set(false);
//            }
//
//            @Override
//            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
//                Log.e("WebSocket", "WebSocket " + id + " failure", t);
//                connectionError = new Exception("WebSocket connection failure: " + t.getMessage(), t);
//                isConnected.set(false);
//                connectionLatch.countDown(); // Make sure to release the latch
//            }
//        };
//
//        // Initiate WebSocket connection
//        webSocket = client.newWebSocket(request, webSocketListener);
//
//        // Wait for connection to be established or failed
//        try {
//            boolean connected = connectionLatch.await(10, TimeUnit.SECONDS);
//
//            if (!connected || !isConnected.get()) {
//                close(); // Ensure we clean up
//                if (connectionError != null) {
//                    throw connectionError;
//                } else {
//                    throw new Exception("Could not connect to WebSocket - timed out after 10 seconds");
//                }
//            }
//        } catch (InterruptedException e) {
//            close(); // Ensure we clean up
//            throw new Exception("WebSocket connection interrupted", e);
//        }
//
//        Log.i("WebSocket", "WebSocket " + id + ": Connected to socket: " + serverURI.toString());
//    }
//
//    /**
//     * Get the WebSocket's ID number.
//     *
//     * @return id
//     */
//    public int getId() {
//        return id;
//    }
//
//    /**
//     * Close the WebSocket.
//     */
//    public void close() {
//        if (webSocket != null) {
//            boolean closed = webSocket.close(1000, "Closing normally");
//            if (!closed) {
//                Log.w("WebSocket", "WebSocket " + id + ": Could not initiate closing");
//            }
//            webSocket = null;
//        }
//        isConnected.set(false);
//    }
//
//    /**
//     * Determine if the connection to the server is open.
//     *
//     * @return true if connection is open; false otherwise
//     */
//    public boolean isOpen() {
//        return isConnected.get();
//    }
//}


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
