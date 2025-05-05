//package mobi.meddle.wehe.combined
//
//import android.util.Log
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.Response
//import okhttp3.WebSocket
//import okhttp3.WebSocketListener
//import okio.ByteString
//
//import java.io.IOException
//import java.net.URI
//import java.util.concurrent.CountDownLatch
//import java.util.concurrent.TimeUnit
//
///**
// * Client to connect to a server using a WebSocket (ws:// or wss://) implemented with OkHttp.
// * Two connections are made when using MLab servers: one is the Side Channel, which uses the regular
// * HTTPS connection; the other is through this WebSocket to authenticate this client. The connection
// * to the server through the WebSocket is opened at the beginning of the test and is maintained
// * throughout the test, but nothing is sent or received. The connection is closed when the test is
// * over. MLab automatically times out after 5 minutes, so a test must run within that period. A new
// * connection is made for each test.
// */
//class WebSocketConnection(
//    /**
//     * Get the WebSocket's ID number.
//     *
//     * @return id
//     */
//    val id: Int, // id of this instance
//    serverURI: URI
//) {
//    private var webSocket: WebSocket? = null
//
//    /**
//     * Determine if the connection to the server is open.
//     *
//     * @return true if connection is open; false otherwise
//     */
//    var isOpen: Boolean = false
//
//    // Create OkHttpClient with appropriate configurations
//    private val client: OkHttpClient = OkHttpClient.Builder()
//        .connectTimeout(5, TimeUnit.SECONDS)
//        .readTimeout(
//            0,
//            TimeUnit.MILLISECONDS
//        ) // No timeout for read operations
//        .writeTimeout(5, TimeUnit.SECONDS)
//        .build()
//
//    /**
//     * Constructor which makes a connection to the client.
//     *
//     * @param id        id of WebSocket
//     * @param serverURI the URI to connect to
//     * @throws IOException if connection fails within timeout period
//     * @throws InterruptedException if the thread is interrupted while waiting
//     */
//    init {
//        // Create websocket request
//        val request: Request = Request.Builder()
//            .url(serverURI.toString())
//            .build()
//
//        val connectionLatch = CountDownLatch(1)
//        val connectionException = arrayOfNulls<IOException>(1)
//
//        // Connect to the WebSocket with a listener to handle events
//        webSocket = client.newWebSocket(request, object : WebSocketListener() {
//            override fun onOpen(webSocket: WebSocket, response: Response) {
//                isOpen = true
//                Log.i(TAG, "WebSocket $id opened")
//                connectionLatch.countDown()
//            }
//
//            override fun onMessage(webSocket: WebSocket, text: String) {
//                Log.d(
//                    TAG,
//                    "WebSocket $id received message: $text"
//                )
//            }
//
//            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
//                Log.d(
//                    TAG,
//                    "WebSocket $id received bytes message"
//                )
//            }
//
//            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
//                Log.d(
//                    TAG,
//                    "WebSocket $id closing: $code $reason"
//                )
//            }
//
//            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
//                isOpen = false
//                Log.i(
//                    TAG,
//                    "WebSocket $id closed: $code $reason"
//                )
//            }
//
//            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
//                isOpen = false
//                Log.e(TAG, "WebSocket $id failed", t)
//                connectionException[0] = IOException("WebSocket connection failed: " + t.message, t)
//                connectionLatch.countDown()
//            }
//        })
//
//        // Wait for connection to establish or fail with timeout
//        val connected = connectionLatch.await(5, TimeUnit.SECONDS)
//
//        if (!connected || connectionException[0] != null) {
//            // Clean up if connection wasn't established
//            webSocket?.close(1000, "Connection timeout")
//
//            if (connectionException[0] != null) {
//                throw connectionException[0]!!
//            } else {
//                throw IOException("Could not connect to WebSocket: timeout after 5 seconds")
//            }
//        }
//
//        Log.i(
//            TAG,
//            "WebSocket $id: Connected to socket: $serverURI"
//        )
//    }
//
//    /**
//     * Close the WebSocket.
//     */
//    fun close() {
//        if (isOpen) {
//            // Normal closure status code is 1000
//            val closed = webSocket?.close(1000, "Closing connection")
//            if (!closed!!) {
//                Log.e(TAG, "Socket $id failed to close")
//            }
//            // Force shutdown of connection pools to free resources
//            client.dispatcher.executorService.shutdown()
//        }
//    }
//
//    companion object {
//        private const val TAG = "WebSocket"
//    }
//}
