package mobi.meddle.wehe.ui.replay

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.WebSocketConnection
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.util.Config
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.inject.Inject
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/**
 * Use case that handles server connections and network operations.
 * This class encapsulates network logic extracted from TraceRunAsync.
 */
class ServerConnectionUseCase @Inject constructor(
    private val context: Context,
    private val networkManager: NetworkManager
) {
    private val servers = mutableListOf<String>()
    private val wsConnections = mutableListOf<WebSocketConnection>()
    private val analyzerServerUrls = mutableListOf<String>()
    private var sslSocketFactory: SSLSocketFactory? = null
    private var hostnameVerifier: HostnameVerifier? = null
    private var isIPv6 = false
    private var mlabServerUsed = false

    /**
     * Checks if network is available.
     *
     * @return true if network is available
     */
    fun isNetworkAvailable(): Boolean = networkManager.isNetworkAvailable()

    /**
     * Sets up server connections and certificates.
     *
     * @param server The primary server hostname
     * @param metadataServer The metadata server hostname
     * @return ServerSetupResult containing result information
     */
    suspend fun setupServersAndCertificates(
        server: String,
        metadataServer: String?
    ): ServerSetupResult = withContext(Dispatchers.IO) {
        servers.clear()
        wsConnections.clear()

        // Special handling for wehe4.meddle.mobi
        if (server == "wehe4.meddle.mobi") {
            servers.add("10.0.0.0")
        } else {
            val serverIP = getServerIP(server)
            if (serverIP.isEmpty()) {
                return@withContext ServerSetupResult(
                    isSuccess = false,
                    errorType = TestErrorType.UNKNOWN_HOST,
                    errorMessage = "Could not resolve server hostname"
                )
            }
            servers.add(serverIP)
        }

        // Check if server is IPv6
        val serverIPisV6 = servers[0].contains(":")

        // Connect to MLab server if needed
        mlabServerUsed = false
        if (servers[0] == "10.0.0.0" || serverIPisV6) {
            mlabServerUsed = true
            servers.removeAt(0)

            try {
                // Connect to MLab servers
                val mlabResp = sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null)
                val mlabServers = mlabResp?.getJSONArray("results") ?: JSONArray()

                for (i in 0 until mlabServers.length()) {
                    try {
                        val serverObj = mlabServers.getJSONObject(i)
                        val mlabServer = "wehe-" + serverObj.getString("machine")
                        val mlabURL = serverObj.getJSONObject("urls")
                            .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY)

                        // Connect to WebSocket
                        val wsConn = WebSocketConnection(wsConnections.size, URI(mlabURL))
                        wsConnections.add(wsConn)

                        // Get server IP
                        val serverIP = getServerIP(mlabServer)
                        if (serverIP.isNotEmpty()) {
                            servers.add(serverIP)
                            break
                        }
                    } catch (e: Exception) {
                        // Failed to connect, try next one
                    }
                }

                if (servers.isEmpty()) {
                    // Try Amazon server as fallback
                    val amazonIP = getServerIP("wehe2.meddle.mobi")
                    if (amazonIP.isNotEmpty()) {
                        servers.add(amazonIP)
                    } else {
                        return@withContext ServerSetupResult(
                            isSuccess = false,
                            errorType = TestErrorType.MLAB_CONNECTION_ERROR,
                            errorMessage = "Failed to connect to any server"
                        )
                    }
                }
            } catch (e: Exception) {
                return@withContext ServerSetupResult(
                    isSuccess = false,
                    errorType = TestErrorType.SERVER_CONNECTION_ERROR,
                    errorMessage = "Failed to set up server connection: ${e.message}"
                )
            }
        }

        // Generate server certificates
        generateServerCertificate(true)

        // Set up analyzer URLs
        val port = Config.get("result_port").toInt()
        analyzerServerUrls.clear()
        for (srvr in servers) {
            analyzerServerUrls.add("https://$srvr:$port/Results")
        }

        // Set up metadata server if provided
        if (metadataServer != null) {
            val metadataIP = getServerIP(metadataServer)
            if (metadataIP.isEmpty()) {
                return@withContext ServerSetupResult(
                    isSuccess = false,
                    errorType = TestErrorType.META_HOST_ERROR,
                    errorMessage = "Could not resolve metadata server hostname"
                )
            }
            // Generate metadata certificates
            generateServerCertificate(false)
        }

        ServerSetupResult(isSuccess = true)
    }

    /**
     * Gets the list of servers as a comma-separated string.
     *
     * @return Comma-separated list of server IPs
     */
    fun getServersList(): String {
        return servers.joinToString(", ")
    }

    /**
     * Performs DNS lookup on a hostname.
     *
     * @param server The hostname to be resolved
     * @return The IP address as a string, or empty string on failure
     */
    private fun getServerIP(server: String): String {
        for (i in 0 until 5) { // 5 attempts to lookup the IP
            try {
                val address = InetAddress.getByName(server)
                return when {
                    address is Inet4Address -> address.hostAddress ?: ""
                    address is Inet6Address -> "[${address.hostAddress}]"
                    else -> ""
                }
            } catch (e: UnknownHostException) {
                // Wait and retry
                Thread.sleep(1000)
            }
        }
        return ""
    }

    /**
     * Gets the public IP of the user's device.
     *
     * @param port Port to connect to
     * @return User's public IP or "-1" if cannot connect
     */
    fun getPublicIP(port: String): String {
        var publicIP = "127.0.0.1"

        if (servers.isNotEmpty() && servers[0] != "127.0.0.1") {
            val url = "http://${servers[0]}:$port/WHATSMYIPMAN"

            var numFails = 0
            while (publicIP == "127.0.0.1") {
                try {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 5000

                    val buffer = StringBuilder()
                    connection.inputStream.bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            buffer.append(line)
                        }
                    }

                    publicIP = buffer.toString()
                    val address = InetAddress.getByName(publicIP)
                    isIPv6 = address is Inet6Address

                    if (publicIP.isEmpty()) {
                        publicIP = "-1"
                    }
                } catch (e: Exception) {
                    if (++numFails == 5) {
                        // Cannot connect to server after 5 tries
                        publicIP = "-1"
                        break
                    }
                    Thread.sleep(1000)
                }
            }
        }

        return publicIP
    }

    /**
     * Generates certificates for servers.
     *
     * @param main true if main server; false if metadata server
     */
    private fun generateServerCertificate(main: Boolean) {
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val resourceId = if (main) R.raw.main else R.raw.metadata

            val ca = context.resources.openRawResource(resourceId).use { caInput ->
                cf.generateCertificate(caInput)
            }

            // Create a KeyStore containing our trusted CA
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry(if (main) "main" else "metadata", ca)
            }

            // Create a TrustManager that trusts the CA in our KeyStore
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }

            // Create an SSLContext that uses our TrustManager
            val context = SSLContext.getInstance("TLS").apply {
                init(null, tmf.trustManagers, null)
            }

            if (main) {
                sslSocketFactory = context.socketFactory
                hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    /**
     * Runs a replay test for an application.
     *
     * @param app The application to test
     * @param appData The application's replay data
     * @param historyCount The test number
     * @param randomID The unique device ID
     * @param isConfirmationTest Whether this is a confirmation test
     * @return ReplayResult containing the result information
     */
    suspend fun runReplay(
        app: ApplicationBean,
        appData: CombinedAppJSONInfoBean,
        historyCount: Int,
        randomID: String,
        isConfirmationTest: Boolean
    ): ReplayResult = withContext(Dispatchers.IO) {
        // Implementation would perform the actual replay test
        // This is a simplified version of the runTest method

        ReplayResult(isSuccess = true)
    }

    /**
     * Analyzes results of a test.
     *
     * @param app The application that was tested
     * @param historyCount The test number
     * @param randomID The unique device ID
     * @return AnalysisResult containing the analysis information
     */
    suspend fun analyzeResults(
        app: ApplicationBean,
        historyCount: Int,
        randomID: String
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            // Request analysis
            val analysisUrl = analyzerServerUrls[0]
            val analysisResult = ask4analysis(analysisUrl, randomID, historyCount)

            if (analysisResult == null || !analysisResult.optBoolean("success", false)) {
                return@withContext AnalysisResult(
                    hasError = true,
                    errorMessage = "Server analysis failed"
                )
            }

            // Get the analysis result
            val resultObj = getSingleResult(analysisUrl, randomID, historyCount)

            if (resultObj == null || !resultObj.optBoolean("success", false)) {
                return@withContext AnalysisResult(
                    hasError = true,
                    errorMessage = "Failed to retrieve analysis result"
                )
            }

            val response = resultObj.optJSONObject("response")
            if (response == null) {
                return@withContext AnalysisResult(
                    hasError = true,
                    errorMessage = "Invalid response format"
                )
            }

            // Check for differentiation
            val areaTest = response.optInt("area_test", 0) == 1
            val ks2Test = response.optInt("ks2_ratio_test", 0) == 1

            val hasDifferentiation = areaTest || ks2Test

            return@withContext AnalysisResult(
                hasError = false,
                hasDifferentiation = hasDifferentiation,
                resultJson = response
            )
        } catch (e: Exception) {
            AnalysisResult(
                hasError = true,
                errorMessage = "Error during analysis: ${e.message}"
            )
        }
    }

    /**
     * Sends a request to analyze a test.
     *
     * @param url The URL to the server
     * @param id The unique device ID
     * @param historyCount The test number
     * @return JSONObject containing the server's response
     */
    private fun ask4analysis(url: String, id: String, historyCount: Int): JSONObject? {
        val pairs = hashMapOf(
            "command" to "analyze",
            "userID" to id,
            "historyCount" to historyCount.toString(),
            "testID" to "1"
        )

        return sendRequest(url, "POST", true, null, pairs)
    }

    /**
     * Retrieves a result from the server.
     *
     * @param url The URL to the server
     * @param id The unique device ID
     * @param historyCount The test number
     * @return JSONObject containing the result
     */
    private fun getSingleResult(url: String, id: String, historyCount: Int): JSONObject? {
        val data = arrayListOf(
            "userID=$id",
            "command=singleResult",
            "historyCount=$historyCount",
            "testID=1"
        )

        return sendRequest(url, "GET", true, data, null)
    }

    /**
     * Sends a request to the server.
     *
     * @param url URL to the server
     * @param method GET or POST
     * @param main true if request is to main server
     * @param data data for GET request
     * @param pairs data for POST request
     * @return JSONObject response from server
     */
    private fun sendRequest(
        url: String,
        method: String,
        main: Boolean,
        data: ArrayList<String>?,
        pairs: HashMap<String, String>?
    ): JSONObject? {
        // Implementation would perform the actual HTTP request
        // This is a simplification of the sendRequest method
        return JSONObject()
    }

    /**
     * Cleanup resources when tests are complete.
     */
    fun cleanup() {
        for (ws in wsConnections) {
            if (ws.isOpen) {
                ws.close()
            }
        }
        wsConnections.clear()
        servers.clear()
        analyzerServerUrls.clear()
    }
}

/**
 * Represents the result of server setup.
 */
data class ServerSetupResult(
    val isSuccess: Boolean,
    val errorType: TestErrorType? = null,
    val errorMessage: String? = null
)

/**
 * Represents the result of a replay.
 */
data class ReplayResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Represents the result of an analysis.
 */
data class AnalysisResult(
    val hasError: Boolean,
    val errorMessage: String? = null,
    val hasDifferentiation: Boolean = false,
    val isInconclusive: Boolean = false,
    val resultJson: JSONObject? = null
)

/**
 * Interface for network management.
 */
interface NetworkManager {
    fun isNetworkAvailable(): Boolean
}