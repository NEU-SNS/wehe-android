package mobi.meddle.wehe.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import javax.inject.Inject

/**
 * WebSocket manager
 */
class WebSocketManager @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val webSockets = mutableListOf<WebSocket>()

    suspend fun connect(url: String, listener: WebSocketListener): WebSocket = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .build()

        val webSocket = okHttpClient.newWebSocket(request, listener)
        webSockets.add(webSocket)
        webSocket
    }

    fun closeAll() {
        webSockets.forEach { it.close(1000, "Normal closure") }
        webSockets.clear()
    }
}