package mobi.meddle.wehe.ui.replay

import android.content.Context
import android.content.SharedPreferences
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.delay
import mobi.meddle.wehe.BuildConfig
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.CTCPClient
import mobi.meddle.wehe.combined.CUDPClient
import mobi.meddle.wehe.combined.CombinedAnalyzerTask
import mobi.meddle.wehe.combined.CombinedQueue
import mobi.meddle.wehe.combined.CombinedSideChannel
import mobi.meddle.wehe.combined.WebSocketConnection
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.bean.RequestSet
import mobi.meddle.wehe.data.bean.ServerInstance
import mobi.meddle.wehe.data.bean.UDPReplayInfoBean
import mobi.meddle.wehe.data.bean.UpdateUIBean
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
import kotlin.coroutines.CoroutineContext
import kotlin.math.abs

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
    private suspend fun connectToMLabServers(
        numTests: Int,
        isTomography: Boolean
    ): Result<Boolean> {
        servers.removeAt(0)
        wsConns.clear()

        try {
            var numTries = 0
            var wsID: Int
            val mLabResp =
                serverRepository.sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null)

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
     * Loads application data based on replay type
     *
     * @param app The application to test
     * @param channel The replay type ("open" or "random")
     * @return The loaded app data
     */
    fun loadAppDataForReplay(app: ApplicationBean, channel: String): CombinedAppJSONInfoBean {
        return if (channel.equals("open", ignoreCase = true)) {
            loadAppData(app.dataFile)
        } else if (channel.equals("random", ignoreCase = true)) {
            loadAppData(app.randomDataFile)
        } else {
            Log.wtf("replayIndex", "replay name error: $channel")
            CombinedAppJSONInfoBean() // Return empty bean for error
        }
    }

    /**
     * Sets up side channels for communication with server
     *
     * @param appData The app data for the test
     * @return List of created side channels
     */
    fun setupSideChannels(appData: CombinedAppJSONInfoBean): ArrayList<CombinedSideChannel> {
        val sideChannelPort = Config.get("combined_sidechannel_port").toInt()
        val sideChannels = ArrayList<CombinedSideChannel>()

        var id = 0
        for (server in servers) {
            sideChannels.add(
                CombinedSideChannel(
                    id, sslSocketFactory!!,
                    server, sideChannelPort, appData.isTCP
                )
            )
            id++
        }

        return sideChannels
    }

    /**
     * Gets port mapping from server
     *
     * @param sideChannels The established side channels
     * @return Pair of server ports maps and UDP replay info
     */
    suspend fun getPortMappingFromServer(sideChannels: ArrayList<CombinedSideChannel>): Pair<
            ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>>,
            ArrayList<UDPReplayInfoBean>
            > {
        val serverPortsMaps =
            ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>>()
        val udpReplayInfoBeans = ArrayList<UDPReplayInfoBean>()

        for (sc in sideChannels) {
            serverPortsMaps.add(sc.receivePortMappingNonBlock())
            val udpReplayInfoBean = UDPReplayInfoBean()
            udpReplayInfoBean.senderCount = sc.receiveSenderCount()
            udpReplayInfoBeans.add(udpReplayInfoBean)
            Log.i(
                "Replay",
                "Channel ${sc.id}: Successfully received serverPortsMap and senderCount!"
            )
        }

        return Pair(serverPortsMaps, udpReplayInfoBeans)
    }

    /**
     * Creates TCP clients from CSPairs
     *
     * @param appData The app data for the test
     * @param serverPortsMaps The port mappings received from the server
     * @return List of TCP client mappings
     */
    fun createTCPClients(
        appData: CombinedAppJSONInfoBean,
        serverPortsMaps: ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>>
    ): ArrayList<HashMap<String, CTCPClient>> {
        val CSPairMappings = ArrayList<HashMap<String, CTCPClient>>()

        for (sc in 0 until servers.size) {
            val CSPairMapping = HashMap<String, CTCPClient>()
            for (csp in appData.tcpCSPs) {
                // Get server IP and port
                val destIP = csp.substring(
                    csp.lastIndexOf('-') + 1,
                    csp.lastIndexOf(".")
                )
                var destPort = csp.substring(csp.lastIndexOf('.') + 1)
                // Pad port to 5 digits with 0s; ex. 00443 or 00080
                destPort = String.format("%5s", destPort).replace(' ', '0')

                // Get the server
                val instance: ServerInstance
                try {
                    instance = serverPortsMaps[sc]["tcp"]
                        ?.get(destIP)
                        ?.get(destPort)!!
                } catch (e: Exception) {
                    Log.e("Replay", "Channel $sc: Cannot get instance", e)
                    throw e
                }

                if (instance.server.trim { it <= ' ' } == "") {
                    instance.server = servers[sc].toString()
                }

                // Create the client
                val c = CTCPClient(
                    csp, instance.server,
                    instance.port.toInt(),
                    appData.replayName, Config.get("publicIP"), false
                )
                CSPairMapping[csp] = c
            }
            CSPairMappings.add(CSPairMapping)
        }

        return CSPairMappings
    }

    /**
     * Creates UDP clients from client ports
     *
     * @param appData The app data for the test
     * @return List of UDP client mappings
     */
    fun createUDPClients(
        appData: CombinedAppJSONInfoBean
    ): ArrayList<HashMap<String, CUDPClient>> {
        val udpPortMappings = ArrayList<HashMap<String, CUDPClient>>()

        for (sc in 0 until servers.size) {
            val udpPortMapping = HashMap<String, CUDPClient>()
            for (originalClientPort in appData.udpClientPorts) {
                val c = CUDPClient(Config.get("publicIP"))
                udpPortMapping[originalClientPort] = c
            }
            udpPortMappings.add(udpPortMapping)
        }

        return udpPortMappings
    }

    /**
     * Checks if connection to the specified port is allowed
     *
     * @param replayPort The port to check
     * @return True if port is accessible, false if blocked
     */
    suspend fun checkPortAccess(replayPort: String): Boolean {
        val ipThroughProxy = serverRepository.getPublicIP(replayPort)
        return ipThroughProxy != "-1"
    }

    /**
     * Sets up and initiates test with the server
     *
     * @param sideChannels The established side channels
     * @param appData The app data for the test
     * @param randomID User's random ID
     * @param historyCount Current history count
     * @param testId Test identifier (0 for open, 1 for random)
     * @param endOfTest Whether this is the last test in the sequence
     * @param doTest Additional test flag
     * @param ipThroughProxy User's IP address
     * @return List of number of time slices from the server
     */
    suspend fun initiateTestWithServer(
        sideChannels: ArrayList<CombinedSideChannel>,
        appData: CombinedAppJSONInfoBean,
        randomID: String?,
        historyCount: Int,
        testId: Int,
        endOfTest: Boolean,
        doTest: Boolean,
        ipThroughProxy: String
    ): Result<ArrayList<Int>> {
        // Step 1: Tell server(s) about the replay
        var i = 0
        for (sc in sideChannels) {
            // Set extra string to number of tries needed to access MLab server
            Config.set("extraString", if (numMLab.size == 0) "0" else numMLab[i].toString())
            sc.declareID(
                appData.replayName, if (endOfTest) "True" else "False",
                randomID, historyCount.toString(), testId.toString(),
                if (doTest) Config.get("extraString") + "-Test" else Config.get("extraString"),
                ipThroughProxy, BuildConfig.VERSION_NAME
            )

            // Tell server if it should operate on packets of traces
            sc.sendChangeSpec(-1, "null", "null")
            i++
        }

        // Step 2: Ask server(s) for permission
        val numOfTimeSlices = ArrayList<Int>()
        for (sc in sideChannels) {
            val permission = sc.ask4Permission()
            val status = permission[0].trim { it <= ' ' }

            Log.d(
                "Replay", ("Channel " + sc.id + ": permission[0]: "
                        + status + " permission[1]: " + permission[1])
            )

            val permissionError = permission[1].trim { it <= ' ' }
            if (status == "0") {
                // Errors that server can report
                val errorCode = when (permissionError) {
                    "1" -> R.string.error_unknown_replay
                    "2" -> R.string.error_IP_connected
                    "3" -> R.string.error_low_resources
                    else -> R.string.error_unknown
                }
                return Result.failure(Exception(context.getString(errorCode)))
            }
            numOfTimeSlices.add(permission[2].trim { it <= ' ' }.toInt(10))
        }

        // Step 3: Send noIperf
        for (sc in sideChannels) {
            sc.sendIperf() // Always send noIperf here
        }

        // Step 4: Send device info
        for (sc in sideChannels) {
            sc.sendMobileStats(Config.get("sendMobileStats"), context)
        }

        return Result.success(numOfTimeSlices)
    }

    /**
     * Run packet queue to server
     *
     * @param queue The combined queue of packets to send
     * @param numberOfTypes Number of replay types being run
     * @param CSPairMappings TCP client mappings
     * @param udpPortMappings UDP client mappings
     * @param udpReplayInfoBeans UDP replay info
     * @param udpServerMappings UDP server mappings
     * @param context Coroutine context for cancellation
     * @return Elapsed time in seconds
     */
    suspend fun runPacketQueue(
        queue: CombinedQueue,
        numberOfTypes: Int,
        CSPairMappings: ArrayList<HashMap<String, CTCPClient>>,
        udpPortMappings: ArrayList<HashMap<String, CUDPClient>>,
        udpReplayInfoBeans: ArrayList<UDPReplayInfoBean>,
        udpServerMappings: ArrayList<HashMap<String, HashMap<String, ServerInstance>>>,
        updateUIBean: UpdateUIBean,
        coroutineContext: CoroutineContext
    ): Double {
        val timeStarted = System.nanoTime()

        queue.run(
            updateUIBean, numberOfTypes, CSPairMappings,
            udpPortMappings, udpReplayInfoBeans, udpServerMappings,
            Config.get("timing").toBoolean(), servers, coroutineContext
        )

        return ((System.nanoTime() - timeStarted).toDouble()) / 1000000000
    }

    /**
     * Process results at the end of test
     *
     * @param sideChannels The established side channels
     * @param duration Test duration in seconds
     * @param analyzerTasks The analyzer tasks containing throughput data
     */
    suspend fun processTestResults(
        sideChannels: ArrayList<CombinedSideChannel>,
        duration: Double,
        analyzerTasks: ArrayList<CombinedAnalyzerTask>
    ) {
        // Tell server replay is finished
        for (sc in sideChannels) {
            sc.sendDone(duration)
        }

        // Send throughputs and slices to server
        for (sc in sideChannels) {
            sc.sendTimeSlices(analyzerTasks[sc.id].averageThroughputsAndSlices)
        }

        // Send Result;No and wait for OK before moving forward
        for (sc in sideChannels) {
            while (sc.getResult(Config.get("result"))) {
                delay(500)
            }
        }
    }

    /**
     * Update history count in shared preferences
     *
     * @param historyCount Current history count
     * @return Updated history count
     */
    fun updateHistoryCount(historyCount: Int): Int {
        val updatedCount = historyCount + 1
        val settings = context.getSharedPreferences("STATUS", Context.MODE_PRIVATE)
        settings.edit().putInt("historyCount", updatedCount).apply()
        Log.d("Replay", "historyCount: $updatedCount")
        return updatedCount
    }

    /**
     * Clean up resources after test
     *
     * @param sideChannels The side channels to close
     * @param CSPairMappings TCP client mappings to close
     * @param udpPortMappings UDP client mappings to close
     * @param appData App data containing client ports
     */
    fun cleanupResources(
        sideChannels: ArrayList<CombinedSideChannel>,
        CSPairMappings: ArrayList<HashMap<String, CTCPClient>>,
        udpPortMappings: ArrayList<HashMap<String, CUDPClient>>,
        appData: CombinedAppJSONInfoBean
    ) {
        // Close side channel sockets
        for (sc in sideChannels) {
            sc.closeSideChannelSocket()
        }

        // Close TCP sockets
        for (mapping in CSPairMappings) {
            for (csp in appData.tcpCSPs) {
                mapping[csp]?.close()
            }
        }

        // Close UDP sockets
        for (mapping in udpPortMappings) {
            for (originalClientPort in appData.udpClientPorts) {
                mapping[originalClientPort]?.close()
            }
        }
    }

    /**
     * Requests analysis for test results from the server
     *
     * @param randomID User's random ID
     * @param historyCount Current history count
     * @return List of analysis results from servers
     */
    fun requestAnalysis(randomID: String, historyCount: Int): Result<ArrayList<JSONObject>> {
        val analysisResults = ArrayList<JSONObject>()

        for (server in getAnalyzerServerUrls()) {
            for (retry in 3 downTo 1) {
                val resp = ask4analysis(server, randomID, historyCount)
                if (resp == null) {
                    Log.e("Result Channel", "$server: ask4analysis returned null!")
                } else {
                    analysisResults.add(resp)
                    break
                }
            }
        }

        if (analysisResults.size != getAnalyzerServerUrls().size) {
            return Result.failure(Exception(context.getString(R.string.error_analysis_fail)))
        }

        // Verify all results were successful
        for (result in analysisResults) {
            val success = result.getBoolean("success")
            if (!success) {
                Log.e("Result Channel", "ask4analysis failed!")
                return Result.failure(Exception(context.getString(R.string.error_analysis_fail)))
            }
        }

        Log.i("Result Channel", "ask4analysis succeeded!")
        return Result.success(analysisResults)
    }

    /**
     * Retrieves test results from the server
     *
     * @param randomID User's random ID
     * @param historyCount Current history count
     * @param runPortTests Whether this is a port test
     * @return List of result JSONObjects
     */
    suspend fun retrieveResults(randomID: String, historyCount: Int, runPortTests: Boolean): Result<List<JSONObject>> {
        val analysisResults = ArrayList<JSONObject>()

        for (url in getAnalyzerServerUrls()) {
            var attempt = 0
            while (attempt < 3) {
                val resp = getSingleResult(url, randomID, historyCount)

                if (resp == null) {
                    Log.e("Result Channel", "$url: getSingleResult returned null!")
                } else {
                    val success = resp.getBoolean("success")
                    if (success) {
                        if (resp.has("response")) {
                            analysisResults.add(resp)
                            Log.i("Result Channel", "$url: retrieve result succeeded")
                            break
                        } else {
                            Log.w("Result Channel", "$url: Server result not ready")
                        }
                    } else if (resp.has("error")) {
                        Log.e("Result Channel", "ERROR: $url: ${resp.getString("error")}")
                    } else {
                        Log.e("Result Channel", "Error: $url: Some error getting results.")
                    }
                }

                if (attempt < 3) {
                    delay(2000)
                } else if (runPortTests) {
                    // Port is likely blocked
                    Log.i("Result Channel", "Can't retrieve result, port blocked")
                    return Result.success(emptyList()) // Return empty to signal port blocked
                } else {
                    return Result.failure(Exception(context.getString(R.string.not_all_tcp_sent_text)))
                }
                attempt++
            }
        }

        return Result.success(analysisResults)
    }

    /**
     * Analyze test results to determine differentiation
     *
     * @param response The server response JSON object
     * @param randomID User's random ID
     * @param historyCount Current history count
     * @param appName Application name
     * @param dataFile Data file name
     * @param runPortTests True if port tests
     * @param a_threshold Area threshold for differentiation
     * @param ks2pvalue_threshold KS2 p-value threshold
     * @param randomThroughput Random throughput value (for port tests)
     * @return ResultAnalysis object containing analysis results
     */
    fun analyzeResults(
        response: JSONObject,
        randomID: String,
        historyCount: Int,
        appName: String,
        dataFile: String,
        runPortTests: Boolean,
        a_threshold: Int,
        ks2pvalue_threshold: Int,
        randomThroughput: Double
    ): Result<ResultAnalysis> {
        try {
            // Create response for port blocked case
            val finalResponse = if (response.length() == 0) {
                val newResponse = JSONObject()
                newResponse.put("userID", randomID)
                newResponse.put("historyCount", historyCount)
                newResponse.put("replayName", dataFile)
                newResponse.put("area_test", -1)
                newResponse.put("ks2pVal", -1)
                newResponse.put("ks2_ratio_test", -1)
                newResponse.put("xput_avg_original", 0)
                newResponse.put("xput_avg_test", randomThroughput)
                newResponse
            } else {
                response
            }

            Log.d("Result Channel", "SERVER RESPONSE: $finalResponse")

            // Extract values from response
            val userID = finalResponse.getString("userID")
            val responseHistoryCount = finalResponse.getInt("historyCount")
            val area_test = finalResponse.getDouble("area_test")
            val ks2pVal = finalResponse.getDouble("ks2pVal")
            val ks2RatioTest = finalResponse.getDouble("ks2_ratio_test")
            val xputOriginal = finalResponse.getDouble("xput_avg_original")
            val xputTest = finalResponse.getDouble("xput_avg_test")

            // Sanity check
            if ((!userID.trim().equals(randomID, ignoreCase = true)) ||
                (responseHistoryCount != historyCount)) {
                Log.e("Result Channel", "Result didn't pass sanity check! " +
                        "correct id: $randomID correct historyCount: $historyCount")
                Log.e("Result Channel", "Result content: $finalResponse")
                return Result.failure(Exception(context.getString(R.string.error_result)))
            }

            // Determine thresholds
            var areaThreshold = a_threshold
            if (xputOriginal > 10 || xputTest > 10) {
                areaThreshold = 30
            }

            val area_test_threshold = areaThreshold.toDouble() / 100
            val ks2pVal_threshold = ks2pvalue_threshold.toDouble() / 100

            // Check for differentiation
            val aboveArea = abs(area_test) >= area_test_threshold
            val belowP = ks2pVal < ks2pVal_threshold
            val portBlocked = response.length() == 0

            var differentiation = false
            var inconclusive = false

            if (portBlocked) {
                differentiation = true
            } else if (aboveArea) {
                if (belowP) {
                    differentiation = true
                } else {
                    inconclusive = true
                }
            }

            // Create error message if differentiation
            var errorMessage: String? = null
            if (differentiation) {
                errorMessage = if (portBlocked) {
                    if (runPortTests) context.getString(R.string.test_blocked_port_text)
                    else context.getString(R.string.test_blocked_app_text)
                } else {
                    if (xputOriginal > xputTest) {
                        if (runPortTests) context.getString(R.string.test_throttled_port_text)
                        else context.getString(R.string.test_throttled_app_text)
                    } else {
                        if (runPortTests) context.getString(R.string.test_prioritized_port_text)
                        else context.getString(R.string.test_prioritized_app_text)
                    }
                }
            }

            // Create result analysis object
            return Result.success(
                ResultAnalysis(
                    differentiation = differentiation,
                    inconclusive = inconclusive,
                    portBlocked = portBlocked,
                    area_test = area_test,
                    ks2pVal = ks2pVal,
                    ks2RatioTest = ks2RatioTest,
                    xputOriginal = xputOriginal,
                    xputTest = xputTest,
                    errorMessage = errorMessage,
                    response = finalResponse
                )
            )
        } catch (e: JSONException) {
            Log.e("Result Channel", "parsing json error", e)
            return Result.failure(e)
        }
    }

    /**
     * Data class to hold result analysis
     */
    data class ResultAnalysis(
        val differentiation: Boolean,
        val inconclusive: Boolean,
        val portBlocked: Boolean,
        val area_test: Double,
        val ks2pVal: Double,
        val ks2RatioTest: Double,
        val xputOriginal: Double,
        val xputTest: Double,
        val errorMessage: String?,
        val response: JSONObject
    )

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