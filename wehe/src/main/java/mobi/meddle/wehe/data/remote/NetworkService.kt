package mobi.meddle.wehe.data.remote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Network service to handle all network operations
 */
@Singleton
class NetworkService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val certificateManager: CertificateManager
) {
    /**
     * Perform a GET request
     */
    suspend fun performGetRequest(url: String, params: Map<String, String>? = null, useSslSocket: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val finalUrl = if (params != null) {
            val queryParams = params.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$url?$queryParams"
        } else {
            url
        }

        val request = Request.Builder()
            .url(finalUrl)
            .get()
            .build()

        val client = if (useSslSocket) {
            okHttpClient.newBuilder()
                .sslSocketFactory(certificateManager.getSSLSocketFactory(), certificateManager.getTrustManager())
                .hostnameVerifier { _, _ -> true }
                .build()
        } else {
            okHttpClient
        }

        return@withContext suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val jsonString = response.body?.string() ?: "{}"
                        val jsonObject = JSONObject(jsonString)
                        continuation.resume(jsonObject)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    /**
     * Perform a POST request
     */
    suspend fun performPostRequest(url: String, params: Map<String, String>, useSslSocket: Boolean = false): JSONObject = withContext(Dispatchers.IO) {
        val formBody = FormBody.Builder().apply {
            params.forEach { (key, value) ->
                add(key, value)
            }
        }.build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        val client = if (useSslSocket) {
            okHttpClient.newBuilder()
                .sslSocketFactory(certificateManager.getSSLSocketFactory(), certificateManager.getTrustManager())
                .hostnameVerifier { _, _ -> true }
                .build()
        } else {
            okHttpClient
        }

        return@withContext suspendCoroutine { continuation ->
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val jsonString = response.body?.string() ?: "{}"
                        val jsonObject = JSONObject(jsonString)
                        continuation.resume(jsonObject)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    /**
     * Resolve hostname to IP address
     */
    suspend fun resolveHostToIp(hostname: String): String = withContext(Dispatchers.IO) {
        var result = ""
        for (i in 0 until 5) {
            try {
                val address = InetAddress.getByName(hostname)
                result = when (address) {
                    is Inet4Address -> address.hostAddress ?: ""
                    is Inet6Address -> "[${address.hostAddress}]"
                    else -> ""
                }
                if (result.isNotEmpty()) break
            } catch (e: UnknownHostException) {
                if (i == 4) throw e
                kotlinx.coroutines.delay(1000)
            }
        }
        result
    }

    /**
     * Get public IP address
     */
    suspend fun getPublicIp(serverIp: String, port: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        var publicIp = "127.0.0.1"
        var isIpv6 = false

        if (serverIp != "127.0.0.1") {
            val url = "http://$serverIp:$port/WHATSMYIPMAN"

            var numFails = 0
            while (publicIp == "127.0.0.1" && numFails < 5) {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .build()

                    val response = okHttpClient.newCall(request).execute()
                    publicIp = response.body?.string()?.trim() ?: ""

                    val address = InetAddress.getByName(publicIp)
                    isIpv6 = address is Inet6Address

                    if (publicIp.isEmpty()) {
                        publicIp = "-1"
                    }
                } catch (e: IOException) {
                    numFails++
                    kotlinx.coroutines.delay(1000)
                    if (numFails == 5) {
                        publicIp = "-1"
                    }
                } catch (e: UnknownHostException) {
                    publicIp = "-1"
                    break
                }
            }
        }

        Pair(publicIp, isIpv6)
    }
}