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
import mobi.meddle.wehe.BuildConfig
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.CTCPClient
import mobi.meddle.wehe.combined.CUDPClient
import mobi.meddle.wehe.combined.CombinedAnalyzerTask
import mobi.meddle.wehe.combined.CombinedNotifierThread
import mobi.meddle.wehe.combined.CombinedQueue
import mobi.meddle.wehe.combined.CombinedReceiverThread
import mobi.meddle.wehe.combined.CombinedSideChannel
import mobi.meddle.wehe.combined.WebSocketConnection
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.bean.JitterBean
import mobi.meddle.wehe.data.bean.RequestSet
import mobi.meddle.wehe.data.bean.ServerInstance
import mobi.meddle.wehe.data.bean.UDPReplayInfoBean
import mobi.meddle.wehe.data.bean.UpdateUIBean
import mobi.meddle.wehe.util.Config
import mobi.meddle.wehe.util.RandomString
import mobi.meddle.wehe.util.UtilsManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.lang.Math.abs
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.Random
import java.util.Timer
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@HiltViewModel
class ReplayViewModel @Inject constructor(application : Application, private val repository: ReplayRepository) : AndroidViewModel(application) {
    // Methods for managing replayOngoing state
    private val _isReplayOngoing = MutableLiveData<Boolean>(false)
    val isReplayOngoing: LiveData<Boolean> = _isReplayOngoing
    var selectedApps: ArrayList<ApplicationBean>? = null // Apps to run
    val diffApps: ArrayList<ApplicationBean> = ArrayList() // Apps with differentiation
    val inconclusiveApps: ArrayList<ApplicationBean> = ArrayList()

    var runPortTests: Boolean = false
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
    private var applicationContext: Context? = application.applicationContext

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
    private val timers = ArrayList<Timer>() // For stopping sendRequest timers
    private val numMLab = ArrayList<Int>() // Number of tries before successful MLab connection
    private var mlabServerUsed = repository.isMlabServerUsed()
    private var serverDisplay: String? = null
    private var isIPv6 = false

    /**
     * Start the trace run in a coroutine
     */
    fun execute() {
        _isReplayOngoing.value = true

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                doInBackground()
            } catch (e: Exception) {
                Log.e("TraceRun", "Error in coroutine", e)
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
        context: Context?
    ) {
        this.runPortTests = runPortTests
        this.carrier = carrier
        this.selectedApps = selectedApps
        this.applicationContext = context

        selectedApps?.let {
            for (app in it) {
                app.status = context?.getString(R.string.pending) ?: "Waiting to start"
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
            applicationContext?.getString(R.string.network_error) ?: "Network Error",
            applicationContext?.getString(R.string.text_network_error) ?: "No network available",
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
            app.status = applicationContext?.getString(R.string.pending) ?: "Pending"
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
            app.status = applicationContext?.getString(R.string.pending) ?: "Pending"
        }

        execute()
    }

    /**
     * Update app status
     */
    private suspend fun updateAppStatus(appName: String, status: String) {
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
                app.status = applicationContext?.resources?.getString(R.string.pending) ?: "Waiting to start"
                updateAppStatus(app.name, app.status)
            }
        }

        // Keep checking if the job was cancelled
        if (!isActive) {
            return
        }

        if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
            showNoNetworkDialog()
            return
        }

        /*
         * Step 1: Initialize several variables.
         */
        updateUIBean = UpdateUIBean()
        applicationContext?.let { Config.readConfigFile(Consts.CONFIG_FILE, it) }

        // Get settings from SettingsFragment
        val sharedPrefs = applicationContext?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        serverDisplay =
            if (isTomography) { // Need to use MLab servers for tomography tests
                Consts.DEFAULT_SERVER
            } else {
                sharedPrefs?.getString(
                    applicationContext?.getString(R.string.pref_server_key),
                    Consts.DEFAULT_SERVER
                ) // Get server from SettingsFragment
            }

        // Metadata here is user's network type device used geolocation if permitted etc
        metadataServer = Consts.METADATA_SERVER
        if (!setupServersAndCertificates(serverDisplay!!, metadataServer)) {
            return
        }

        // Get from preferences from SettingsFragment
        sharedPrefs?.let {
            confirmationReplays = it.getBoolean("pref_multiple_tests", true)
            useDefaultThresholds = it.getBoolean("pref_switch", true)
            a_threshold = Objects.requireNonNull(
                it.getString("pref_threshold_area", "10")
            )?.toInt() ?: 10
            ks2pvalue_threshold = Objects.requireNonNull(
                it.getString("pref_threshold_ks2p", "5")
            )?.toInt() ?: 5
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
        settings = applicationContext?.getSharedPreferences(STATUS, Context.MODE_PRIVATE)

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
        val publicIP = repository.serverRepository?.getPublicIP("80") // Get user's IP address
        Config.set("publicIP", publicIP)
        Log.d("Replay", "public IP: $publicIP")

        // If cannot connect to server, display an error and stop tests
        if (publicIP == "-1") {
            showDialog(
                applicationContext?.getString(R.string.simple_error) ?: "Error",
                applicationContext?.getString(R.string.error_no_connection) ?: "No connection",
                true
            )
            return
        }

        if (!isActive) {
            return
        }

        if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
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

                if (!firstApp && mlabServerUsed) {
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
//                for (ws in repository.wsConns) {
//                    ws.close()
//                }
//                repository.closeConnections()

                for (t in timers) {
                    t.cancel()
                }
                timers.clear()

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
            repository.closeConnections()
        }

        /*
         * Step 3: Save results.
         */
        if (results?.length() ?: 0 > 0) {
            Log.i("Result Channel", "Storing results")
            repository.saveResults(results, sharedPrefs)
        }
        if (!isActive) {
            return
        }

        showDialog(
            applicationContext?.getString(R.string.replay_finished_title) ?: "Test Complete",
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
        if (repository.isNetworkUnavailable(applicationContext!!)) {
            showNoNetworkDialog()
            return false
        }
        repository.setupServersAndCertificates(server, metadataServer, numTests, isTomography)
        return true
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
            updateAppStatus(it.name, applicationContext?.getString(R.string.inconclusive) ?: "Inconclusive")
        }
    }

    /**
     * Run test. This method is called for every app/port the user selects. It is also called if
     * differentiation is detected for a test, and confirmation setting is enabled to run a
     * second test for the app/port to confirm if there is differentiation.
     *
     * Each test has two replays. For apps, the replays consist of the original replay,
     * which contains actual traffic from that app, and a random replay, which replaces the
     * content of the original replay with random traffic. For ports, the "original" replay is
     * the port that is being tested. The "random" replay is port 443. The method uses "open" to
     * denote the "original" replay and "random" to denote the "random" replay.
     *
     * @param isConfirmation true if running confirmation test; false if running original test
     * @return true if test will be rerun; false otherwise
     */
    private suspend fun runTest(isConfirmation: Boolean): Boolean {
        /*
         * Step A: Flip a coin to decide which replay type to run first.
         */
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
            for (ws in repository.wsConns) {
                Log.d(
                    "WebSocket", ("Before running test WebSocket (id: "
                            + ws.id + ") connectivity check: "
                            + (if (ws.isOpen) "CONNECTED" else "CLOSED"))
                )
            }

            if (!isActive) { // user cancels running tests
                return false
            }
            if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
                showNoNetworkDialog()
                return false
            }

            // Load app data based on replay type
            this.appData = repository.loadAppDataForReplay(app!!, channel)

            try {
                applicationContext?.let { updateAppStatus(app!!.name, it.getString(R.string.create_side_channel)) }

                // Setup side channels for communication with server
                val sideChannels = repository.setupSideChannels(appData!!)
                val jitterBeans = ArrayList<JitterBean>()
                for (i in 0 until repository.servers.size) {
                    jitterBeans.add(JitterBean())
                }

                // Update history count only once during the run of a single test or set of tomography tests
                if (iteration == 1) {
                    historyCount = repository.updateHistoryCount(historyCount)
                    app!!.historyCount = historyCount
                }

                // Check for random ID
                if (randomID == null) {
                    Log.e("RecordReplay", "randomID does not exist!")
                    applicationContext?.let { setInconclusive(it.getString(R.string.error_no_user_id)) }
                    return false
                }

                // Initialize endOfTest value
                val endOfTest = channel.equals(types[types.size - 1], ignoreCase = true)
                if (endOfTest) {
                    Log.i("Replay", "last replay running ${types[types.size - 1]}!")
                }

                // Check port access
                var replayPort = "80"
                var ipThroughProxy = "127.0.0.1"
                if (appData!!.isTCP) {
                    for (csp in appData!!.tcpCSPs) {
                        replayPort = csp.substring(csp.lastIndexOf('.') + 1)
                    }

                    val isPortAccessible = repository.checkPortAccess(replayPort)
                    if (!isPortAccessible) {
                        portBlocked = true
                        iteration++
                        continue
                    }
                    ipThroughProxy = repository.serverRepository.getPublicIP(replayPort)
                }

                // Set testId (0 for open, 1 for random)
                testId = if (channel.equals("open", ignoreCase = true)) 0 else 1

                // Initiate test with server
                applicationContext?.getString(R.string.ask4permission)?.let { updateAppStatus(app!!.name, it) }

                val numOfTimeSlicesResult = repository.initiateTestWithServer(
                    sideChannels,
                    appData!!,
                    randomID,
                    historyCount,
                    testId,
                    endOfTest,
                    doTest,
                    ipThroughProxy
                )

                val numOfTimeSlices = when {
                    numOfTimeSlicesResult.isSuccess -> numOfTimeSlicesResult.getOrNull()
                    else -> {
                        val error = numOfTimeSlicesResult.exceptionOrNull()?.message ?: "Unknown error"
                        setInconclusive(error)
                        return false
                    }
                }

                if (isCancelled) {
                    return false
                }
                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
                    showNoNetworkDialog()
                    return false
                }

                // Get port mapping from server
                applicationContext?.let { updateAppStatus(app!!.name, it.getString(R.string.receive_server_port_mapping)) }

                val (serverPortsMaps, udpReplayInfoBeans) = repository.getPortMappingFromServer(sideChannels)

                // Create TCP clients from CSPairs
                applicationContext?.getString(R.string.create_tcp_client)?.let { updateAppStatus(app!!.name, it) }

                val CSPairMappings = try {
                    repository.createTCPClients(appData!!, serverPortsMaps)
                } catch (e: Exception) {
                    Log.e("Replay", "Error creating TCP clients", e)
                    applicationContext?.getString(R.string.error_no_connection)?.let { setInconclusive(it) }
                    return false
                }

                // Create UDP clients from client ports
                applicationContext?.getString(R.string.create_udp_client)?.let { updateAppStatus(app!!.name, it) }

                val udpPortMappings = repository.createUDPClients(appData!!)

                if (isCancelled) {
                    return false
                }
                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
                    showNoNetworkDialog()
                    return false
                }

                // Start notifier(s) for UDP
                applicationContext?.getString(R.string.run_notf)?.let { updateAppStatus(app!!.name, it) }

                val notifiers = ArrayList<CombinedNotifierThread>()
                val notfThreads = ArrayList<Thread>()
                for (sc in sideChannels) {
                    val notifier = sc.notifierCreator(udpReplayInfoBeans[sc.id])
                    notifiers.add(notifier)
                    val notfThread = Thread(notifier)
                    notfThread.start()
                    notfThreads.add(notfThread)
                }

                // Start receiver(s) to log throughputs on a given interval
                applicationContext?.getString(R.string.run_receiver)?.let { updateAppStatus(app!!.name, it) }

                val analyzerTasks = ArrayList<CombinedAnalyzerTask>()
                val analyzerTimers = ArrayList<Timer>()
                val receivers = ArrayList<CombinedReceiverThread>()
                val rThreads = ArrayList<Thread>()
                for (sc in sideChannels) {
                    val analyzerTask = CombinedAnalyzerTask(
                        app!!.time / 2.0,
                        appData!!.isTCP,
                        numOfTimeSlices!![sc.id],
                        runPortTests
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

                // Start progress bar update coroutine
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

                // Send packets to server(s)
                applicationContext?.getString(R.string.run_sender)?.let { updateAppStatus(app!!.name, it) }

                val udpServerMappings = ArrayList<HashMap<String, HashMap<String, ServerInstance>>>()
                for (m in serverPortsMaps) {
                    udpServerMappings.add(m["udp"]!!)
                }

                val queue = CombinedQueue(
                    appData!!.q,
                    jitterBeans,
                    analyzerTasks,
                    if (runPortTests) Consts.REPLAY_PORT_TIMEOUT else Consts.REPLAY_APP_TIMEOUT
                )

                // Run the packet queue
                val duration = repository.runPacketQueue(
                    queue,
                    types.size,
                    CSPairMappings,
                    udpPortMappings,
                    udpReplayInfoBeans,
                    udpServerMappings,
                    updateUIBean!!,
                    coroutineContext
                )

                // Stop timers and threads
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
                if (iteration == 1) {
                    finishProgress(1)
                } else {
                    finishProgress(2)
                    Log.i("UpdateUI", "completed!")
                }

                if (isCancelled) {
                    return false
                }
                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
                    showNoNetworkDialog()
                    return false
                }

                // Process results
                applicationContext?.getString(R.string.send_done)?.let { updateAppStatus(app!!.name, it) }

                repository.processTestResults(sideChannels, duration, analyzerTasks)

                // Set avg of port 443, so it can be displayed if port being tested is blocked
                if (runPortTests && channel.equals("random", ignoreCase = true)) {
                    app!!.randomThroughput = analyzerTasks[0].avgThroughput
                }

                // Clean up resources
                repository.cleanupResources(sideChannels, CSPairMappings, udpPortMappings, appData!!)

                Log.i("CleanUp", "Closed all connections")
                iteration++
            } catch (e: InterruptedException) {
                Log.w("Replay", "Replay interrupted!", e)
            } catch (e: IOException) {
                Log.e("Replay", "Some IO issue with server", e)
                applicationContext?.getString(R.string.error_no_connection)?.let { setInconclusive(it) }
                return false
            }
        }

        /*
         * Step C: Determine if there is differentiation.
         */
        return getResults(portBlocked, isConfirmation)
    }

//    /**
//     * Run test. This method is called for every app/port the user selects. It is also called if
//     * differentiation is detected for a test, and confirmation setting is enabled to run a
//     * second test for the app/port to confirm if there is differentiation.
//     *
//     *
//     * Each test has two replays. For apps, the replays consist of the original replay,
//     * which contains actual traffic from that app, and a random replay, which replaces the
//     * content of the original replay with random traffic. For ports, the "original" replay is
//     * the port that is being tested. The "random" replay is port 443. The method uses "open" to
//     * denote the "original" replay and "random" to denote the "random" replay.
//     *
//     *
//     * There are three main steps in this method:
//     * Step A: Flip a coin to decide which replay type to run first.
//     * Step B: Run replays.
//     * Step C: Determine if there is differentiation.
//     *
//     *
//     * Step B has several sub-steps which run for each replay:
//     * Step 0: Initialize variables.
//     * Step 1: Tell server(s) about the replay that is about to happen.
//     * Step 2: Ask server(s) for permission to run replay.
//     * Step 3: Send noIperf.
//     * Step 4: Send device info.
//     * Step 5: Get port mapping from server(s).
//     * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
//     * Step 7: Start notifier(s) for UDP.
//     * Step 8: Start receiver(s) to log throughputs on a given interval.
//     * Step 8.5?: Start progress bar.
//     * Step 9: Send packets to server(s).
//     * Step 10: Tell server(s) that replay is finished.
//     * Step 11: Send throughputs and slices to server(s).
//     * Step 12: Close side channel(s) and TCP/UDP sockets.
//     *
//     * @param isConfirmation true if running confirmation test; false if running original test
//     * @return true if test will be rerun; false otherwise
//     */
//    private suspend fun runTest(isConfirmation: Boolean): Boolean {
//        /*
//  * Step A: Flip a coin to decide which replay type to run first.
//  */
//        //"random" test for ports is port 443
//        val types = if (Math.random() < 0.5) {
//            arrayOf("open", "random")
//        } else {
//            arrayOf("random", "open")
//        }
//
//        /*
//         * Step B: Run replays.
//         */
//        var iteration = 1
//        var portBlocked = false
//        for (channel in types) {
//            for (ws in repository.wsConns) {
//                Log.d(
//                    "WebSocket", ("Before running test WebSocket (id: "
//                            + ws.id + ") connectivity check: "
//                            + (if (ws.isOpen) "CONNECTED" else "CLOSED"))
//                )
//            }
//
//            if (!isActive) { //user cancels running tests
//                return false
//            }
//            if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                showNoNetworkDialog()
//                return false
//            }
//
//            /*
//             * Step 0: Initialize variables.
//             */
//            // Based on the type selected load open or random trace of given application
//            if (channel.equals("open", ignoreCase = true)) {
//                this.appData = repository.loadAppData(app!!.dataFile)
//            } else if (channel.equals("random", ignoreCase = true)) {
//                this.appData = repository.loadAppData(app!!.randomDataFile)
//            } else {
//                Log.wtf("replayIndex", "replay name error: $channel")
//            }
//
//            try {
//
//                applicationContext?.let { updateAppStatus(app!!.name, it.getString(R.string.create_side_channel)) }
////                updateProgress(
////                    "updateStatus", app!!.name, (iteration.toString() + "/"
////                            + types.size + " " + resources.getString(R.string.create_side_channel))
////                )
//                val sideChannelPort = Config.get("combined_sidechannel_port").toInt()
//
//                Log.d("Servers", "$repository.servers metadata $metadataServer")
//                //The Side Channel communicates, in bytes mode, with the server to set up the
//                //tests, start them, end them, and let the server know what exactly is going on.
//                //The tests themselves are conducted over 2 other channels with the server -
//                //the TCP channel for TCP tests and UDP channel for UDP tests. These channels
//                //can be found in CTCPClientThread.java, CUDPClient.java, CombinedReceiverThread.java,
//                //and CombinedNotifierThread.java
//                //Server handles communication in handle() function in server_replay.py in server
//                //code
//                val sideChannels = ArrayList<CombinedSideChannel>()
//                val jitterBeans = ArrayList<JitterBean>()
//                //lots of for loops and ArrayLists in this method - tomography tests require
//                //multiple tests to run at once; each test requires their own set of variables
//                //so the variables for each test are stored in ArrayLists. Normal tests will only
//                //need 1 test, so 1 element in the ArrayLists, but tomography tests will have more
//                var id = 0
//                for (server in repository.servers) {
//                    sideChannels.add(
//                        CombinedSideChannel(
//                            id, repository.sslSocketFactory!!,
//                            server, sideChannelPort, appData!!.isTCP
//                        )
//                    )
//                    jitterBeans.add(JitterBean())
//                    id++
//                }
//
//                // increase history count only once during the run of a single test or set of
//                // tomography tests
//                if (iteration == 1) {
//                    // First update historyCount
//                    historyCount++
//                    // Then write current historyCount to applicationBean
//                    app!!.historyCount = historyCount
//                    // To get historyCount
//                    settings = applicationContext?.getSharedPreferences(STATUS, Context.MODE_PRIVATE)
//                    val localSettings = settings
//                    val editor = localSettings?.edit()
//                    editor?.putInt("historyCount", historyCount)
//                    editor?.apply()
//                    Log.d("Replay", "historyCount: $historyCount")
//                }
//
//                // This random ID is used to map the test results to a specific instance of app
//                // It is generated only once and saved thereafter
//                if (randomID == null) {
//                    Log.e("RecordReplay", "randomID does not exist!")
//                    applicationContext?.let { setInconclusive(it.getString(R.string.error_no_user_id)) }
//                    return false
//                }
//
//                // initialize endOfTest value
//                var endOfTest = false //true if last replay in this test is running
//                if (channel.equals(types[types.size - 1], ignoreCase = true)) {
//                    Log.i("Replay", "last replay running " + types[types.size - 1] + "!")
//                    endOfTest = true
//                }
//
//                //Get user's IP address
//                var replayPort = "80"
//                var ipThroughProxy = "127.0.0.1"
//                if (appData!!.isTCP) {
//                    for (csp in appData!!.tcpCSPs) {
//                        replayPort = csp.substring(csp.lastIndexOf('.') + 1)
//                    }
//                    ipThroughProxy = repository.serverRepository.getPublicIP(replayPort)
//                    if (ipThroughProxy == "-1") { //port is blocked; move on to next replay
//                        //TODO: check if ui needed here
//                        portBlocked = true
//                        iteration++
//                        continue
//                    }
//                }
//
//                // testId is how server knows if the trace ran was open or random
//                testId = if (channel.equals("open", ignoreCase = true)) 0 else 1
//
//                if (doTest) {
//                    Log.w("Replay", "include -Test string")
//                }
//
//                /*
//                 * Step 1: Tell server(s) about the replay that is about to happen.
//                 */
//                var i = 0
//                for (sc in sideChannels) {
//                    // This is group of values that is used to track traces on server
//                    // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
//                    //set extra string to number tries needed to access MLab server
//                    Config.set("extraString", if (numMLab.size == 0) "0" else numMLab[i].toString())
//                    sc.declareID(
//                        appData!!.replayName, if (endOfTest) "True" else "False",
//                        randomID, historyCount.toString(), testId.toString(),
//                        if (doTest) Config.get("extraString") + "-Test" else Config.get("extraString"),
//                        ipThroughProxy, BuildConfig.VERSION_NAME
//                    )
//
//                    // This tuple tells the server if the server should operate on packets of traces
//                    // and if so which packets to process
//                    sc.sendChangeSpec(-1, "null", "null")
//                    i++
//                }
//
//                if (isCancelled) {
//                    return false
//                }
//                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                    showNoNetworkDialog()
//                    return false
//                }
//                /*
//                 * Step 2: Ask server(s) for permission to run replay.
//                 */
//                applicationContext?.getString(R.string.ask4permission)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                // Now to move forward we ask for server permission
//                val numOfTimeSlices = ArrayList<Int>()
//                for (sc in sideChannels) {
//                    val permission = sc.ask4Permission()
//                    val status = permission[0].trim { it <= ' ' }
//
//                    Log.d(
//                        "Replay", ("Channel " + sc.id + ": permission[0]: "
//                                + status + " permission[1]: " + permission[1])
//                    )
//
//                    val permissionError = permission[1].trim { it <= ' ' }
//                    var customError: String
//                    if (status == "0") {
//                        // These are the different errors that server can report
//                        customError = when (permissionError) {
//                            "1" -> applicationContext?.getString(R.string.error_unknown_replay).toString()
//                            "2" -> applicationContext?.getString(R.string.error_IP_connected).toString()
//                            "3" -> applicationContext?.getString(R.string.error_low_resources).toString()
//                            else -> applicationContext?.getString(R.string.error_unknown).toString()
//                        }
//                        setInconclusive(customError)
//                        return false
//                    }
//                    numOfTimeSlices.add(permission[2].trim { it <= ' ' }.toInt(10))
//                }
//
//                /*
//                 * Step 3: Send noIperf.
//                 */
//                for (sc in sideChannels) {
//                    sc.sendIperf() // always send noIperf here
//                }
//
//                if (isCancelled) {
//                    return false
//                }
//                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                    showNoNetworkDialog()
//                    return false
//                }
//
//                /*
//                 * Step 4: Send device info.
//                 */
//                for (sc in sideChannels) {
//                    sc.sendMobileStats(Config.get("sendMobileStats"), applicationContext)
//                }
//
//                /*
//                 * Step 5: Get port mapping from server.
//                 */
//                /*
//                 * Ask for port mapping from server. For some reason, port map
//                 * info parsing was throwing error. so, I put while loop to do
//                 * this until port mapping is parsed successfully.
//                 */
//                applicationContext?.let { updateAppStatus(app!!.name, it.getString(R.string.receive_server_port_mapping)) }
//
//                val serverPortsMaps =
//                    ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>>()
//                val udpReplayInfoBeans = ArrayList<UDPReplayInfoBean>()
//                for (sc in sideChannels) {
//                    serverPortsMaps.add(sc.receivePortMappingNonBlock())
//                    val udpReplayInfoBean = UDPReplayInfoBean()
//                    udpReplayInfoBean.senderCount = sc.receiveSenderCount()
//                    udpReplayInfoBeans.add(udpReplayInfoBean)
//                    Log.i(
//                        "Replay", ("Channel " + sc.id + ": Successfully"
//                                + " received serverPortsMap and senderCount!")
//                    )
//                }
//
//                /*
//                 * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
//                 */
//                applicationContext?.getString(R.string.create_tcp_client)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                //map of all cs pairs to TCP clients for a replay
//                val CSPairMappings = ArrayList<HashMap<String, CTCPClient>>()
//
//                //create TCP clients
//                for (sc in sideChannels) {
//                    val CSPairMapping = HashMap<String, CTCPClient>()
//                    for (csp in appData!!.tcpCSPs) {
//                        //get server IP and port
//                        val destIP = csp.substring(
//                            csp.lastIndexOf('-') + 1,
//                            csp.lastIndexOf(".")
//                        )
//                        var destPort = csp.substring(csp.lastIndexOf('.') + 1)
//                        //pad port to 5 digits with 0s; ex. 00443 or 00080
//                        destPort = String.format("%5s", destPort).replace(' ', '0')
//
//                        //get the server
//                        val instance: ServerInstance
//                        try {
//                            instance = serverPortsMaps[sc.id]["tcp"]
//                                ?.get(destIP)
//                                ?.get(destPort)!!
//                        } catch (e: NullPointerException) {
//                            Log.e("Replay", "Channel " + sc.id + ": Cannot get instance", e)
//                            applicationContext?.getString(R.string.error_no_connection)
//                                ?.let { setInconclusive(it) }
//                            return false
//                        } catch (e: AssertionError) {
//                            Log.e("Replay", "Channel " + sc.id + ": Cannot get instance", e)
//                            applicationContext?.getString(R.string.error_no_connection)
//                                ?.let { setInconclusive(it) }
//                            return false
//                        }
//                        if (instance.server.trim { it <= ' ' } == "")  // TODO: Use a setter instead probably
//                            instance.server =
//                                repository.servers[sc.id].toString() // serverPortsMap.get(destPort);
//
//
//                        //create the client
//                        val c = CTCPClient(
//                            csp, instance.server,
//                            instance.port.toInt(),
//                            appData!!.replayName, Config.get("publicIP"), false
//                        )
//                        CSPairMapping[csp] = c
//                    }
//                    CSPairMappings.add(CSPairMapping)
//                    Log.i(
//                        "Replay", ("Channel " + sc.id
//                                + ": created clients from CSPairs")
//                    )
//                    Log.d("Replay", "Size of CSPairMapping is " + CSPairMapping.size)
//                }
//
//                applicationContext?.getString(R.string.create_udp_client)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                //map of all client ports to UDP clients for a replay
//                val udpPortMappings = ArrayList<HashMap<String, CUDPClient>>()
//
//                //create client for each UDP port
//                for (sc in sideChannels) {
//                    val udpPortMapping = HashMap<String, CUDPClient>()
//                    for (originalClientPort in appData!!.udpClientPorts) {
//                        val c = CUDPClient(Config.get("publicIP"))
//                        udpPortMapping[originalClientPort] = c
//                    }
//                    udpPortMappings.add(udpPortMapping)
//                    Log.i(
//                        "Replay", ("Channel " + sc.id
//                                + ": created clients from udpClientPorts")
//                    )
//                    Log.d("Replay", "Size of udpPortMapping is " + udpPortMapping.size)
//                }
//
//                if (isCancelled) {
//                    return false
//                }
//                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                    showNoNetworkDialog()
//                    return false
//                }
//
//                /*
//                 * Step 7: Start notifier(s) for UDP.
//                 */
//
//                applicationContext?.getString(R.string.run_notf)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//
//                val notifiers = ArrayList<CombinedNotifierThread>()
//                val notfThreads = ArrayList<Thread>()
//                for (sc in sideChannels) {
//                    val notifier = sc.notifierCreator(udpReplayInfoBeans[sc.id])
//                    notifiers.add(notifier)
//                    val notfThread = Thread(notifier)
//                    notfThread.start()
//                    notfThreads.add(notfThread)
//                }
//
//                /*
//                 * Step 8: Start receiver(s) to log throughputs on a given interval.
//                 */
//                applicationContext?.getString(R.string.run_receiver)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                val analyzerTasks = ArrayList<CombinedAnalyzerTask>()
//                val analyzerTimers = ArrayList<Timer>()
//                val receivers = ArrayList<CombinedReceiverThread>()
//                val rThreads = ArrayList<Thread>()
//                for (sc in sideChannels) {
//                    val analyzerTask = CombinedAnalyzerTask(
//                        app!!.time / 2.0,
//                        appData!!.isTCP, numOfTimeSlices[sc.id], runPortTests
//                    ) //throughput logger
//                    val analyzerTimer = Timer(true) //timer to log throughputs on interval
//                    analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.interval)
//                    analyzerTasks.add(analyzerTask)
//                    analyzerTimers.add(analyzerTimer)
//
//                    val receiver = CombinedReceiverThread(
//                        udpReplayInfoBeans[sc.id], jitterBeans[sc.id], analyzerTask
//                    ) //receiver for udp
//                    receivers.add(receiver)
//                    val rThread = Thread(receiver)
//                    rThread.start()
//                    rThreads.add(rThread)
//                }
//
//                /*
//                 * Step 8.5?: Start progress bar.
//                 */
//                // This thread runs in parallel keeps progressbar up to date
//                val finalIteration = iteration
//
//                //TODO: switch to ScheduledThreadExecutor?
//                val uiUpdateJob = CoroutineScope(Dispatchers.Default).launch {
//                    // Name the coroutine for debugging (optional)
//                    currentCoroutineContext()[CoroutineName]?.let {
//                        Thread.currentThread().name = "UIUpdateCoroutine"
//                    }
//
//                    if (finalIteration == 1) {
//                        clearProgressBar()
//                    }
//
//                    while (updateUIBean!!.progress < 100 && isActive) {
//                        //doubtful
//                        updateProgress(updateUIBean!!.progress)
//                        // Delay in coroutine instead of Thread.sleep
//                        delay(500) // 500ms delay
//                    }
//                }
//                uiUpdateJobs.add(uiUpdateJob)
//
//                /*
//                 * Step 9: Send packets to server(s).
//                 */
//                applicationContext?.getString(R.string.run_sender)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                val udpServerMappings =
//                    ArrayList<HashMap<String, HashMap<String, ServerInstance>>>()
//                for (m in serverPortsMaps) {
//                    udpServerMappings.add(m["udp"]!!)
//                }
//
//                val queue = CombinedQueue(
//                    appData!!.q, jitterBeans, analyzerTasks,
//                    if (runPortTests) Consts.REPLAY_PORT_TIMEOUT else Consts.REPLAY_APP_TIMEOUT
//                )
//                val timeStarted = System.nanoTime() //start time for sending
//                //send packets
//                updateUIBean?.let {
//                    queue.run(
//                        it, types.size, CSPairMappings,
//                        udpPortMappings, udpReplayInfoBeans, udpServerMappings,
//                        Config.get("timing").toBoolean(), repository.servers, coroutineContext
//                    )
//                }
//
//                //all packets sent - stop logging and receiving
//                queue.stopTimers()
//                for (t in analyzerTimers) {
//                    t.cancel()
//                }
//                for (n in notifiers) {
//                    n.doneSending = true
//                }
//                for (t in notfThreads) {
//                    t.join()
//                }
//                for (r in receivers) {
//                    r.keepRunning = false
//                }
//                for (t in rThreads) {
//                    t.join()
//                }
//
//                if (iteration == 1) { //make progress bar to 50%
//                    finishProgress(1)
////                    updateProgress("finishProgress", "1")
//                } else { //make progress bar to 100%
//                    finishProgress(2)
////                    updateProgress("finishProgress", "2")
//                    Log.i("UpdateUI", "completed!")
//                }
//
//                if (isCancelled) {
//                    return false
//                }
//                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                    showNoNetworkDialog()
//                    return false
//                }
//
//                /*
//                 * Step 10: Tell server(s) that replay is finished.
//                 */
//                applicationContext?.getString(R.string.send_done)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                //time to send all packets
//                val duration = ((System.nanoTime() - timeStarted).toDouble()) / 1000000000
//                for (sc in sideChannels) {
//                    sc.sendDone(duration)
//                }
//                Log.d("Replay", "replay(s) finished using time $duration s")
//
//                /*
//                 * Step 11: Send throughputs and slices to server(s).
//                 */
//                for (sc in sideChannels) {
//                    sc.sendTimeSlices(analyzerTasks[sc.id].averageThroughputsAndSlices)
//                }
//
//                //set avg of port 443, so it can be displayed if port being tested is blocked
//                if (runPortTests && channel.equals("random", ignoreCase = true)) {
//                    app!!.randomThroughput =
//                        analyzerTasks[0].avgThroughput //TODO: Multithread display
//                }
//
//                // TODO find a better way to do this
//                // Send Result;No and wait for OK before moving forward
//                for (sc in sideChannels) {
//                    while (sc.getResult(Config.get("result"))) {
//                        Thread.sleep(500)
//                    }
//                }
//
//                /*
//                 * Step 12: Close side channel(s) and TCP/UDP sockets.
//                 */
//                // closing side channel sockets
//                for (sc in sideChannels) {
//                    sc.closeSideChannelSocket()
//                }
//
//                //close TCP sockets
//                for (mapping in CSPairMappings) {
//                    for (csp in appData!!.tcpCSPs) {
//                        val c = mapping[csp]
//                        c?.close()
//                    }
//                }
//                Log.i("CleanUp", "Closed CSPairs 1")
//
//                //close UDP sockets
//                for (mapping in udpPortMappings) {
//                    for (originalClientPort in appData!!.udpClientPorts) {
//                        val c = mapping[originalClientPort]
//                        c?.close()
//                    }
//                }
//
//                Log.i("CleanUp", "Closed CSPairs 2")
//                iteration++
//            } catch (e: InterruptedException) {
//                Log.w("Replay", "Replay interrupted!", e)
//            } catch (e: IOException) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
//                Log.e("Replay", "Some IO issue with server", e)
//                applicationContext?.getString(R.string.error_no_connection)
//                    ?.let { setInconclusive(it) }
//                return false
//            }
//        }
//
//        /*
//         * Step C: Determine if there is differentiation.
//         */
//        return getResults(portBlocked, isConfirmation)
//    }
//
//    /**
//     * Determines if there is differentiation. If app is running, result of random test is
//     * compared against original test. If port is running, result of port test is compared
//     * against port 443.
//     *
//     *
//     * If results are inconclusive or have differentiation, a confirmation test will run if the
//     * confirmation setting is switched on.
//     *
//     *
//     * For port tests, Step 1 and Step 2 are skipped if a port is blocked, as no throughputs are
//     * sent to the server to analyze. A response is created for Step 3 instead of retrieving
//     * from the server. When a port is blocked, the port throughput is 0 Mbps, while port 443
//     * (which should not be blocked) has a throughput, which is calculated in Step 11 of
//     * runTest().
//     *
//     *
//     * Step 1: Ask sever to analyze a test.
//     * Step 2: Get result of analysis from server.
//     * Step 3: Parse the analysis results.
//     * Step 4: Determine if there is differentiation.
//     * Step 5: Save and display results to user. Rerun test if necessary.
//     *
//     * @param portBlocked    true if a port in the port tests is blocked; false otherwise
//     * @param isConfirmation true if confirmation test; false if original test
//     * @return true if confirmation test needs to be run; false otherwise
//     */
//    private suspend fun getResults(portBlocked: Boolean, isConfirmation: Boolean): Boolean {
//        var portBlocked = portBlocked
//        try {
//            if (isCancelled) {
//                return false
//            }
//            if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                showNoNetworkDialog()
//                return false
//            }
//
//            var id = 0
//            for (w in repository.wsConns) { //check websockets still connected if using MLab
//                Log.d(
//                    "WebSocket", ("WebSocket (id: " + id + ") connectivity check: "
//                            + (if (w.isOpen) "CONNECTED" else "CLOSED"))
//                )
//                id++
//            }
//
//            val analysisResults = ArrayList<JSONObject>()
//            if (!portBlocked) { //skip Step 1 and step 2 if port blocked
//                /*
//                 * Step 1: Ask server to analyze a test.
//                 */
//                var resp: JSONObject?
//                for (server in repository.getAnalyzerServerUrls()) {
//                    for (ask4analysisRetry in 3 downTo 1) {
//                        resp = repository.ask4analysis(server, randomID, app!!.historyCount) //request analysis
//                        if (resp == null) {
//                            Log.e(
//                                "Result Channel",
//                                "$server: ask4analysis returned null!"
//                            )
//                        } else {
//                            analysisResults.add(resp)
//                            break
//                        }
//                    }
//                }
//
//                if (analysisResults.size != repository.getAnalyzerServerUrls().size) {
//                    applicationContext?.getString(R.string.error_analysis_fail)
//                        ?.let { setInconclusive(it) }
//                    return false
//                }
//
//                var success: Boolean
//                for (result in analysisResults) {
//                    success = result.getBoolean("success")
//                    if (!success) {
//                        Log.e("Result Channel", "ask4analysis failed!")
//                        applicationContext?.getString(R.string.error_analysis_fail)
//                            ?.let { setInconclusive(it) }
//                        return false
//                    }
//                }
//
//                applicationContext?.getString(R.string.waiting)
//                    ?.let { updateAppStatus(app!!.name, it) }
//
//                // sanity check
//                if (app!!.historyCount < 0) {
//                    Log.e("Result Channel", "historyCount value not correct!")
//                    return false
//                }
//
//                Log.i("Result Channel", "ask4analysis succeeded!")
//                if (isCancelled) {
//                    return false
//                }
//                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
//                    showNoNetworkDialog()
//                    return false
//                }
//
//                /*
//                 * Step 2: Get results of analysis from server.
//                 */
//                analysisResults.clear()
//                for (url in repository.getAnalyzerServerUrls()) {
//                    var i = 0
//                    while (true) {
//                        //3 attempts to get analysis from sever
//                        resp = repository.getSingleResult(url, randomID, app!!.historyCount) //get results
//
//                        if (resp == null) {
//                            Log.e(
//                                "Result Channel",
//                                "$url: getSingleResult returned null!"
//                            )
//                        } else {
//                            success = resp.getBoolean("success")
//                            if (success) { //success
//                                if (resp.has("response")) { //success and has response
//                                    analysisResults.add(resp)
//                                    Log.i(
//                                        "Result Channel",
//                                        "$url: retrieve result succeeded"
//                                    )
//                                    break
//                                } else { //success but response is missing
//                                    Log.w(
//                                        "Result Channel",
//                                        "$url: Server result not ready"
//                                    )
//                                }
//                            } else if (resp.has("error")) {
//                                Log.e(
//                                    "Result Channel",
//                                    "ERROR: " + url + ": " + resp.getString("error")
//                                )
//                            } else {
//                                Log.e(
//                                    "Result Channel",
//                                    "Error: $url: Some error getting results."
//                                )
//                            }
//                        }
//
//                        if (i < 3) { //wait 2 seconds to try again
//                            try {
//                                Thread.sleep(2000)
//                            } catch (e: InterruptedException) {
//                                Log.w("Result Channel", "Sleep interrupted", e)
//                            }
//                        } else { //error after 3rd attempt
//                            if (runPortTests) { //"the port 80 issue"
//                                portBlocked = true
//                                Log.i("Result Channel", "Can't retrieve result, port blocked")
//                                break
//                            } else {
//                                applicationContext?.getString(R.string.not_all_tcp_sent_text)
//                                    ?.let { setInconclusive(it) }
//                                return false
//                            }
//                        }
//                        i++
//                    }
//                }
//            }
//
//            /*
//             * Step 3: Parse the analysis results.
//             */
//            println("RESULLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLTS")
//            for (j in analysisResults) {
//                println(j)
//            }
//
//            var differentiationNetwork: String? = ""
//            if (isTomography) {
//                //TODO: tomography api call here
//                val random = Random()
//                if (random.nextInt(2) == 1) {
//                    differentiationNetwork = carrier
//                }
//            }
//            //TODO: put multithread display here
//            val response =
//                if (portBlocked) JSONObject() else analysisResults[0].getJSONObject("response")
//
//
//            if (portBlocked) { //generate results if port blocked
//                response.put("userID", randomID)
//                response.put("historyCount", historyCount)
//                response.put("replayName", app!!.dataFile)
//                response.put("area_test", -1)
//                response.put("ks2pVal", -1)
//                response.put("ks2_ratio_test", -1)
//                response.put("xput_avg_original", 0)
//                response.put(
//                    "xput_avg_test",
//                    app!!.randomThroughput
//                ) //calculated in runTest() Step 11
//            }
//
//            Log.d("Result Channel", "SERVER RESPONSE: $response")
//
//            val userID = response.getString("userID")
//            val historyCount = response.getInt("historyCount")
//            val area_test = response.getDouble("area_test")
//            val ks2pVal = response.getDouble("ks2pVal")
//            val ks2RatioTest = response.getDouble("ks2_ratio_test")
//            val xputOriginal = response.getDouble("xput_avg_original")
//            val xputTest = response.getDouble("xput_avg_test")
//
//            // sanity check
//            if ((!userID.trim { it <= ' ' }.equals(randomID, ignoreCase = true))
//                || (historyCount != app!!.historyCount)
//            ) {
//                Log.e(
//                    "Result Channel", ("Result didn't pass sanity check! "
//                            + "correct id: " + randomID
//                            + " correct historyCount: " + app!!.historyCount)
//                )
//                Log.e("Result Channel", "Result content: $response")
//                applicationContext?.getString(R.string.error_result)?.let { setInconclusive(it) }
//                return false
//            }
//
//            /*
//             * Step 4: Determine if there is differentiation.
//             */
//            //area test threshold default is 50%; ks2 p value test threshold default is 1%
//            //if default switch is on and one of the throughputs is over 10 Mbps, change the
//            //area threshold to 30%, which increases chance of Wehe finding differentiation.
//            //If the throughputs are over 10 Mbps, the difference between the two throughputs
//            //would need to be much larger than smaller throughputs for differentiation to be
//            //triggered, which may confuse users
//            //TODO: might have to relook at thresholds and do some formal research on optimal
//            // thresholds. Currently thresholds chosen ad-hoc
//            if (useDefaultThresholds && (xputOriginal > 10 || xputTest > 10)) {
//                a_threshold = 30
//            }
//
//            val area_test_threshold = a_threshold.toDouble() / 100
//            val ks2pVal_threshold = ks2pvalue_threshold.toDouble() / 100
//
//            //double ks2RatioTest_threshold = (double) 95 / 100;
//            val aboveArea = abs(area_test) >= area_test_threshold
//            //boolean trustPValue = ks2RatioTest >= ks2RatioTest_threshold;
//            val belowP = ks2pVal < ks2pVal_threshold
//            var differentiation = false
//            var inconclusive = false
//
//            if (portBlocked) {
//                differentiation = true
//            } else if (aboveArea) {
//                if (belowP) {
//                    differentiation = true
//                } else {
//                    inconclusive = true
//                }
//            }
//
//            // TODO uncomment following code when you want differentiation to occur
////                differentiation = true;
////                inconclusive = true;
//
//            /*
//             * Step 5: Save and display results to user. Rerun test if necessary.
//             */
//            //determine if the test needs to be rerun
//            if ((inconclusive || differentiation) && confirmationReplays && !isConfirmation && !isTomography) {
//                applicationContext?.getString(R.string.confirmation_replay)
//                    ?.let { updateAppStatus(app!!.name, it) }
//                try { //wait 2 seconds so user can read message before it disappears
//                    Thread.sleep(2000)
//                } catch (e: InterruptedException) {
//                    Log.w("Result Channel", "sleep interrupted", e)
//                }
//                //runTest();
//                return true //return so that first result isn't saved
//            }
//
//            val displayStatus: String //display for the user in their language
//            val saveStatus: String //save to disk, so it can appear in the correct language in prev results
//            if (isTomography) {
//                saveStatus = if (differentiationNetwork == "") "tomo failed" else "tomo succ"
//                displayStatus = if (differentiationNetwork == "") ({
//                    applicationContext?.getString(R.string.tomo_failed)
//                }).toString()
//                else applicationContext?.getString(R.string.tomo_succ).toString()
//
//            } else if (inconclusive) {
//                saveStatus = "inconclusive"
//                displayStatus = applicationContext?.getString(R.string.inconclusive).toString()
//                inconclusiveApps.add(app!!)
//            } else if (differentiation) {
//                saveStatus = "has diff"
//                displayStatus = applicationContext?.getString(R.string.has_diff).toString()
//
//                var error =
//                    if (runPortTests) applicationContext?.getString(R.string.test_blocked_port_text) else applicationContext?.getString(
//                        R.string.test_blocked_app_text
//                    )
//                if (!portBlocked) {
//                    error = if (xputOriginal > xputTest) {
//                        if (runPortTests) applicationContext?.getString(R.string.test_prioritized_port_text) else applicationContext?.getString(
//                            R.string.test_prioritized_app_text
//                        )
//                    } else {
//                        if (runPortTests) applicationContext?.getString(R.string.test_throttled_port_text) else applicationContext?.getString(
//                            R.string.test_throttled_app_text
//                        )
//                    }
//                }
//                app!!.error = error
//
//                val current = applicationContext?.resources?.configuration?.locale
//                val country = current?.country
//                if (country == "FR") { //show alert arcep button
//                    app!!.arcepNeedsAlerting = true
//                } else if (country == "US") {
//                    app!!.isAlertFCC = true
//                }
//                diffApps.add(app!!)
//            } else {
//                saveStatus = "no diff"
//                displayStatus = applicationContext?.getString(R.string.no_diff).toString()
//            }
//
//            //for results display on the Run Test page
//            app!!.status = displayStatus
//            app!!.area_test = area_test
//            app!!.ks2pVal = ks2pVal
//            app!!.ks2pRatio = ks2RatioTest
//            app!!.originalThroughput = xputOriginal
//            app!!.randomThroughput = xputTest
//            app!!.differentiationNetwork = differentiationNetwork
//
//            Log.i("Result Channel", "writing results to json array")
//            //for results display on the Previous Results page
//            response.put("isPort", runPortTests)
//            response.put("appName", app!!.name)
//            response.put("appImage", app!!.image)
//            response.put("status", saveStatus)
//            response.put("date", Date().time)
//            response.put("areaThreshold", area_test_threshold)
//            response.put("ks2pThreshold", ks2pVal_threshold)
//            response.put("isIPv6", isIPv6)
//            response.put("server", serverDisplay)
//            response.put("carrier", carrier)
//            if (isTomography) {
//                response.put("tomographyNetwork", differentiationNetwork)
//            }
//            Log.d("response", response.toString())
//            results!!.put(response) //put response in array to save
//            updateAppStatus(app!!.name, app!!.status) //display results to user
//        } catch (e: JSONException) {
//            Log.e("Result Channel", "parsing json error", e)
//        }
//        return false
//        //todos: put description of tomography at top of reruns
//        //fix ui pop up
//        //does rerun button pop up after tomo tests?
//        //do no diff tests appear during tomo tests?
//    }

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
        val current = applicationContext?.resources?.configuration?.locale
        val country = current?.country

        // Determine if confirmation test is needed
        val needsConfirmation = (analysis.inconclusive || analysis.differentiation) &&
                confirmationReplays &&
                !isConfirmation &&
                !isTomography

        if (needsConfirmation) {
            app?.let {
                applicationContext?.getString(R.string.confirmation_replay)
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
                    applicationContext?.getString(R.string.tomo_succ).toString()
                else
                    applicationContext?.getString(R.string.tomo_failed).toString()
            } else if (analysis.inconclusive) {
                it.status = applicationContext?.getString(R.string.inconclusive).toString()
                inconclusiveApps.add(it)
            } else if (analysis.differentiation) {
                it.status = applicationContext?.getString(R.string.has_diff).toString()
                it.error = analysis.errorMessage

                // Add country-specific alert buttons
                if (country == "FR") {
                    it.arcepNeedsAlerting = true
                } else if (country == "US") {
                    it.isAlertFCC = true
                }
                diffApps.add(it)
            } else {
                it.status = applicationContext?.getString(R.string.no_diff).toString()
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
            response.put("isIPv6", isIPv6)
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

        // Add to results array
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
            if (isCancelled) {
                return false
            }

            if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
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
                        ?: applicationContext?.getString(R.string.error_analysis_fail)
                    errorMsg?.let { setInconclusive(it) }
                    return false
                }

                applicationContext?.getString(R.string.waiting)
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

                if (applicationContext?.let { isNetworkUnavailable(it) } == true) {
                    showNoNetworkDialog()
                    return false
                }

                // Retrieve results from servers
                val resultsRetrieved =
                    randomID?.let { repository.retrieveResults(it, app!!.historyCount, runPortTests) }

                if (resultsRetrieved != null) {
                    if (resultsRetrieved.isFailure) {
                        val errorMsg = resultsRetrieved.exceptionOrNull()?.message
                            ?: applicationContext?.getString(R.string.error_analysis_fail)
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
                    applicationContext?.getString(R.string.not_all_tcp_sent_text)
                        ?.let { setInconclusive(it) }
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
                    ?: applicationContext?.getString(R.string.error_result)
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
    private fun logWebSocketConnections() {
        for ((id, w) in repository.wsConns.withIndex()) {
            Log.d(
                "WebSocket", ("WebSocket (id: " + id + ") connectivity check: "
                        + (if (w.isOpen) "CONNECTED" else "CLOSED"))
            )
        }
    }
}