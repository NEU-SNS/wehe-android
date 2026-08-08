package mobi.meddle.wehe.data.repository

import android.util.Log
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Timer
import java.util.TimerTask
import javax.inject.Singleton
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers

/**
 * Repository to send requests to the server.
 */
@Singleton
class ServerRepository
{
    var hostnameVerifier: HostnameVerifier? = null
    var sslSocketFactory: SSLSocketFactory? = null
    private val timers = ArrayList<Timer>()
    private var servers = ArrayList<String?>() //servers to run the replays to
    var isIPv6 = false

    /**
     * HTTP status of the most recent request, or [NO_RESPONSE_CODE] when the request never got a
     * response at all (DNS failure, timeout, refused connection). [sendRequest] collapses every
     * kind of failure into a null return, so this is what lets a caller tell "the server turned us
     * away" apart from "we could not reach the server" -- notably a 429 from the M-Lab locate
     * service. It holds whichever request finished last, so read it immediately after the
     * [sendRequest] call whose outcome you care about.
     */
    @Volatile
    var lastResponseCode = NO_RESPONSE_CODE
        private set

    /**
     * Send a GET or POST request to the server.
     *
     * @param url    URL to the server
     * @param method either GET or POST
     * @param main   true if request is to main server; false otherwise
     * @param data   data to send to server in a GET request, null if a POST request or if no
     * data to send to server
     * @param pairs  data to send to server in a POST request, null if a GET request
     * @return a response from the server in the form of a JSONObject, null if error
     */
    fun sendRequest(
        url: String, method: String, main: Boolean,
        data: ArrayList<String>?, pairs: HashMap<String, String?>?
    ): JSONObject? {
        val json = arrayOf<JSONObject?>(null)
        val conn = arrayOfNulls<HttpsURLConnection>(1)
        val readyToReturn = booleanArrayOf(false)
        lastResponseCode = NO_RESPONSE_CODE
        val serverComm = Thread {
            var urlString = url

            // Add client_name parameter for MLab servers
            if (url.contains("mlab") || url.contains("measurementLab") || url.contains("locate-dot-mlab-staging.appspot.com") || url.contains("locate.measurementlab.net")) {
                val separator = if (urlString.contains("?")) "&" else "?"
                urlString += "${separator}client_name=wehe-android"
            }

            if (method.equals("GET", ignoreCase = true)) {
                if (data != null) {
                    val dataURL = urlEncoder(data)
                    urlString += if (urlString.contains("?")) "&$dataURL" else "?$dataURL"
                }
                Log.d("Send GET Request", urlString)

                for (i in 0..2) {
                    try {
                        //connect to server
                        val u = URL(urlString)
                        //send data to server
                        conn[0] = u.openConnection() as HttpsURLConnection
                        if (main && hostnameVerifier != null && sslSocketFactory != null) {
                            conn[0]!!.hostnameVerifier = hostnameVerifier
                            conn[0]!!.sslSocketFactory = sslSocketFactory
                        }
                        conn[0]!!.connectTimeout = 8000
                        conn[0]!!.readTimeout = 8000

                        //check the status before touching inputStream: Android throws a
                        //misleading FileNotFoundException from getInputStream() for every code
                        //>= 400, so read the error stream instead to find out what actually
                        //went wrong (429 rate limited, 503 down, ...)
                        val responseCode = conn[0]!!.responseCode
                        lastResponseCode = responseCode
                        if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                            val errorBody = try {
                                conn[0]!!.errorStream?.use { errStream ->
                                    BufferedReader(InputStreamReader(errStream)).readText()
                                }.orEmpty()
                            } catch (e: IOException) {
                                Log.w("Send Request", "Could not read error stream", e)
                                ""
                            }
                            //Retry-After is set when we are being rate limited; it tells us how
                            //many seconds to wait, which is far longer than this request's own
                            //8 second budget, so there is nothing useful to do but report it
                            val retryAfter = conn[0]!!.getHeaderField("Retry-After")
                            Log.e(
                                "Send Request",
                                "sendRequest GET failed: HTTP $responseCode for $urlString" +
                                        (if (retryAfter != null) " (Retry-After: $retryAfter)" else "") +
                                        " body: $errorBody"
                            )
                            conn[0]!!.disconnect()
                            //a 4xx is our mistake, not a blip: the next two attempts would get
                            //the same answer and, if this is a 429, burn more of our quota
                            if (responseCode < HttpURLConnection.HTTP_INTERNAL_ERROR) {
                                break
                            }
                            continue
                        }

                        val `in` = BufferedReader(
                            InputStreamReader(
                                conn[0]!!.inputStream
                            )
                        )
                        val buffer = StringBuilder()
                        var input: String?

                        // parse BufferReader rd to StringBuilder res
                        while ((`in`.readLine()
                                .also { input = it }) != null
                        ) { //read response from server
                            buffer.append(input)
                        }

                        `in`.close()
                        conn[0]!!.disconnect()
                        json[0] = JSONObject(buffer.toString()) // parse String to json file
                        Log.d("Send GET Request", json[0].toString())
                        break
                    } catch (e: IOException) {
                        Log.e("Send Request", "sendRequest GET failed", e)
                    } catch (e: JSONException) {
                        Log.e("Send Request", "JSON Parse failed", e)
                    }
                }
            } else if (method.equals("POST", ignoreCase = true)) {
                Log.d("Send POST Request", urlString)

                try {
                    //connect to server
                    val u = URL(urlString)
                    conn[0] = u.openConnection() as HttpsURLConnection
                    conn[0]!!.hostnameVerifier = hostnameVerifier
                    conn[0]!!.sslSocketFactory = sslSocketFactory
                    conn[0]!!.connectTimeout = 5000
                    conn[0]!!.readTimeout = 5000
                    conn[0]!!.requestMethod = "POST"
                    conn[0]!!.doInput = true
                    conn[0]!!.doOutput = true

                    val os = conn[0]!!.outputStream
                    val writer = BufferedWriter(
                        OutputStreamWriter(os, StandardCharsets.UTF_8)
                    )
                    writer.write(pairs?.let { paramsToPostData(it) }) //send data to server

                    writer.flush()
                    writer.close()
                    os.close()

                    //same trap as the GET path: getInputStream() reports every code >= 400 as a
                    //FileNotFoundException, so read the status and error stream first
                    val responseCode = conn[0]!!.responseCode
                    lastResponseCode = responseCode
                    if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                        val errorBody = try {
                            conn[0]!!.errorStream?.use { errStream ->
                                BufferedReader(InputStreamReader(errStream)).readText()
                            }.orEmpty()
                        } catch (e: IOException) {
                            Log.w("Send Request", "Could not read error stream", e)
                            ""
                        }
                        Log.e(
                            "Send Request",
                            "sendRequest POST failed: HTTP $responseCode for $urlString" +
                                    " body: $errorBody"
                        )
                        conn[0]!!.disconnect()
                        json[0] = null
                        readyToReturn[0] = true
                        return@Thread
                    }

                    val `in` = BufferedReader(
                        InputStreamReader(
                            conn[0]!!.inputStream
                        )
                    )
                    val buffer = StringBuilder()
                    var input: String?

                    // parse BufferReader rd to StringBuilder res
                    while ((`in`.readLine()
                            .also { input = it }) != null
                    ) { //read response from server
                        buffer.append(input)
                    }
                    `in`.close()
                    conn[0]!!.disconnect()
                    json[0] = JSONObject(buffer.toString()) // parse String to json file.
                } catch (e: JSONException) {
                    Log.e("Send Request", "convert string to json failed", e)
                    json[0] = null
                } catch (e: IOException) {
                    Log.e("Send Request", "sendRequest POST failed", e)
                    json[0] = null
                }
            }
            readyToReturn[0] = true
        }
        serverComm.start()
        val t = Timer()
        timers.add(t)
        //timeout server after 8 sec; server timeout field times out only when nothing is sent;
        //if stuff sends too slowly, it could take forever, so this external timer prevents that
        t.schedule(object : TimerTask() {
            override fun run() { //set timer to timeout the thread if max time has been reached for replay
                if (conn[0] != null) {
                    conn[0]!!.disconnect()
                }
                readyToReturn[0] = true
            }
        }, 8000)
        //wait until ready to move on (i.e. when result retrieved or timeout), as threads don't
        //block execution
        while (!readyToReturn[0]) {
            try {
                Thread.sleep(500)
            } catch (e: InterruptedException) {
                Log.w("Send Request", "Interrupted", e)
            }
        }
        return json[0]
    }

    /**
     * Helper method to encode URL parameters
     * @param map List of strings to encode
     * @return Encoded URL string
     */
    private fun urlEncoder(map: ArrayList<String>): String {
        val data = StringBuilder()
        for (s in map) {
            if (data.isNotEmpty()) {
                data.append("&")
            }
            data.append(s)
        }
        return data.toString()
    }

    /**
     * Encodes data into a string to send POST request to server.
     *
     * @param params data to convert into string to send to server
     * @return an encoded string to send to the server
     */
    private fun paramsToPostData(params: HashMap<String, String?>): String {
        val result = StringBuilder()
        var first = true
        for ((key, value) in params) {
            if (first) {
                first = false
            } else {
                result.append("&")
            }

            try {
                result.append(java.net.URLEncoder.encode(key, "UTF-8"))
                result.append("=")
                result.append(java.net.URLEncoder.encode(value, "UTF-8"))
            } catch (e: UnsupportedEncodingException) {
                Log.e("paramsToPostData", "Encoding error", e)
            }
        }
        return result.toString()
    }

    /**
     * Does a DNS lookup on a hostname.
     *
     * @param server the hostname to be resolved
     * @return the IP of the host; empty string if there is an error doing so.
     */
    suspend fun getServerIP(server: String): String = withContext(Dispatchers.IO) {
        var host = server
        Log.d("getServerIP", "Server hostname: $host")
        var address: InetAddress?
        for (i in 0..4) { //5 attempts to lookup the IP
            try {
                //DNS lookup; hostAddress is null when the address cannot be resolved
                host = InetAddress.getByName(host).hostAddress
                    ?: throw UnknownHostException("No IP address for $host")
                address = InetAddress.getByName(host)
                if (address is Inet4Address) {
                    return@withContext host
                }
                if (address is Inet6Address) {
                    return@withContext "[$host]"
                }
            } catch (e: UnknownHostException) {
                if (i == 4) {
                    Log.e("getServerIP", "Failed to get IP of server", e)
                } else {
                    Log.w("getServerIP", "Failed to get IP of server, trying again")
                }
                try {
                    Thread.sleep(1000)
                } catch (ex: InterruptedException) {
                    Log.w("getServerIP", "Sleep interrupted", ex)
                }
            }
        }
        return@withContext ""
    }

    /**
     * Get IP of user's device.
     *
     * @param port port to run replays
     * @return user's public IP or -1 if cannot connect to the server
     */
    suspend fun getPublicIP(port: String): String = withContext(Dispatchers.IO) {
        var publicIP = "127.0.0.1"

        if (servers.size != 0 && servers[0] != "127.0.0.1") {
            val url = "http://" + servers[0] + ":" + port + "/WHATSMYIPMAN"
            Log.d("getPublicIP", "url: $url")

            var numFails = 0
            while (publicIP == "127.0.0.1") {
                try {
                    val u = URL(url)
                    //go to server
                    val conn = u.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 5000
                    val `in` = BufferedReader(
                        InputStreamReader(
                            conn.inputStream
                        )
                    )
                    val buffer = StringBuilder()
                    var input: String?

                    while ((`in`.readLine().also { input = it }) != null) { //read IP address
                        buffer.append(input)
                    }
                    `in`.close()
                    conn.disconnect()
                    publicIP = buffer.toString()
                    val address = InetAddress.getByName(publicIP)
                    if (address !is Inet4Address && address !is Inet6Address) {
                        Log.e("getPublicIP", "wrong format of public IP: $publicIP")
                        throw UnknownHostException()
                    }
                    isIPv6 = address is Inet6Address
                    if (publicIP == "") {
                        publicIP = "-1"
                    }
                    Log.d("getPublicIP", "public IP: $publicIP")
                } catch (e: UnknownHostException) {
                    Log.w("getPublicIP", "failed to get public IP!", e)
                    publicIP = "127.0.0.1"
                    break
                } catch (e: IOException) {
                    Log.w("getPublicIP", "Can't connect to server")
                    try {
                        Thread.sleep(1000)
                    } catch (e1: InterruptedException) {
                        Log.w("getPublicIP", "Sleep interrupted", e1)
                    }
                    if (++numFails == 5) { //Cannot connect to server after 5 tries
                        Log.w("getPublicIP", "Returning -1", e)
                        publicIP = "-1"
                        break
                    }
                }
            }
        } else {
            Log.w("getPublicIP", "server ip is not available: " + servers[0])
        }
        return@withContext publicIP
    }

    /**
     * Set the servers to run the replays to
     *
     * @param servers list of servers to run the replays to
     */
    fun setServers(servers : ArrayList<String?>) {
        this.servers = servers
    }

    /**
     * Clean up any active timers
     */
    fun cleanup() {
        for (timer in timers) {
            timer.cancel()
        }
        timers.clear()
    }

    companion object {
        /** [lastResponseCode] when the request failed before any HTTP status came back. */
        const val NO_RESPONSE_CODE = -1

        /** HTTP 429; [HttpURLConnection] has no constant for it. */
        const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
//
//import android.util.Log
//import kotlinx.coroutines.CancellationException
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.flow
//import kotlinx.coroutines.withContext
//import kotlinx.coroutines.withTimeout
//import org.json.JSONException
//import org.json.JSONObject
//import java.io.BufferedReader
//import java.io.BufferedWriter
//import java.io.IOException
//import java.io.InputStreamReader
//import java.io.OutputStreamWriter
//import java.net.HttpURLConnection
//import java.net.Inet4Address
//import java.net.Inet6Address
//import java.net.InetAddress
//import java.net.URL
//import java.net.UnknownHostException
//import java.nio.charset.StandardCharsets
//import javax.inject.Inject
//import javax.inject.Singleton
//import javax.net.ssl.HostnameVerifier
//import javax.net.ssl.HttpsURLConnection
//import javax.net.ssl.SSLSocketFactory
//
///**
// * Repository to handle network communication with the server.
// */
//@Singleton
//class ServerRepository @Inject constructor(
//    private val logger: Logger
//) {
//    var hostnameVerifier: HostnameVerifier? = null
//    var sslSocketFactory: SSLSocketFactory? = null
//    private var servers = ArrayList<String?>()
//    var isIPv6 = false
//
//    /**
//     * Send a GET request to the server asynchronously.
//     *
//     * @param url URL to the server
//     * @param main true if request is to main server; false otherwise
//     * @param data data to send to server in a GET request
//     * @return Flow emitting Result with response from the server
//     */
//    fun sendGetRequest(
//        url: String,
//        main: Boolean,
//        data: List<String>? = null
//    ): Flow<Result<JSONObject>> = flow {
//        try {
//            var urlString = url
//            if (data != null) {
//                val dataURL = urlEncoder(data)
//                urlString += "?$dataURL"
//            }
//
//            logger.debug("Send GET Request", urlString)
//
//            var result: JSONObject? = null
//            var lastException: Exception? = null
//
//            // Multiple attempts logic
//            for (i in 0..2) {
//                try {
//                    withTimeout(8000) {
//                        result = executeGetRequest(urlString, main)
//                    }
//                    break
//                } catch (e: Exception) {
//                    lastException = e
//                    logger.error("Send Request", "GET attempt $i failed", e)
//                    // Allow retry unless it was a cancellation
//                    if (e is CancellationException) throw e
//                }
//            }
//
//            if (result != null) {
//                emit(Result.success(result!!))
//            } else {
//                emit(Result.failure(lastException ?: IOException("Failed to execute GET request")))
//            }
//        } catch (e: Exception) {
//            emit(Result.failure(e))
//        }
//    }
//
//    private suspend fun executeGetRequest(urlString: String, main: Boolean): JSONObject = withContext(Dispatchers.IO) {
//        val url = URL(urlString)
//        val conn = url.openConnection() as HttpsURLConnection
//
//        try {
//            if (main && hostnameVerifier != null && sslSocketFactory != null) {
//                conn.hostnameVerifier = hostnameVerifier
//                conn.sslSocketFactory = sslSocketFactory
//            }
//
//            conn.connectTimeout = 8000
//            conn.readTimeout = 8000
//
//            val inputStream = conn.inputStream
//            val reader = BufferedReader(InputStreamReader(inputStream))
//            val buffer = StringBuilder()
//
//            var line: String?
//            while (reader.readLine().also { line = it } != null) {
//                buffer.append(line)
//            }
//
//            reader.close()
//            val jsonResponse = JSONObject(buffer.toString())
//            logger.debug("GET Response", jsonResponse.toString())
//            return@withContext jsonResponse
//        } finally {
//            conn.disconnect()
//        }
//    }
//
//    /**
//     * Send a POST request to the server asynchronously.
//     *
//     * @param url URL to the server
//     * @param data data to send to server in a POST request
//     * @return Flow emitting Result with response from the server
//     */
//    fun sendPostRequest(
//        url: String,
//        data: Map<String, String?>
//    ): Flow<Result<JSONObject>> = flow {
//        try {
//            logger.debug("Send POST Request", url)
//
//            withTimeout(8000) {
//                val result = executePostRequest(url, data)
//                emit(Result.success(result))
//            }
//        } catch (e: Exception) {
//            logger.error("Send Request", "POST failed", e)
//            emit(Result.failure(e))
//        }
//    }
//
//    private suspend fun executePostRequest(urlString: String, data: Map<String, String?>): JSONObject = withContext(Dispatchers.IO) {
//        val url = URL(urlString)
//        val conn = url.openConnection() as HttpsURLConnection
//
//        try {
//            conn.hostnameVerifier = hostnameVerifier
//            conn.sslSocketFactory = sslSocketFactory
//            conn.connectTimeout = 5000
//            conn.readTimeout = 5000
//            conn.requestMethod = "POST"
//            conn.doInput = true
//            conn.doOutput = true
//
//            val os = conn.outputStream
//            val writer = BufferedWriter(OutputStreamWriter(os, StandardCharsets.UTF_8))
//            writer.write(paramsToPostData(data))
//            writer.flush()
//            writer.close()
//            os.close()
//
//            val reader = BufferedReader(InputStreamReader(conn.inputStream))
//            val buffer = StringBuilder()
//
//            var line: String?
//            while (reader.readLine().also { line = it } != null) {
//                buffer.append(line)
//            }
//
//            reader.close()
//            return@withContext JSONObject(buffer.toString())
//        } finally {
//            conn.disconnect()
//        }
//    }
//
//    /**
//     * Does a DNS lookup on a hostname.
//     *
//     * @param server the hostname to be resolved
//     * @return Result containing the IP of the host; error if resolution fails
//     */
//    suspend fun getServerIP(hostname: String): Result<String> = withContext(Dispatchers.IO) {
//        var server = hostname
//        logger.debug("getServerIP", "Server hostname: $server")
//
//        for (i in 0..4) {
//            try {
//                val address = InetAddress.getByName(server)
//                val ip = address.hostAddress
//
//                return@withContext when {
//                    address is Inet4Address -> Result.success(ip)
//                    address is Inet6Address -> Result.success("[$ip]")
//                    else -> Result.failure(UnknownHostException("Unknown address type"))
//                }
//            } catch (e: UnknownHostException) {
//                if (i == 4) {
//                    logger.error("getServerIP", "Failed to get IP of server", e)
//                    return@withContext Result.failure(e)
//                } else {
//                    logger.warning("getServerIP", "Failed to get IP of server, trying again")
//                    kotlinx.coroutines.delay(1000)
//                }
//            }
//        }
//
//        Result.failure(UnknownHostException("Failed to resolve hostname after multiple attempts"))
//    }
//
//    /**
//     * Get IP of user's device.
//     *
//     * @param port port to run replays
//     * @return Result containing user's public IP or error if cannot connect to the server
//     */
//    suspend fun getPublicIP(port: String): Result<String> = withContext(Dispatchers.IO) {
//        if (servers.isEmpty() || servers[0] == "127.0.0.1") {
//            logger.warning("getPublicIP", "server ip is not available: ${servers.firstOrNull()}")
//            return@withContext Result.success("127.0.0.1")
//        }
//
//        val url = "http://${servers[0]}:$port/WHATSMYIPMAN"
//        logger.debug("getPublicIP", "url: $url")
//
//        for (attempt in 1..5) {
//            try {
//                val conn = URL(url).openConnection() as HttpURLConnection
//                conn.connectTimeout = 3000
//                conn.readTimeout = 5000
//
//                try {
//                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
//                    val buffer = StringBuilder()
//                    var line: String?
//
//                    while (reader.readLine().also { line = it } != null) {
//                        buffer.append(line)
//                    }
//
//                    reader.close()
//
//                    val publicIP = buffer.toString()
//                    if (publicIP.isEmpty()) {
//                        return@withContext Result.failure(IOException("Empty IP received"))
//                    }
//
//                    val address = InetAddress.getByName(publicIP)
//                    if (address !is Inet4Address && address !is Inet6Address) {
//                        logger.error("getPublicIP", "wrong format of public IP: $publicIP")
//                        return@withContext Result.failure(UnknownHostException("Invalid IP format"))
//                    }
//
//                    isIPv6 = address is Inet6Address
//                    logger.debug("getPublicIP", "public IP: $publicIP")
//                    return@withContext Result.success(publicIP)
//
//                } finally {
//                    conn.disconnect()
//                }
//
//            } catch (e: IOException) {
//                logger.warning("getPublicIP", "Can't connect to server (attempt $attempt)")
//                if (attempt == 5) {
//                    logger.warning("getPublicIP", "Failed after 5 attempts", e)
//                    return@withContext Result.failure(e)
//                }
//                kotlinx.coroutines.delay(1000)
//            } catch (e: UnknownHostException) {
//                logger.error("getPublicIP", "Failed to resolve host", e)
//                return@withContext Result.failure(e)
//            }
//        }
//
//        Result.failure(IOException("Failed to get public IP after multiple attempts"))
//    }
//
//    /**
//     * Set the servers to run the replays to
//     *
//     * @param servers list of servers to run the replays to
//     */
//    fun setServers(servers: ArrayList<String?>) {
//        this.servers = servers
//    }
//
//    /**
//     * Helper method to encode URL parameters
//     * @param data List of strings to encode
//     * @return Encoded URL string
//     */
//    private fun urlEncoder(data: List<String>): String {
//        return data.joinToString("&")
//    }
//
//    /**
//     * Encodes data into a string to send POST request to server.
//     *
//     * @param params data to convert into string to send to server
//     * @return an encoded string to send to the server
//     */
//    private fun paramsToPostData(params: Map<String, String?>): String {
//        return params.entries.joinToString("&") { (key, value) ->
//            "${java.net.URLEncoder.encode(key, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}"
//        }
//    }
//}