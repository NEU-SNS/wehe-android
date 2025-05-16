package mobi.meddle.wehe.ui.replay

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import mobi.meddle.wehe.ui.main.MainActivity
import javax.inject.Inject

/**
 * Foreground Service to run replay tests in the background
 */
@AndroidEntryPoint
class ReplayForegroundService : LifecycleService() {

    @Inject
    lateinit var replayRepository: ReplayRepository

    private val binder = ReplayServiceBinder()
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    // Service state
    private val _serviceRunning = MutableLiveData<Boolean>(false)
    val serviceRunning: LiveData<Boolean> = _serviceRunning

    // Test state
    private val _isReplayOngoing = MutableLiveData<Boolean>(false)
    val isReplayOngoing: LiveData<Boolean> = _isReplayOngoing

    // Current app being tested
    private val _currentTestingApp = MutableLiveData<ApplicationBean?>()
    val currentTestingApp: LiveData<ApplicationBean?> = _currentTestingApp

    // Test progress
    private val _progressUpdate = MutableLiveData<Int>()
    val progressUpdate: LiveData<Int> = _progressUpdate

    // Test status
    private val _statusUpdate = MutableLiveData<Pair<String, String>>()
    val statusUpdate: LiveData<Pair<String, String>> = _statusUpdate

    // Current iteration
    private val _iteration = MutableLiveData<Int>()
    val iteration: LiveData<Int> = _iteration

    // Test results
    private val _testResults = MutableLiveData<Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>>()
    val testResults: LiveData<Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>> = _testResults

    // Test complete
    private val _testComplete = MutableLiveData<Boolean>()
    val testComplete: LiveData<Boolean> = _testComplete

    // Error message
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Test parameters
    private var runPortTests: Boolean = false
    private var carrier: String? = null
    private var selectedApps: ArrayList<ApplicationBean>? = null
    private var viewModel: ReplayViewModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "Service onStartCommand, intent: ${intent?.action}")

        // Extract test parameters from intent
        intent?.let {
            runPortTests = it.getBooleanExtra("runPortTests", false)
            carrier = it.getStringExtra("carrier")
            @Suppress("DEPRECATION") // Handle compatibility for older devices
            selectedApps = it.getParcelableArrayListExtra("selectedApps")

            Log.d(TAG, "Received parameters - runPortTests: $runPortTests, carrier: $carrier, apps: ${selectedApps?.size}")

            // Start the service in foreground immediately to prevent ANR
            startForeground(NOTIFICATION_ID, createNotification("Preparing tests..."))

            // Acquire wake lock to prevent CPU from sleeping
            acquireWakeLock()

            // Start the tests if not already running
            if (_serviceRunning.value != true) {
                startReplayTests()
            } else {
                Log.w(TAG, "Tests already running, ignoring duplicate start command")
            }
        } ?: run {
            Log.e(TAG, "Service started with null intent")
            stopSelf()
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        releaseWakeLock()
        cancelTestsInternal()
        super.onDestroy()
    }

    /**
     * Acquire wake lock to prevent device from sleeping during tests
     */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Wehe:ReplayWakeLock"
            )
            wakeLock?.acquire(30 * 60 * 1000L) // 30 minutes timeout
            Log.d(TAG, "Wake lock acquired")
        }
    }

    /**
     * Release wake lock when tests are complete
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "Wake lock released")
            }
            wakeLock = null
        }
    }

    /**
     * Create notification channel for Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wehe Tests",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running network neutrality tests"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Create notification for foreground service
     */
    private fun createNotification(contentText: String): Notification {
        // Intent to open main activity when notification is tapped
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(
                this, 0, notificationIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )
        }

        // Create cancel action
        val cancelIntent = Intent(this, ReplayForegroundService::class.java).apply {
            action = ACTION_CANCEL_TEST
        }
        val cancelPendingIntent = PendingIntent.getService(
            this, 1, cancelIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        // Create notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wehe Network Tests")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update notification with current status
     */
    private fun updateNotification(contentText: String) {
        try {
            val notification = createNotification(contentText)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $contentText")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    /**
     * Start the replay tests
     */
    private fun startReplayTests() {
        if (_serviceRunning.value == true) {
            Log.w(TAG, "Service already running, ignoring start request")
            return
        }

        Log.d(TAG, "Starting replay tests")
        _serviceRunning.postValue(true)
        _isReplayOngoing.postValue(true)
        _errorMessage.postValue(null)

        // Initialize ViewModel if not already initialized
        if (viewModel == null) {
            viewModel = ReplayViewModel(application, replayRepository)
        }

        // Set up observers for the ViewModel
        setupViewModelObservers()

        // Initialize data in ViewModel
        selectedApps?.let {
            Log.d(TAG, "Initializing ViewModel with ${it.size} apps")
            viewModel?.initializeData(runPortTests, carrier, it, applicationContext)
        } ?: run {
            Log.e(TAG, "No apps selected for testing")
            handleError("No apps selected for testing")
            return
        }

        // Start tests in a coroutine
        serviceJob = serviceScope.launch {
            try {
                Log.d(TAG, "Executing tests")
                updateNotification("Starting tests...")

                // Execute tests
                viewModel?.execute()

                // Wait for tests to complete
                while (viewModel?.isReplayOngoing?.value == true) {
                    delay(500)
                }

                Log.d(TAG, "Tests completed")

                // Collect and post results
                collectAndPostResults()

                // Update notification
                updateNotification("Tests completed")

                // Stop service after a delay to ensure results are delivered
                delay(10000)
                stopSelf()

            } catch (e: CancellationException) {
                Log.d(TAG, "Tests cancelled")
                _isReplayOngoing.postValue(false)
                updateNotification("Tests cancelled")
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error running tests", e)
                handleError("Error: ${e.message}")
            } finally {
                _isReplayOngoing.postValue(false)
                _serviceRunning.postValue(false)
                releaseWakeLock()
            }
        }
    }

    /**
     * Set up observers for the ViewModel
     */
    private fun setupViewModelObservers() {
        viewModel?.let { vm ->
            // Remove any existing observers to prevent duplicates
            vm.isReplayOngoing.removeObservers(this)
            vm.currentTestingApp.removeObservers(this)
            vm.progressUpdateEvent.removeObservers(this)
            vm.statusUpdateEvent.removeObservers(this)
            vm.iteration.removeObservers(this)

            // Set up new observers
            vm.isReplayOngoing.observe(this) { isOngoing ->
                _isReplayOngoing.postValue(isOngoing)
                Log.d(TAG, "Replay ongoing: $isOngoing")
            }

            vm.currentTestingApp.observe(this) { app ->
                _currentTestingApp.postValue(app)
                app?.let {
                    Log.d(TAG, "Testing app: ${it.name}")
                    updateNotification("Testing: ${it.name}")
                }
            }

            vm.progressUpdateEvent.observe(this) { progress ->
                _progressUpdate.postValue(progress)
                Log.d(TAG, "Progress update: $progress")
            }

            vm.statusUpdateEvent.observe(this) { status ->
                _statusUpdate.postValue(status)
                Log.d(TAG, "Status update: ${status.first} - ${status.second}")
                updateNotification("${status.first}: ${status.second}")
            }

            vm.iteration.observe(this) { iterationNum ->
                _iteration.postValue(iterationNum)
                Log.d(TAG, "Iteration: $iterationNum")
            }
        }
    }

    /**
     * Collect and post results
     */
    private fun collectAndPostResults() {
        Log.d(TAG, "Collecting test results")

        // Get lists from ViewModel
        val allApps = selectedApps ?: ArrayList()
        val diffApps = ArrayList<ApplicationBean>()
        val inconclusiveApps = ArrayList<ApplicationBean>()

        viewModel?.let { vm ->
            // Add differentiated apps
            vm.diffApps?.let { diffApps.addAll(it) }
            // Add inconclusive apps
            vm.inconclusiveApps?.let { inconclusiveApps.addAll(it) }
        }

        // Post results
        val results = Triple(allApps, diffApps, inconclusiveApps)
        _testResults.postValue(results)
        _testComplete.postValue(true)

        Log.d(TAG, "Results posted: ${allApps.size} total, ${diffApps.size} differentiated, ${inconclusiveApps.size} inconclusive")
    }

    /**
     * Handle error during test execution
     */
    private fun handleError(errorMessage: String) {
        _errorMessage.postValue(errorMessage)
        updateNotification("Test error: $errorMessage")

        // Stop service after a delay
        serviceScope.launch {
            delay(5000)
            stopSelf()
        }
    }

    /**
     * Cancel the running tests
     */
    fun cancelTests() {
        Log.d(TAG, "Cancelling tests via public method")
        cancelTestsInternal()
    }

    /**
     * Internal method to cancel tests
     */
    private fun cancelTestsInternal() {
        Log.d(TAG, "Cancelling tests internally")
        viewModel?.cancel()
        serviceJob?.cancel()
        _isReplayOngoing.postValue(false)
        updateNotification("Tests cancelled")

        // Release resources
        releaseWakeLock()
    }

    /**
     * Service binder class
     */
    inner class ReplayServiceBinder : Binder() {
        fun getService(): ReplayForegroundService = this@ReplayForegroundService
    }

    companion object {
        private const val TAG = "ReplayForegroundService"
        private const val CHANNEL_ID = "wehe_replay_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_CANCEL_TEST = "mobi.meddle.wehe.ACTION_CANCEL_TEST"
    }
}