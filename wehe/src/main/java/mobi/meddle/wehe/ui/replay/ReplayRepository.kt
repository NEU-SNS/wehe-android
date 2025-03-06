package mobi.meddle.wehe.ui.replay

import android.content.Context
import android.content.SharedPreferences
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import mobi.meddle.wehe.BuildConfig
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.WebSocketConnection
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.bean.RequestSet
import mobi.meddle.wehe.util.Config
import mobi.meddle.wehe.util.UtilsManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory

class ReplayRepository @Inject constructor(private val context: Context) {
    // Server and certificate management
    var hostnameVerifier: HostnameVerifier? = null
    var sslSocketFactory: SSLSocketFactory? = null
    var serverRepository: ServerRepository = ServerRepository()
    val servers = ArrayList<String?>()
    val wsConns = ArrayList<WebSocketConnection>()
    private var metadataServer: String? = null
    private val analyzerServerUrls = ArrayList<String>()
    private var mlabServerUsed = false
    private val numMLab = ArrayList<Int>()

    /**
     * Gets IPs of server and metadata server
     *
     * @param server The hostname of the server to connect to
     * @param metadataServer The hostname of the metadata server to connect to
     * @return Result object with success status and error message if failed
     */
    suspend fun setupServersAndCertificates(
        server: String,
        metadataServer: String?,
        numTests: Int,
        isTomography: Boolean
    ): Result<Boolean> {
        var serverName = server

        // Version code 40 = version name 3.46
        if (BuildConfig.VERSION_CODE >= 40 && serverName == "wehe3.meddle.mobi") {
            serverName = "wehe4.meddle.mobi"
        }

        servers.clear()
        // Extreme hack to temporarily get around French DNS look up issue
        if (serverName == "wehe4.meddle.mobi") {
            servers.add("10.0.0.0")
            Log.d("Serverhack", "hacking wehe4")
        } else {
            servers.add(serverRepository.getServerIP(serverName))
            if (servers[servers.size - 1] == "") {
                return Result.failure(Exception(context.getString(R.string.error_unknown_host)))
            }
        }

        // A hacky way to check server IP version
        var serverIPisV6 = false
        if (servers[0]!!.contains(":")) {
            serverIPisV6 = true
        }
        Log.d("ServerIPVersion", servers[0] + (if (serverIPisV6) "IPV6" else "IPV4"))

        // Connect to an MLab server if needed
        mlabServerUsed = false
        if (servers[0] == "10.0.0.0" || serverIPisV6) {
            val result = connectToMLabServers(numTests, isTomography)
            if (!result.isSuccess) {
                return result
            }
        }

        for (i in 0 until numTests) {
            if (servers[i] == "") {
                if (wsConns.isNotEmpty() && i < wsConns.size && wsConns[i].isOpen) {
                    wsConns[i].close()
                }
                return Result.failure(Exception(context.getString(R.string.error_unknown_host)))
            }
        }

        Log.d("GetReplayServerIP", "Server IP: $servers")
        generateServerCertificate(true)

        // Get URL(s) for analysis and results
        val port = Config.get("result_port").toInt()
        analyzerServerUrls.clear()
        for (srvr in servers) {
            analyzerServerUrls.add("https://$srvr:$port/Results")
            Log.d("Result Channel", "path: $srvr port: $port")
        }

        if (metadataServer != null) {
            this.metadataServer = serverRepository.getServerIP(metadataServer)
            if (this.metadataServer == "") {
                return Result.failure(Exception(context.getString(R.string.error_unknown_meta_host)))
            }
            generateServerCertificate(false)
        }

        serverRepository.setServers(servers)
        return Result.success(true)
    }

    /**
     * Connect to MLAB servers
     */
    private suspend fun connectToMLabServers(numTests: Int, isTomography: Boolean): Result<Boolean> {
        servers.removeAt(0)
        wsConns.clear()

        try {
            var numTries = 0
            var wsID: Int
            val mLabResp = serverRepository.sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null)

            val mLabServers = mLabResp!!["results"] as JSONArray
            var i = 0
            while (wsConns.size < numTests && i < mLabServers.length()) {
                try {
                    i++
                    wsID = wsConns.size
                    numTries++
                    val serverObj = mLabServers[i] as JSONObject
                    val serverName = "wehe-" + serverObj.getString("machine")
                    val mLabURL = (serverObj["urls"] as JSONObject)
                        .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY)

                    Log.d("WebSocket", "Attempting to connect to server $i: $serverName")
                    wsConns.add(WebSocketConnection(wsID, URI(mLabURL)))

                    Log.d(
                        "WebSocket", "New WebSocket (id: $wsID) connectivity check: " +
                                (if (wsConns[wsID].isOpen) "CONNECTED" else "CLOSED") + " TO $serverName"
                    )
                    servers.add(serverRepository.getServerIP(serverName))
                    numMLab.add(numTries)
                    numTries = 0
                } catch (e: Exception) {
                    Log.w("WebSocket", "Failed to connect to WebSocket", e)
                }
                i++
            }

            if (wsConns.size != numTests) {
                Log.i("GetReplayServerIP", "Can't get MLab server, trying Amazon")
                servers.clear()
                for (ws in wsConns) {
                    if (ws.isOpen) {
                        ws.close()
                    }
                }
                wsConns.clear()
                if (isTomography) {
                    return Result.failure(Exception(context.getString(R.string.tomography_not_supported)))
                }
                servers.add(serverRepository.getServerIP("wehe2.meddle.mobi"))
            }
            return Result.success(true)
        } catch (e: Exception) {
            Log.e("WebSocket", "Can't retrieve M-Lab servers", e)
            return Result.failure(e)
        }
    }

    /**
     * Gets the certificates for the servers
     *
     * @param main true if main server; false if metadata server
     */
    private fun generateServerCertificate(main: Boolean) {
        try {
            val server = if (main) "main" else "metadata"
            val cf = CertificateFactory.getInstance("X.509")
            var ca: Certificate
            context.resources.openRawResource(if (main) R.raw.main else R.raw.metadata)
                .use { caInput ->
                    ca = cf.generateCertificate(caInput)
                    Log.d("Certificate", server + "=" + (ca as X509Certificate).issuerDN)
                }
            // Create a KeyStore containing our trusted CAs
            val keyStoreType = KeyStore.getDefaultType()
            val keyStore = KeyStore.getInstance(keyStoreType)
            keyStore.load(null, null)
            keyStore.setCertificateEntry(server, ca)

            // Create a TrustManager that trusts the CAs in our KeyStore
            val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
            val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
            tmf.init(keyStore)

            // Create an SSLContext that uses our TrustManager
            val context = SSLContext.getInstance("TLS")
            context.init(null, tmf.trustManagers, null)
            if (main) {
                sslSocketFactory = context.socketFactory
                hostnameVerifier =
                    HostnameVerifier { hostname: String?, session: SSLSession? -> true }
                serverRepository.sslSocketFactory = sslSocketFactory
                serverRepository.hostnameVerifier = hostnameVerifier
            }
        } catch (e: Exception) {
            Log.e("Certificates", "Error generating certificates", e)
        }
    }

    /**
     * Asks the server for analysis of a replay
     */
    fun ask4analysis(url: String, id: String?, historyCount: Int): JSONObject? {
        val pairs = HashMap<String, String?>()

        pairs["command"] = "analyze"
        pairs["userID"] = id
        pairs["historyCount"] = historyCount.toString()
        pairs["testID"] = "1"

        return serverRepository.sendRequest(url, "POST", true, null, pairs)
    }

    /**
     * Retrieves a replay result from the server
     */
    fun getSingleResult(url: String, id: String?, historyCount: Int): JSONObject? {
        val data = ArrayList<String>()

        data.add("userID=$id")
        data.add("command=singleResult")
        data.add("historyCount=$historyCount")
        data.add("testID=1")

        return serverRepository.sendRequest(url, "GET", true, data, null)
    }

    /**
     * Reads the replay files and loads them into memory as a bean
     */
    fun loadAppData(filename: String): CombinedAppJSONInfoBean {
        val appData = CombinedAppJSONInfoBean()
        val Q = ArrayList<RequestSet>()

        try {
            val assetManager = context.assets
            val inputStream = assetManager.open(filename)
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            val jsonStr = String(buffer, StandardCharsets.UTF_8)
            val json = JSONArray(jsonStr)

            val qArray = json[0] as JSONArray
            for (i in 0 until qArray.length()) {
                val tempRS = RequestSet()
                val dictionary = qArray.getJSONObject(i)
                tempRS.cSPair = dictionary["c_s_pair"] as String
                tempRS.payload = UtilsManager.hexStringToByteArray(
                    dictionary["payload"] as String
                )
                tempRS.timestamp = dictionary["timestamp"] as Double

                if (dictionary.has("response_len")) {
                    tempRS.responseLen = dictionary["response_len"] as Int
                }
                if (dictionary.has("response_hash")) {
                    tempRS.responseHash = dictionary["response_hash"].toString()
                }
                if (dictionary.has("end")) tempRS.end = dictionary["end"] as Boolean

                Q.add(tempRS)
            }

            appData.q = Q

            val portArray = json[1] as JSONArray
            val portStrArray = ArrayList<String>()
            for (i in 0 until portArray.length()) {
                portStrArray.add(portArray.getString(i))
            }
            appData.udpClientPorts = portStrArray

            val csArray = json[2] as JSONArray
            val csStrArray = ArrayList<String>()
            for (i in 0 until csArray.length()) {
                csStrArray.add(csArray[i] as String)
            }
            appData.tcpCSPs = csStrArray
            appData.replayName = json[3] as String
        } catch (e: Exception) {
            Log.e("UnpickleJSON", "Error reading test files", e)
        }
        return appData
    }

    /**
     * Check if the network is available
     */
    fun isNetworkUnavailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork ?: return true
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities != null) {
                return !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return true
    }

    /**
     * Save results to shared preferences
     */
    fun saveResults(results: JSONArray?, settings: SharedPreferences?) {
        val dateFormat: DateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val strDate = dateFormat.format(Date())

        var resultsWithDate = try {
            JSONObject(settings?.getString("lastResult", "{}"))
        } catch (e: JSONException) {
            JSONObject()
        }

        if (resultsWithDate.length() >= 10) {
            val it = resultsWithDate.keys()
            if (it.hasNext()) {
                resultsWithDate.remove(it.next())
            } else {
                Log.w("Result Channel", "iterator doesn't have next but length is not 0")
            }
        }

        try {
            resultsWithDate.put(strDate, results)
        } catch (e: JSONException) {
            Log.e("saveResults", "Error saving results, $e")
            return
        }

        settings?.edit()?.apply {
            putString("lastResult", resultsWithDate.toString())
            apply()
        }
    }

    /**
     * Close all connections
     */
    fun closeConnections() {
        for (ws in wsConns) {
            ws.close()
        }
//        wsConns.clear()
    }

    /**
     * Get all analyzer server URLs
     */
    fun getAnalyzerServerUrls(): List<String> = analyzerServerUrls

    /**
     * Check if MLab server was used
     */
    fun isMlabServerUsed(): Boolean = mlabServerUsed
}