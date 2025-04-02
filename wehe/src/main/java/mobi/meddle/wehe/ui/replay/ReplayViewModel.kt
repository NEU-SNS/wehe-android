package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.ContactsContract.ProviderStatus.STATUS
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.preference.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.NonCancellable.isCancelled
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.CombinedAnalyzerTask
import mobi.meddle.wehe.combined.CombinedNotifierThread
import mobi.meddle.wehe.combined.CombinedQueue
import mobi.meddle.wehe.combined.CombinedReceiverThread
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.bean.ServerInstance
import mobi.meddle.wehe.data.bean.UpdateUIBean
import mobi.meddle.wehe.util.Config
import mobi.meddle.wehe.util.RandomString
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Date
import java.util.Objects
import java.util.Timer
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class ReplayViewModel @Inject constructor(application : Application, private val repository: ReplayRepository) : AndroidViewModel(application) {
    // Methods for managing replayOngoing state
    private val _isReplayOngoing = MutableLiveData<Boolean>(false)
    val isReplayOngoing: LiveData<Boolean> = _isReplayOngoing
    private var selectedApps: ArrayList<ApplicationBean>? = null // Apps to run
    val diffApps: ArrayList<ApplicationBean> = ArrayList() // Apps with differentiation
    val inconclusiveApps: ArrayList<ApplicationBean> = ArrayList()

    private var runPortTests: Boolean = false
    var carrier: String? = null // Carrier to display in results

    // Tomography test flag
    var isTomography: Boolean = false // True if tomography test, false if normal test

    // LiveData for UI events
    private val _statusUpdateEvent = MutableLiveData<Pair<String, String>>()
    val statusUpdateEvent: LiveData<Pair<String, String>> = _statusUpdateEvent
    private val _progressUpdateEvent = MutableLiveData<Int>()
    val progressUpdateEvent: LiveData<Int> = _progressUpdateEvent
    private val _progressCompleteEvent = MutableLiveData<Int>()
    val progressCompleteEvent: LiveData<Int> = _progressCompleteEvent
    private val _toastEvent = MutableLiveData<String>()
    val toastEvent: LiveData<String> = _toastEvent
    private val _dialogEvent = MutableLiveData<Triple<String, String, Boolean>>()
    val dialogEvent: LiveData<Triple<String, String, Boolean>> = _dialogEvent
    private val _showRerunTomoButtonsEvent = MutableLiveData<Boolean>()
    val showRerunTomoButtonsEvent: LiveData<Boolean> = _showRerunTomoButtonsEvent

    // Coroutine jobs
    private var job: Job? = null
    private val uiUpdateJobs = ArrayList<Job>()

    // Activity reference for context
    private var applicationContext: Context = application.applicationContext

    // Data for the app being tested
    private var appData: CombinedAppJSONInfoBean? = null
    private var app: ApplicationBean? = null
    private var metadataServer: String? = null
    private var updateUIBean: UpdateUIBean? = null
    private var doTest = false // Add a tail for testing data if true

    // Test configuration
    private var confirmationReplays = false
    private var useDefaultThresholds = false
    private var a_threshold = 0
    private var ks2pvalue_threshold = 0
    private var settings: SharedPreferences? = null

    // User and test identification
    private var randomID: String? = null // Unique user ID for certain device
    private var historyCount = 0 // Test number
    private var testId = 0 // Replay number in a test
    private var results: JSONArray? = null // Results containing apps or the port arrays
    private var serverDisplay: String? = null // preferred server entered by user

    /**
     * Start the trace run in a coroutine
     */
    fun execute() {
        _isReplayOngoing.value = true

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                doInBackground()
            } catch (e: Exception) {
                Log.e("TraceRun", "Error while running tests (in the coroutine)", e)
            } finally {
                withContext(Dispatchers.Main) {
                    _isReplayOngoing.value = false
                }
            }
        }
    }

    /**
     * Cancel the running coroutine
     */
    fun cancel() {
        job?.cancel()

        // Cancel all UI update jobs
        for (job in uiUpdateJobs) {
            job.cancel()
        }
        uiUpdateJobs.clear()

        _isReplayOngoing.value = false
        Log.i("Replay", "Forced exit")
    }

    /**
     * Initialize the ViewModel with application data
     */
    fun initializeData(
        runPortTests: Boolean,
        carrier: String?,
        selectedApps: ArrayList<ApplicationBean>?,
        context: Context
    ) {
        this.runPortTests = runPortTests
        this.carrier = carrier
        this.selectedApps = selectedApps
        this.applicationContext = context

        selectedApps?.let {
            for (app in it) {
                app.status = context.getString(R.string.pending) ?: "Waiting to start"
            }
        }
    }

    /**
     * Check network availability
     */
    fun isNetworkUnavailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check if connectivityManager is not null
        if (connectivityManager != null) {
            // Get the active network
            val activeNetwork = connectivityManager.activeNetwork ?: return true
            // If there is no active network, the network is unavailable

            // Get network capabilities and check for connectivity
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork)
            // Check if the network is connected to Wi-Fi or mobile data
            if (networkCapabilities != null) {
                // Return true if the network is connected to the internet (either Wi-Fi or mobile data)
                return !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        // If the connectivityManager is null, consider the network unavailable
        return true
    }

    /**
     * Show "No Network" dialog event
     */
    fun showNoNetworkDialog() {
        _dialogEvent.value = Triple(
            applicationContext.getString(R.string.network_error) ?: "Network Error",
            applicationContext.getString(R.string.text_network_error) ?: "No network available",
            true
        )
    }

    /**
     * Prepare for rerun tests
     */
    fun prepareRerunTests(isRunningDifferentiation: Boolean) {
        val selectedApps = if (isRunningDifferentiation) {
            ArrayList(diffApps)
        } else {
            ArrayList(inconclusiveApps)
        }

        this.selectedApps = selectedApps
        isTomography = false

        for (app in selectedApps) {
            app.isTomography = false
            app.arcepNeedsAlerting = false
            app.isAlertFCC = false
            app.status = applicationContext.getString(R.string.pending) ?: "Pending"
        }

        inconclusiveApps.clear()
        diffApps.clear()
        execute()
    }

    /**
     * Prepare for tomography tests
     */
    fun prepareTomographyTests() {
        isTomography = true
        selectedApps = ArrayList(diffApps)

        for (app in selectedApps!!) {
            app.isTomography = true
            app.arcepNeedsAlerting = false
            app.isAlertFCC = false
            app.status = applicationContext.getString(R.string.pending) ?: "Pending"
        }

        execute()
    }

    /**
     * Update app status
     */
    private suspend fun updateAppStatus(appName: String, status: String, iteration: Int = -1) {
        var status = status
        if (iteration != -1) {
           status = "$iteration/2 $status"
        }
        if (app?.name == appName) {
            app?.status = status
        } else {
            selectedApps?.forEach { app ->
                if (app.name == appName) {
                    app.status = status
                }
            }
        }
        withContext(Dispatchers.Main) {
            _statusUpdateEvent.value = Pair(appName, status)
        }
    }

    /**
     * Update UI progress
     */
    private suspend fun updateProgress(progress: Int) {
        withContext(Dispatchers.Main) {
            _progressUpdateEvent.value = progress
        }
    }

    /**
     * Complete progress for an iteration
     */
    private suspend fun finishProgress(iteration: Int) {
        withContext(Dispatchers.Main) {
            _progressCompleteEvent.value = iteration
        }
    }

    /**
     * Show toast message
     */
    private fun showToast(message: String) {
        _toastEvent.value = message
    }

    /**
     * Show dialog
     */
    private suspend fun showDialog(title: String, message: String, exitReplays: Boolean) {
        withContext(Dispatchers.Main) {
            _dialogEvent.value = Triple(title, message, exitReplays)
        }
    }

    /**
     * Reset the progress bar to 0
     */
    private suspend fun clearProgressBar() {
        updateUIBean?.clearProgress()
        withContext(Dispatchers.Main) {
            _progressCompleteEvent.value = 0
        }
    }

    /**
     * Signal to show rerun and tomography buttons
     */
    private suspend fun showRerunTomoButtons() {
        withContext(Dispatchers.Main) {
            _showRerunTomoButtonsEvent.value = true
        }
    }

    /**
     * This method begins process to run tests.
     * Step 1: Initialize several variables.
     * Step 2: Run tests.
     * Step 3: Save results.
     */
    private suspend fun doInBackground() {
        // Set each app's status to "Waiting"
        selectedApps?.let {
            for (app in it) {
                app.status = applicationContext.resources?.getString(R.string.pending) ?: "Waiting to start"
                updateAppStatus(app.name, app.status)
            }
        }

        // Keep checking if the job was cancelled
        if (!isActive) {
            return
        }

        if (isNetworkUnavailable(applicationContext)) {
            showNoNetworkDialog()
            return
        }

        /*
         * Step 1: Initialize several variables.
         */
        updateUIBean = UpdateUIBean()
        Config.readConfigFile(Consts.CONFIG_FILE, applicationContext)

        // Get settings from SettingsFragment
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

        // Get from preferences from SettingsFragment
        sharedPrefs.let {
            confirmationReplays = it.getBoolean("pref_multiple_tests", true)
            useDefaultThresholds = it.getBoolean("pref_switch", true)
            a_threshold = Objects.requireNonNull(
                it.getString("pref_threshold_area", "10")
            )?.toInt() ?: 10
            ks2pvalue_threshold = Objects.requireNonNull(
                it.getString("pref_threshold_ks2p", "5")
            )?.toInt() ?: 5
        }

        serverDisplay =
            if (isTomography) { // Need to use MLab servers for tomography tests
                Consts.DEFAULT_SERVER
            } else {
                sharedPrefs.getString(
                    applicationContext.getString(R.string.pref_server_key),
                    Consts.DEFAULT_SERVER
                ) // Get server from SettingsFragment
            }

        // Metadata here is user's network type device used geolocation if permitted etc
        metadataServer = Consts.METADATA_SERVER
        if (!setupServersAndCertificates(serverDisplay!!, metadataServer)) {
            return
        }

        // Generate or retrieve an id for this phone
        val hasID = sharedPrefs?.getBoolean("hasID", false) ?: false
        if (!hasID) {
            randomID = RandomString(10).nextString()
            sharedPrefs?.edit()?.apply {
                putBoolean("hasID", true)
                putString("ID", randomID)
                apply()
            }
        } else {
            randomID = sharedPrefs?.getString("ID", null)
        }

        // To get historyCount
        settings = applicationContext.getSharedPreferences(STATUS, Context.MODE_PRIVATE)

        // Generate or retrieve a historyCount for this phone
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
            if (historyCount == -1) { // Check if retrieve historyCount succeeded
                throw RuntimeException("Failed to retrieve history count")
            }
        }

        testId = -1
        doTest = false
        results = JSONArray() // Init results

        // Timing allows replays to be run with the same timing as when they were recorded
        // Port tests try to run as fast as possible, so there is no timing for them
        Config.set("timing", if (runPortTests) "false" else "true")
        val serversStr = repository.servers.toString()
        Config.set("server", serversStr.substring(1, serversStr.length - 1))
        val publicIP = repository.serverRepository.getPublicIP("80") // Get user's IP address
        Config.set("publicIP", publicIP)
        Log.d("Replay", "public IP: $publicIP")

        // If cannot connect to server, display an error and stop tests
        if (publicIP == "-1") {
            showDialog(
                applicationContext.getString(R.string.simple_error),
                applicationContext.getString(R.string.error_no_connection),
                true
            )
            return
        }

        if (!isActive) {
            return
        }

        if (isNetworkUnavailable(applicationContext)) {
            showNoNetworkDialog()
            return
        }

        /*
         * Step 2: Run tests.
         */
        var firstApp = true
        selectedApps?.let { apps ->
            for (app in apps) {
                if (!isActive) {
                    return@let
                }

                if (!(firstApp && repository.isMlabServerUsed())) {
                    if (!setupServersAndCertificates(serverDisplay!!, null)) {
                        return@let
                    }
                }

                this.app = app // Set the app to run test for
                this.app!!.arcepNeedsAlerting = false
                this.app!!.isAlertFCC = false

                if (!isActive) {
                    return@let
                }

                // Make sure progress bar is clear
                clearProgressBar()
                updateProgress(0) // Make progress bar visible
                val rerun = runTest(false) // Run the test on this.app

                if (!isTomography && rerun) {
                    // Run confirmation test if confirmation tests are switched on in Settings and
                    // First test was inconclusive or had differentiation
                    // Don't run confirmation tests for tomography tests
                    clearProgressBar()
                    updateProgress(0) // Make progress bar visible
                    runTest(true)
                }

//                // Clean up
                repository.closeWebSocketConnections()

                repository.clearTimers()

//                for (t in timers) {
//                    t.cancel()
//                }
//                timers.clear()

                // Cancel all UI update jobs
                for (job in uiUpdateJobs) {
                    job.cancel()
                }
                uiUpdateJobs.clear()

                firstApp = false

                if (!isActive) {
                    return@let
                }
            }
        }

        /*
         * Step 3: Save results.
         */
        if ((results?.length() ?: 0) > 0) {
            Log.i("Result Channel", "Storing results")
            repository.saveResults(results, sharedPrefs)
        }
        if (!isActive) {
            return
        }

        showDialog(
            applicationContext.getString(R.string.replay_finished_title),
            "",
            false
        )

        // If there are apps with differentiation or inconclusive results, show rerun buttons
        if (diffApps.size != 0 || inconclusiveApps.size != 0) {
            showRerunTomoButtons()
        }
        Log.i("Result Channel", "Exiting normally")
    }

    /**
     * Gets IPs of server and metadata server
     *
     * @param server The hostname of the server to connect to
     * @param metadataServer The hostname of the metadata server to connect to
     * @return true if everything properly sets up; false otherwise
     */
    private suspend fun setupServersAndCertificates(server: String, metadataServer: String?): Boolean {
        val numTests = if (isTomography) Consts.NUM_TOMOGRAPHY_TESTS else 1
        if (repository.isNetworkUnavailable(applicationContext)) {
            showNoNetworkDialog()
            return false
        }
        val result = repository.setupServersAndCertificates(server, metadataServer, numTests, isTomography)
        return result.isSuccess
    }

    /**
     * Sets the status of the app to be inconclusive if there is an error
     */
    private suspend fun setInconclusive(msg: String) {
        if (!inconclusiveApps.contains(app)) {
            app?.let { inconclusiveApps.add(it) }
        }
        app?.error = msg
        app?.let {
            updateAppStatus(it.name, applicationContext.getString(R.string.inconclusive))
        }
    }

    private suspend fun runTest(isConfirmation: Boolean): Boolean {
        /*
         * Step A: Flip a coin to decide which replay type to run first.
         */
        //"random" test for ports is port 443
        val types = if (Math.random() < 0.5) {
            arrayOf("open", "random")
        } else {
            arrayOf("random", "open")
        }

        /*
         * Step B: Run replays.
         */
        var iteration = 1
        var portBlocked = false
        for (channel in types) {
            logWebSocketConnections("Before running test ")

            if (!isActive) { //user cancels running tests
                return false
            }
            if (isNetworkUnavailable(applicationContext)) {
                showNoNetworkDialog()
                return false
            }

            /*
             * Step 0: Initialize variables.
             */
            // Load appropriate data based on replay type
            this.appData = repository.loadAppDataForReplay(app!!, channel)

            try {
                updateAppStatus(app!!.name, applicationContext.getString(R.string.create_side_channel), iteration)

                // Create side channels for communication with server
                val (sideChannels, jitterBeans) = repository.setupSideChannels(appData!!)

                // Increase history count only once during the run of a single test
                val currentHistoryCount = if (iteration == 1) {
                    historyCount = repository.updateHistoryCount(historyCount)
                    app!!.historyCount = historyCount
                    historyCount
                } else {
                    historyCount
                }

                // Check if user ID exists
                if (randomID == null) {
                    Log.e("RecordReplay", "randomID does not exist!")
                    setInconclusive(applicationContext.getString(R.string.error_no_user_id))
                    return false
                }

                // Determine if this is the last replay in the test
                val endOfTest = channel.equals(types[types.size - 1], ignoreCase = true)
                if (endOfTest) {
                    Log.i("Replay", "last replay running ${types[types.size - 1]}!")
                }

                // Check port accessibility for TCP tests
                var replayPort = "80"
                var ipThroughProxy = "127.0.0.1"
                if (appData!!.isTCP) {
                    for (csp in appData!!.tcpCSPs) {
                        replayPort = csp.substring(csp.lastIndexOf('.') + 1)
                    }
                    val result = repository.checkPortAccess(replayPort)
                    ipThroughProxy = result.first
                    // Check if port is accessible
                    if (!result.second) {
                        portBlocked = true
                        iteration++
                        continue
                    }
                }

                // Set test ID based on channel type
                testId = if (channel.equals("open", ignoreCase = true)) 0 else 1

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable(applicationContext)) {
                    showNoNetworkDialog()
                    return false
                }

                /*
                 * Steps 1-4: Initiate test with server
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.ask4permission), iteration)

                // Communicate with server to set up test
                val timeSlicesResult = repository.initiateTestWithServer(
                    sideChannels,
                    appData!!,
                    randomID,
                    currentHistoryCount,
                    testId,
                    endOfTest,
                    doTest,
                    ipThroughProxy
                )

                // Handle permission errors
                val numOfTimeSlices = timeSlicesResult.getOrElse {
                    setInconclusive(it.message ?: applicationContext.getString(R.string.error_unknown))
                    return false
                }

                /*
                 * Step 5: Get port mapping from server.
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.receive_server_port_mapping), iteration)

                // Get port mappings and UDP info from server
                val (serverPortsMaps, udpReplayInfoBeans) = repository.getPortMappingFromServer(sideChannels)

                /*
                 * Step 6: Create TCP and UDP clients
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.create_tcp_client), iteration)

                // Create TCP clients from CSPairs
                val CSPairMappings = try {
                    repository.createTCPClients(appData!!, serverPortsMaps)
                } catch (e: Exception) {
                    setInconclusive(applicationContext.getString(R.string.error_no_connection))
                    return false
                }

                updateAppStatus(app!!.name, applicationContext.getString(R.string.create_udp_client), iteration)

                // Create UDP clients
                val udpPortMappings = repository.createUDPClients(appData!!)

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable(applicationContext)) {
                    showNoNetworkDialog()
                    return false
                }

                /*
                 * Step 7: Start notifier(s) for UDP.
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.run_notf), iteration)

                val notifiers = ArrayList<CombinedNotifierThread>()
                val notfThreads = ArrayList<Thread>()
                for (sc in sideChannels) {
                    val notifier = sc.notifierCreator(udpReplayInfoBeans[sc.id])
                    notifiers.add(notifier)
                    val notfThread = Thread(notifier)
                    notfThread.start()
                    notfThreads.add(notfThread)
                }

                /*
                 * Step 8: Start receiver(s) to log throughputs on a given interval.
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.run_receiver), iteration)

                val analyzerTasks = ArrayList<CombinedAnalyzerTask>()
                val analyzerTimers = ArrayList<Timer>()
                val receivers = ArrayList<CombinedReceiverThread>()
                val rThreads = ArrayList<Thread>()
                for (sc in sideChannels) {
                    val analyzerTask = CombinedAnalyzerTask(
                        app!!.time / 2.0,
                        appData!!.isTCP, numOfTimeSlices[sc.id], runPortTests
                    ) //throughput logger
                    val analyzerTimer = Timer(true) //timer to log throughputs on interval
                    analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.interval)
                    analyzerTasks.add(analyzerTask)
                    analyzerTimers.add(analyzerTimer)

                    val receiver = CombinedReceiverThread(
                        udpReplayInfoBeans[sc.id], jitterBeans[sc.id], analyzerTask
                    ) //receiver for udp
                    receivers.add(receiver)
                    val rThread = Thread(receiver)
                    rThread.start()
                    rThreads.add(rThread)
                }

                /*
                 * Step 8.5: Start progress bar.
                 */
                val finalIteration = iteration
                val uiUpdateJob = CoroutineScope(Dispatchers.Default).launch {
                    currentCoroutineContext()[CoroutineName]?.let {
                        Thread.currentThread().name = "UIUpdateCoroutine"
                    }

                    if (finalIteration == 1) {
                        clearProgressBar()
                    }

                    while (updateUIBean!!.progress < 100 && isActive) {
                        updateProgress(updateUIBean!!.progress)
                        delay(500)
                    }
                }
                uiUpdateJobs.add(uiUpdateJob)

                /*
                 * Step 9: Send packets to server(s).
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.run_sender), iteration)

                // Extract UDP server mappings
                val udpServerMappings = ArrayList<HashMap<String, HashMap<String, ServerInstance>>>()
                for (m in serverPortsMaps) {
                    udpServerMappings.add(m["udp"]!!)
                }

                // Set up queue for sending packets
                val queue = CombinedQueue(
                    appData!!.q, jitterBeans, analyzerTasks,
                    if (runPortTests) Consts.REPLAY_PORT_TIMEOUT else Consts.REPLAY_APP_TIMEOUT
                )

                // Run packet queue and get duration
                val duration = updateUIBean?.let {
                    repository.runPacketQueue(
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

                // Update progress bar
                if (iteration == 1) { //make progress bar to 50%
                    finishProgress( 1)
                } else { //make progress bar to 100%
                    finishProgress(2)
                    Log.i("UpdateUI", "completed!")
                }

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable(applicationContext)) {
                    showNoNetworkDialog()
                    return false
                }

                /*
                 * Step 10-11: Process test results
                 */
                updateAppStatus(app!!.name, applicationContext.getString(R.string.send_done), iteration)

                // Send test results to server
                repository.processTestResults(sideChannels, duration, analyzerTasks)

                // Set average port 443 throughput for display if port being tested is blocked
                if (runPortTests && channel.equals("random", ignoreCase = true)) {
                    app!!.randomThroughput = analyzerTasks[0].avgThroughput
                }

                /*
                 * Step 12: Clean up resources
                 */
                // Close all connections
                repository.cleanupResources(sideChannels, CSPairMappings, udpPortMappings, appData!!)

                iteration++
            } catch (e: InterruptedException) {
                Log.w("Replay", "Replay interrupted!", e)
            } catch (e: IOException) {
                Log.e("Replay", "Some IO issue with server", e)
                setInconclusive(applicationContext.getString(R.string.error_no_connection))
                return false
            }
        }

        /*
         * Step C: Determine if there is differentiation.
         */
        return getResults(portBlocked, isConfirmation)
    }

    /**
     * Process test results
     *
     * @param analysis Result analysis from repository
     * @param isConfirmation Whether this is a confirmation test
     * @param isTomography Whether this is a tomography test
     * @param carrier Carrier name for tomography tests
     * @param results JSONArray for storing results
     * @return ResultState containing the status of the test
     */
    private suspend fun processResults(
        analysis: ReplayRepository.ResultAnalysis,
        isConfirmation: Boolean,
        isTomography: Boolean,
        carrier: String,
        results: JSONArray
    ): ResultState {
        val current = applicationContext.resources?.configuration?.locale
        val country = current?.country

        // Determine if confirmation test is needed
        val needsConfirmation = (analysis.inconclusive || analysis.differentiation) &&
                confirmationReplays &&
                !isConfirmation &&
                !isTomography

        if (needsConfirmation) {
            app?.let {
                applicationContext.getString(R.string.confirmation_replay)
                    ?.let { message -> updateAppStatus(it.name, message) }
            }
            return ResultState(
                needsConfirmation = true,
                portBlocked = analysis.portBlocked
            )
        }

        // Set app properties
        app?.let {
            // Set throughput values
            it.area_test = analysis.area_test
            it.ks2pVal = analysis.ks2pVal
            it.ks2pRatio = analysis.ks2RatioTest
            it.originalThroughput = analysis.xputOriginal
            it.randomThroughput = analysis.xputTest

            // Set status based on analysis
            if (isTomography) {
                it.differentiationNetwork = if (analysis.differentiation) carrier else ""
                it.status = if (analysis.differentiation)
                    applicationContext.getString(R.string.tomo_succ)
                else
                    applicationContext.getString(R.string.tomo_failed)
            } else if (analysis.inconclusive) {
                it.status = applicationContext.getString(R.string.inconclusive)
                inconclusiveApps.add(it)
            } else if (analysis.differentiation) {
                it.status = applicationContext.getString(R.string.has_diff)
                it.error = analysis.errorMessage

                // Add country-specific alert buttons
                if (country == "FR") {
                    it.arcepNeedsAlerting = true
                } else if (country == "US") {
                    it.isAlertFCC = true
                }
                diffApps.add(it)
            } else {
                it.status = applicationContext.getString(R.string.no_diff)
            }

            // Update UI
            updateAppStatus(it.name, it.status)
        }

        // Create result JSON for storage
        val response = analysis.response
        app?.let {
            // Add additional data to the response for storage
            response.put("isPort", runPortTests)
            response.put("appName", it.name)
            response.put("appImage", it.image)
            response.put("date", Date().time)
            response.put("areaThreshold", a_threshold.toDouble() / 100)
            response.put("ks2pThreshold", ks2pvalue_threshold.toDouble() / 100)
            response.put("isIPv6", repository.isIPv6())
            response.put("server", serverDisplay)
            response.put("carrier", carrier)

            // Set status for storage
            val saveStatus = when {
                isTomography -> if (it.differentiationNetwork == "") "tomo failed" else "tomo succ"
                analysis.inconclusive -> "inconclusive"
                analysis.differentiation -> "has diff"
                else -> "no diff"
            }
            response.put("status", saveStatus)

            if (isTomography) {
                response.put("tomographyNetwork", it.differentiationNetwork)
            }
        }

        results.put(response)

        return ResultState(
            needsConfirmation = false,
            portBlocked = analysis.portBlocked
        )
    }

    /**
     * Data class to hold result state
     */
    data class ResultState(
        val needsConfirmation: Boolean,
        val portBlocked: Boolean
    )

    /**
     * Determines if there is differentiation by analyzing test results.
     * Compares original test results with random test or port test results.
     *
     * @param portBlocked    true if a port in the port tests is blocked; false otherwise
     * @param isConfirmation true if confirmation test; false if original test
     * @return true if confirmation test needs to be run; false otherwise
     */
    private suspend fun getResults(portBlocked: Boolean, isConfirmation: Boolean): Boolean {
        var currentPortBlocked = portBlocked
        var response = JSONObject()

        try {
            // Check for cancellation or network unavailability
            if (!isActive) {
                return false
            }

            if (isNetworkUnavailable(applicationContext)) {
                showNoNetworkDialog()
                return false
            }

            logWebSocketConnections()

            // Skip analysis request if port is blocked
            if (!currentPortBlocked) {
                // Request analysis from servers
                val analysisResult = randomID?.let { repository.requestAnalysis(it, app!!.historyCount) }

                if (analysisResult?.isFailure == true) {
                    val errorMsg = analysisResult.exceptionOrNull()?.message
                        ?: applicationContext.getString(R.string.error_analysis_fail)
                    errorMsg?.let { setInconclusive(it) }
                    return false
                }

                applicationContext.getString(R.string.waiting)
                    ?.let { updateAppStatus(app!!.name, it) }

                // Sanity check
                if (app!!.historyCount < 0) {
                    Log.e("Result Channel", "historyCount value not correct!")
                    return false
                }

                // Check for cancellation or network unavailability again
                if (isCancelled) {
                    return false
                }

                if (isNetworkUnavailable(applicationContext)) {
                    showNoNetworkDialog()
                    return false
                }

                // Retrieve results from servers
                val resultsRetrieved =
                    randomID?.let { repository.retrieveResults(it, app!!.historyCount, runPortTests) }

                if (resultsRetrieved != null) {
                    if (resultsRetrieved.isFailure) {
                        val errorMsg = resultsRetrieved.exceptionOrNull()?.message
                            ?: applicationContext.getString(R.string.error_analysis_fail)
                        errorMsg?.let { setInconclusive(it) }
                        return false
                    }
                }

                val retrievedResults = resultsRetrieved?.getOrNull() ?: emptyList()

                // Check if we couldn't retrieve results and it's a port test (port blocked)
                if (retrievedResults.isEmpty() && runPortTests) {
                    currentPortBlocked = true
                    Log.i("Result Channel", "Can't retrieve result, port blocked")
                } else if (retrievedResults.isEmpty()) {
                    setInconclusive(applicationContext.getString(R.string.not_all_tcp_sent_text))
                    return false
                } else {
                    // Get response from first result if we have results
                    response = retrievedResults[0].getJSONObject("response")
                }
            }

            // Analyze results
            val analysisResult = randomID?.let {
                repository.analyzeResults(
                    response = response,
                    randomID = it,
                    historyCount = app!!.historyCount,
                    appName = app!!.name,
                    dataFile = app!!.dataFile,
                    runPortTests = runPortTests,
                    a_threshold = a_threshold,
                    ks2pvalue_threshold = ks2pvalue_threshold,
                    randomThroughput = app!!.randomThroughput
                )
            }

            if (analysisResult?.isFailure == true) {
                val errorMsg = analysisResult.exceptionOrNull()?.message
                    ?: applicationContext.getString(R.string.error_result)
                errorMsg?.let { setInconclusive(it) }
                return false
            }

            val analysis = analysisResult?.getOrNull()!!

            // Process results and determine if confirmation test is needed
            val resultState = carrier?.let {
                processResults(
                    analysis = analysis,
                    isConfirmation = isConfirmation,
                    isTomography = isTomography,
                    carrier = it,
                    results = results!!
                )
            }

            // Return whether confirmation test is needed
            if (resultState != null) {
                return resultState.needsConfirmation
            }

            return true

        } catch (e: Exception) {
            Log.e("Result Channel", "Error during results processing", e)
            return false
        }
    }

    /**
     * Logs the status of WebSocket connections
     */
    private fun logWebSocketConnections(prefix: String = "") {

        for ((id, w) in repository.wsConns.withIndex()) {
            if (w != null) {
                Log.d(
                    "WebSocket", (prefix + "WebSocket (id: " + id + ") connectivity check: "
                            + (if (w.isOpen) "CONNECTED" else "CLOSED"))
                )
            }
        }
    }
}