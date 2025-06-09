package mobi.meddle.wehe.ui.replay

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.CombinedAnalyzerTask
import mobi.meddle.wehe.combined.CombinedNotifierThread
import mobi.meddle.wehe.combined.CombinedQueue
import mobi.meddle.wehe.combined.CombinedReceiverThread
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.model.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.model.ServerInstance
import mobi.meddle.wehe.data.model.UpdateUIBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import mobi.meddle.wehe.util.Config
import mobi.meddle.wehe.util.RandomString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

/**
 * Background test runner that doesn't rely on LiveData/ViewModel pattern
 * Designed specifically for background service execution
 */
class BackgroundTestRunner(
    private val replayRepository: ReplayRepository,
    private val applicationContext: Context,
    private val onProgressUpdate: (Int) -> Unit,
    private val onStatusUpdate: (Pair<String, String>) -> Unit,
    private val onCurrentAppUpdate: (ApplicationBean?) -> Unit,
    private val onIterationUpdate: (Int) -> Unit,
    private val onTestComplete: (List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>) -> Unit,
    private val onError: (String) -> Unit
) {

    private var job: Job? = null
    private var isRunning = false

    // Test results
    private val diffApps = ArrayList<ApplicationBean>()
    private val inconclusiveApps = ArrayList<ApplicationBean>()

    // Test configuration
    private var confirmationReplays = false
    private var useDefaultThresholds = false
    private var a_threshold = 0
    private var ks2pvalue_threshold = 0
    private var settings: SharedPreferences? = null
    private var randomID: String? = null
    private var historyCount = 0
    private var testId = 0
    private var results: JSONArray? = null
    private var serverDisplay: String? = null
    private var metadataServer: String? = null
    private var updateUIBean: UpdateUIBean? = null
    private var doTest = false

    suspend fun runTests(
        runPortTests: Boolean,
        carrier: String?,
        selectedApps: ArrayList<ApplicationBean>
    ) {
        if (isRunning) {
            Log.w(TAG, "Tests already running")
            return
        }

        isRunning = true
        diffApps.clear()
        inconclusiveApps.clear()

        try {
            Log.d(TAG, "Starting background tests for ${selectedApps.size} apps")

            // Initialize test configuration
            if (!initializeTestConfiguration(runPortTests)) {
                onError("Failed to initialize test configuration")
                return
            }

            // Initialize apps status
            selectedApps.forEach { app ->
                app.status = applicationContext.getString(R.string.pending) ?: "Waiting to start"
            }

            // Run tests for each app
            for ((index, app) in selectedApps.withIndex()) {
                if (!isRunning) break // Check if cancelled

                onIterationUpdate(index + 1)
                onCurrentAppUpdate(app)

                // Update progress
                val progress = ((index.toFloat() / selectedApps.size) * 100).toInt()
                onProgressUpdate(progress)

                // Update status
                onStatusUpdate(Pair(app.name ?: "Unknown App", "Starting test"))

                try {
                    // Run the actual test for this app
                    val testResult = runTestForApp(app, runPortTests, carrier)

                    // Process test result
                    when (testResult) {
                        TestResult.DIFFERENTIATED -> {
                            diffApps.add(app)
                            app.status = applicationContext.getString(R.string.has_diff)
                        }
                        TestResult.NOT_DIFFERENTIATED -> {
                            app.status = applicationContext.getString(R.string.no_diff)
                        }
                        TestResult.INCONCLUSIVE -> {
                            inconclusiveApps.add(app)
                            app.status = applicationContext.getString(R.string.inconclusive)
                        }
                        TestResult.ERROR -> {
                            // temporary error message
                            app.status = applicationContext.getString(R.string.error)
                        }
                    }

                    onStatusUpdate(Pair(app.name ?: "Unknown App", app.status))
                    Log.d(TAG, "Completed test for ${app.name}: ${app.status}")

                } catch (e: Exception) {
                    Log.e(TAG, "Error testing app ${app.name}", e)
                    app.status = applicationContext.getString(R.string.error)
                    onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.error)))
                }

                // Add delay between tests if needed
                delay(1000)
            }

            // Save results if any
            if ((results?.length() ?: 0) > 0) {
                Log.i(TAG, "Storing results")
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
                replayRepository.saveResults(results, sharedPrefs)
            }

            // Final progress update
            onProgressUpdate(100)
            onStatusUpdate(Pair("Complete", "All tests finished"))

            // Report results
            onTestComplete(selectedApps, diffApps, inconclusiveApps)

        } catch (e: CancellationException) {
            Log.d(TAG, "Tests cancelled")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during test execution", e)
            onError("Test execution failed: ${e.message}")
        } finally {
            isRunning = false
            onCurrentAppUpdate(null)
        }
    }

    /**
     * Initialize test configuration similar to ReplayViewModel
     */
    private suspend fun initializeTestConfiguration(runPortTests: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                updateUIBean = UpdateUIBean()
                Config.readConfigFile(Consts.CONFIG_FILE, applicationContext)

                // Get settings from SharedPreferences
                val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

                sharedPrefs.let {
                    confirmationReplays = it.getBoolean("pref_multiple_tests", true)
                    useDefaultThresholds = it.getBoolean("pref_switch", true)
                    a_threshold = it.getString("pref_threshold_area", "10")?.toInt() ?: 10
                    ks2pvalue_threshold = it.getString("pref_threshold_ks2p", "5")?.toInt() ?: 5
                }

                serverDisplay = sharedPrefs.getString(
                    applicationContext.getString(R.string.pref_server_key),
                    Consts.DEFAULT_SERVER
                )

                metadataServer = Consts.METADATA_SERVER

                if (!setupServersAndCertificates(serverDisplay!!, metadataServer)) {
                    onError("Server unavailable")
                    return@withContext false
                }

                // Generate or retrieve user ID
                val hasID = sharedPrefs.getBoolean("hasID", false)
                if (!hasID) {
                    randomID = RandomString(10).nextString()
                    sharedPrefs.edit().apply {
                        putBoolean("hasID", true)
                        putString("ID", randomID)
                        apply()
                    }
                } else {
                    randomID = sharedPrefs.getString("ID", null)
                }

                // Initialize history count
                settings = applicationContext.getSharedPreferences("STATUS", Context.MODE_PRIVATE)
                val hasHistoryCount = settings?.getBoolean("hasHistoryCount", false) ?: false
                if (!hasHistoryCount) {
                    historyCount = 0
                    settings?.edit()?.apply {
                        putBoolean("hasHistoryCount", true)
                        putInt("historyCount", historyCount)
                        apply()
                    }
                } else {
                    historyCount = settings?.getInt("historyCount", -1) ?: -1
                    if (historyCount == -1) {
                        throw RuntimeException("Failed to retrieve history count")
                    }
                }

                testId = -1
                doTest = false
                results = JSONArray()

                // Configure test settings
                Config.set("timing", if (runPortTests) "false" else "true")
                val serversStr = replayRepository.servers.toString()
                Config.set("server", serversStr.substring(1, serversStr.length - 1))
                val publicIP = replayRepository.serverRepository.getPublicIP("80")
                Config.set("publicIP", publicIP)
                Log.d(TAG, "public IP: $publicIP")

                if (publicIP == "-1") {
                    onError("No connection to server")
                    return@withContext false
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing test configuration", e)
                false
            }
        }
    }

    /**
     * Setup servers and certificates
     */
    private suspend fun setupServersAndCertificates(server: String, metadataServer: String?): Boolean {
        return withContext(Dispatchers.IO) {
            if (isNetworkUnavailable()) {
                onError("Network unavailable")
                return@withContext false
            }
            val result = replayRepository.setupServersAndCertificates(server, metadataServer, 1, false)
            result.isSuccess
        }
    }

    /**
     * Check network availability
     */
    private fun isNetworkUnavailable(): Boolean {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (connectivityManager != null) {
            val activeNetwork = connectivityManager.activeNetwork ?: return true
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities != null) {
                return !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return true
    }

    /**
     * Run test for a specific app - Core implementation from ReplayViewModel
     */
    private suspend fun runTestForApp(
        app: ApplicationBean,
        runPortTests: Boolean,
        carrier: String?
    ): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Running test for app: ${app.name}")
//                onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.pending) ?: "Waiting to start"))

                // Check network before starting
                if (isNetworkUnavailable()) {
                    onError(applicationContext.getString(R.string.text_network_error) ?: "No network available")
                    return@withContext TestResult.ERROR
                }

                // Setup servers if needed
                if (!setupServersAndCertificates(serverDisplay!!, null)) {
                    onError(applicationContext.getString(R.string.server_unavailable) ?: "Server unavailable")
                    return@withContext TestResult.ERROR
                }

                // Run the test similar to ReplayViewModel.runTest
                val rerun = runSingleTest(app, false, runPortTests, carrier)

//                if (!rerun) {
//                    return@withContext TestResult.ERROR
//                }

                // Run confirmation test if needed
                if (rerun && confirmationReplays) {
                    onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.confirmation_replay)))
                    runSingleTest(app, true, runPortTests, carrier)
                }

                // Cleanup
                replayRepository.clearTimers()
                replayRepository.closeWebSocketConnections()

                // Determine result based on app status
                when {
                    app.status.contains(applicationContext.getString(R.string.has_diff)) || app.status.contains("has diff") -> TestResult.DIFFERENTIATED
                    app.status.contains(applicationContext.getString(R.string.inconclusive)) || app.status.contains("inconclusive") -> TestResult.INCONCLUSIVE
                    app.status.contains(applicationContext.getString(R.string.no_diff)) || app.status.contains("no diff") -> TestResult.NOT_DIFFERENTIATED
                    app.status.contains(applicationContext.getString(R.string.error)) -> TestResult.ERROR
                    else -> TestResult.ERROR
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error running test for ${app.name}", e)
                TestResult.ERROR
            }
        }
    }

    /**
     * Run a single test iteration (extracted from ReplayViewModel.runTest)
     */
    private suspend fun runSingleTest(
        app: ApplicationBean,
        isConfirmation: Boolean,
        runPortTests: Boolean,
        carrier: String?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Flip coin to decide test order
                val types = if (Math.random() < 0.5) {
                    arrayOf("open", "random")
                } else {
                    arrayOf("random", "open")
                }

                var iteration = 1
                var portBlocked = false

                for (channel in types) {
                    if (!isRunning) return@withContext false

                    onIterationUpdate(iteration)
                    onStatusUpdate(Pair(app.name ?: "Unknown App", "Running $channel test"))

                    // Load app data for replay
                    val appData = replayRepository.loadAppDataForReplay(app, channel)

                    try {
                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.create_side_channel)))

                        // Create side channels
                        val (sideChannels, jitterBeans) = replayRepository.setupSideChannels(appData)

                        // Update history count
                        val currentHistoryCount = if (iteration == 1) {
                            historyCount = replayRepository.updateHistoryCount(historyCount)
                            app.historyCount = historyCount
                            historyCount
                        } else {
                            historyCount
                        }

                        if (randomID == null) {
                            Log.e(TAG, "randomID does not exist!")
                            setInconclusive(app, "No user ID")
                            return@withContext false
                        }

                        val endOfTest = channel.equals(types[types.size - 1], ignoreCase = true)
                        if (endOfTest) {
                            Log.i("Replay", "last replay running ${types[types.size - 1]}!")
                        }

                        // Check port accessibility for TCP tests
                        var replayPort = "80"
                        var ipThroughProxy = "127.0.0.1"
                        if (appData.isTCP) {
                            for (csp in appData.tcpCSPs) {
                                replayPort = csp.substring(csp.lastIndexOf('.') + 1)
                            }
                            val result = replayRepository.checkPortAccess(replayPort)
                            ipThroughProxy = result.first
                            if (!result.second) {
                                portBlocked = true
                                iteration++
                                continue
                            }
                        }

                        testId = if (channel.equals("open", ignoreCase = true)) 0 else 1

                        if (!isRunning) return@withContext false

                        /*
                         * Steps 1-4: Initiate test with server
                         */
                        onStatusUpdate(Pair(app.name ?: "Unknown App",  applicationContext.getString(R.string.ask4permission)))

                        // Initiate test with server
                        val timeSlicesResult = replayRepository.initiateTestWithServer(
                            sideChannels, appData, randomID!!, currentHistoryCount,
                            testId, endOfTest, doTest, ipThroughProxy
                        )

                        val numOfTimeSlices = timeSlicesResult.getOrElse {
                            setInconclusive(app, it.message ?: applicationContext.getString(R.string.error_unknown))
                            return@withContext false
                        }

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.receive_server_port_mapping)))

                        // Get port mappings
                        val (serverPortsMaps, udpReplayInfoBeans) = replayRepository.getPortMappingFromServer(sideChannels)

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.create_tcp_client)))

                        // Create TCP clients
                        val CSPairMappings = try {
                            replayRepository.createTCPClients(appData, serverPortsMaps)
                        } catch (e: Exception) {
                            setInconclusive(app, applicationContext.getString(R.string.error_no_connection))
                            return@withContext false
                        }

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.create_udp_client)))

                        // Create UDP clients
                        val udpPortMappings = replayRepository.createUDPClients(appData)

                        if (!isRunning) return@withContext false

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.run_notf)))

                        // Start notifiers
                        val notifiers = ArrayList<CombinedNotifierThread>()
                        val notfThreads = ArrayList<Thread>()
                        for (sc in sideChannels) {
                            val notifier = sc.notifierCreator(udpReplayInfoBeans[sc.id])
                            notifiers.add(notifier)
                            val notfThread = Thread(notifier)
                            notfThread.start()
                            notfThreads.add(notfThread)
                        }

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.run_receiver)))

                        // Start receivers
                        val analyzerTasks = ArrayList<CombinedAnalyzerTask>()
                        val analyzerTimers = ArrayList<Timer>()
                        val receivers = ArrayList<CombinedReceiverThread>()
                        val rThreads = ArrayList<Thread>()

                        for (sc in sideChannels) {
                            val analyzerTask = CombinedAnalyzerTask(
                                app.time / 2.0, appData.isTCP, numOfTimeSlices[sc.id], runPortTests
                            )
                            val analyzerTimer = Timer(true)
                            analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.interval)
                            analyzerTasks.add(analyzerTask)
                            analyzerTimers.add(analyzerTimer)

                            val receiver = CombinedReceiverThread(
                                udpReplayInfoBeans[sc.id], jitterBeans[sc.id], analyzerTask
                            )
                            receivers.add(receiver)
                            val rThread = Thread(receiver)
                            rThread.start()
                            rThreads.add(rThread)
                        }

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.run_sender)))

                        // Extract UDP server mappings
                        val udpServerMappings = ArrayList<HashMap<String, HashMap<String, ServerInstance>>>()
                        for (m in serverPortsMaps) {
                            udpServerMappings.add(m["udp"]!!)
                        }

                        // Set up queue for sending packets
                        val queue = CombinedQueue(
                            appData.q, jitterBeans, analyzerTasks,
                            if (runPortTests) Consts.REPLAY_PORT_TIMEOUT else Consts.REPLAY_APP_TIMEOUT
                        )

                        // Run packet queue
                        val duration = updateUIBean?.let {
                            replayRepository.runPacketQueue(
                                queue, types.size, CSPairMappings,
                                udpPortMappings, udpReplayInfoBeans, udpServerMappings,
                                it, coroutineContext
                            )
                        } ?: 0.0

                        // Stop all timers and threads
                        queue.stopTimers()
                        for (t in analyzerTimers) {
                            t.cancel()
                        }
                        for (n in notifiers) {
                            n.doneSending = true
                        }
                        for (t in notfThreads) {
                            t.join()
                        }
                        for (r in receivers) {
                            r.keepRunning = false
                        }
                        for (t in rThreads) {
                            t.join()
                        }

                        if (!isRunning) return@withContext false

                        onStatusUpdate(Pair(app.name ?: "Unknown App", applicationContext.getString(R.string.processing_results)))

                        // Process test results
                        replayRepository.processTestResults(sideChannels, duration, analyzerTasks)

                        // Set random throughput for port tests
                        if (runPortTests && channel.equals("random", ignoreCase = true)) {
                            app.randomThroughput = analyzerTasks[0].avgThroughput
                        }

                        // Cleanup resources
                        replayRepository.cleanupResources(sideChannels, CSPairMappings, udpPortMappings, appData)

                        iteration++

                    } catch (e: InterruptedException) {
                        Log.w(TAG, "Test interrupted!", e)
                        return@withContext false
                    } catch (e: IOException) {
                        Log.e(TAG, "IO issue with server", e)
                        setInconclusive(app, applicationContext.getString(R.string.error_no_connection))
                        return@withContext false
                    }
                }

                // Get and process results
                return@withContext getResults(app, portBlocked, isConfirmation, runPortTests, carrier)

            } catch (e: Exception) {
                Log.e(TAG, "Error in runSingleTest", e)
                false
            }
        }
    }

    /**
     * Set app status to inconclusive
     */
    private fun setInconclusive(app: ApplicationBean, msg: String) {
        if (!inconclusiveApps.contains(app)) {
            inconclusiveApps.add(app)
        }
        app.error = msg
        app.status = applicationContext.getString(R.string.inconclusive) ?: "Inconclusive"
    }

    /**
     * Get and process results (extracted from ReplayViewModel.getResults)
     */
    private suspend fun getResults(
        app: ApplicationBean,
        portBlocked: Boolean,
        isConfirmation: Boolean,
        runPortTests: Boolean,
        carrier: String?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var currentPortBlocked = portBlocked
                var response = JSONObject()

                if (!isRunning) return@withContext false

                if (isNetworkUnavailable()) {
                    onError("Network unavailable")
                    return@withContext false
                }

                if (!currentPortBlocked) {
                    // Request analysis
                    val analysisResult = randomID?.let {
                        replayRepository.requestAnalysis(it, app.historyCount)
                    }

                    if (analysisResult?.isFailure == true) {
                        setInconclusive(app, analysisResult.exceptionOrNull()?.message ?: "Analysis failed")
                        return@withContext false
                    }

                    onStatusUpdate(Pair(app.name ?: "Unknown App", "Waiting for results"))

                    if (app.historyCount < 0) {
                        Log.e(TAG, "historyCount value not correct!")
                        return@withContext false
                    }

                    if (!isRunning) return@withContext false

                    // Retrieve results
                    val resultsRetrieved = randomID?.let {
                        replayRepository.retrieveResults(it, app.historyCount, runPortTests)
                    }

                    if (resultsRetrieved?.isFailure == true) {
                        setInconclusive(app, resultsRetrieved.exceptionOrNull()?.message ?: "Failed to retrieve results")
                        return@withContext false
                    }

                    val retrievedResults = resultsRetrieved?.getOrNull() ?: emptyList()

                    if (retrievedResults.isEmpty() && runPortTests) {
                        currentPortBlocked = true
                        Log.i(TAG, "Can't retrieve result, port blocked")
                    } else if (retrievedResults.isEmpty()) {
                        setInconclusive(app, "Not all TCP packets sent")
                        return@withContext false
                    } else {
                        response = retrievedResults[0].getJSONObject("response")
                    }
                }

                // Analyze results
                val analysisResult = randomID?.let {
                    replayRepository.analyzeResults(
                        response = response,
                        randomID = it,
                        historyCount = app.historyCount,
                        dataFile = app.dataFile,
                        runPortTests = runPortTests,
                        a_threshold = a_threshold,
                        ks2pvalue_threshold = ks2pvalue_threshold,
                        randomThroughput = app.randomThroughput
                    )
                }

                if (analysisResult?.isFailure == true) {
                    setInconclusive(app, analysisResult.exceptionOrNull()?.message ?: "Result analysis failed")
                    return@withContext false
                }

                val analysis = analysisResult?.getOrNull()!!

                // Process results
                return@withContext processResults(app, analysis, isConfirmation, false, carrier ?: "")

            } catch (e: Exception) {
                Log.e(TAG, "Error during results processing", e)
                false
            }
        }
    }

    /**
     * Process test results (extracted from ReplayViewModel.processResults)
     */
    private suspend fun processResults(
        app: ApplicationBean,
        analysis: ReplayRepository.ResultAnalysis,
        isConfirmation: Boolean,
        isTomography: Boolean,
        carrier: String
    ): Boolean {
        val current = applicationContext.resources?.configuration?.locale
        val country = current?.country

        // Determine if confirmation test is needed
        val needsConfirmation = (analysis.inconclusive || analysis.differentiation) &&
                confirmationReplays &&
                !isConfirmation &&
                !isTomography

        if (needsConfirmation) {
            onStatusUpdate(Pair(app.name ?: "Unknown App", "Needs confirmation"))
            return true
        }

        // Set app properties
        app.area_test = analysis.area_test
        app.ks2pVal = analysis.ks2pVal
        app.ks2pRatio = analysis.ks2RatioTest
        app.originalThroughput = analysis.xputOriginal
        app.randomThroughput = analysis.xputTest

        // Set status based on analysis
        if (isTomography) {
            app.differentiationNetwork = if (analysis.differentiation) carrier else ""
            app.status = if (analysis.differentiation) "Tomo Success" else "Tomo Failed"
        } else if (analysis.inconclusive) {
            app.status = "Inconclusive"
            inconclusiveApps.add(app)
        } else if (analysis.differentiation) {
            app.status = "Has Differentiation"
            app.error = analysis.errorMessage

            // Add country-specific alert buttons
            if (country == "FR") {
                app.arcepNeedsAlerting = true
            } else if (country == "US") {
                app.isAlertFCC = true
            }
            diffApps.add(app)
        } else {
            app.status = applicationContext.getString(R.string.no_diff) ?: "No differentiation"
        }

        onStatusUpdate(Pair(app.name ?: "Unknown App", app.status))

        // Create result JSON for storage
        val response = analysis.response
        response.put("isPort", false) // Assuming not port tests for background
        response.put("appName", app.name)
        response.put("appImage", app.image)
        response.put("date", Date().time)
        response.put("areaThreshold", a_threshold.toDouble() / 100)
        response.put("ks2pThreshold", ks2pvalue_threshold.toDouble() / 100)
        response.put("isIPv6", replayRepository.isIPv6())
        response.put("server", serverDisplay)
        response.put("carrier", carrier)

        val saveStatus = when {
            isTomography -> if (app.differentiationNetwork == "") "tomo failed" else "tomo succ"
            analysis.inconclusive -> "inconclusive"
            analysis.differentiation -> "has diff"
            else -> "no diff"
        }
        response.put("status", saveStatus)

        if (isTomography) {
            response.put("tomographyNetwork", app.differentiationNetwork)
        }

        results?.put(response)

        return false // No confirmation needed
    }

    /**
     * Cancel the running tests
     */
    fun cancel() {
        Log.d(TAG, "Cancelling background tests")
        isRunning = false
        job?.cancel()
    }

    enum class TestResult {
        DIFFERENTIATED,
        NOT_DIFFERENTIATED,
        INCONCLUSIVE,
        ERROR
    }

    companion object {
        private const val TAG = "BackgroundTestRunner"
    }
}