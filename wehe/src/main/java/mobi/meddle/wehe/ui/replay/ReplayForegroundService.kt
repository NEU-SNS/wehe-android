package mobi.meddle.wehe.ui.replay;

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
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
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Extract test parameters from intent
        intent?.let {
            runPortTests = it.getBooleanExtra("runPortTests", false)
            carrier = it.getStringExtra("carrier")
            selectedApps = it.getParcelableArrayListExtra("selectedApps")

            // Start the service in foreground
            startForeground(NOTIFICATION_ID, createNotification("Starting tests..."))

            // Start the tests
            startReplayTests()
        }

        // If service is killed, restart it
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        _serviceRunning.postValue(false)
        super.onDestroy()
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

        // Create notification
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wehe Network Tests")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * Update notification with current status
     */
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Start the replay tests
     */
    private fun startReplayTests() {
        if (_serviceRunning.value == true) {
            Log.w(TAG, "Service already running, ignoring start request")
            return
        }

        _serviceRunning.postValue(true)
        _isReplayOngoing.postValue(true)
        _errorMessage.postValue(null)

        // Initialize ViewModel
        viewModel = ReplayViewModel(application, replayRepository).apply {
            // Observe ViewModel's LiveData
            isReplayOngoing.observeForever { _isReplayOngoing.postValue(it) }
            currentTestingApp.observeForever {
                _currentTestingApp.postValue(it)
                it?.let { app -> updateNotification("Testing: ${app.name}") }
            }
            progressUpdateEvent.observeForever { _progressUpdate.postValue(it) }
            statusUpdateEvent.observeForever {
                _statusUpdate.postValue(it)
                updateNotification("Testing ${it.first}: ${it.second}")
            }
            iteration.observeForever { _iteration.postValue(it) }
        }

        // Initialize data in ViewModel
        selectedApps?.let {
            viewModel?.initializeData(runPortTests, carrier, it, applicationContext)
        }

        // Start tests in a coroutine
        serviceJob = serviceScope.launch {
            try {
                // Execute tests
                viewModel?.execute()

                // Wait for tests to complete
                while (viewModel?.isReplayOngoing?.value == true) {
                    delay(500)
                }

                // Collect results
                val allApps = selectedApps ?: ArrayList()
                val diffApps = viewModel?.diffApps ?: ArrayList()
                val inconclusiveApps = viewModel?.inconclusiveApps ?: ArrayList()

                // Post results
                _testResults.postValue(Triple(allApps, diffApps, inconclusiveApps))
                _testComplete.postValue(true)

                // Update notification
                updateNotification("Tests completed")

                // Stop service after a delay
                delay(5000)
                stopSelf()

            } catch (e: Exception) {
                Log.e(TAG, "Error running tests", e)
                _errorMessage.postValue("Error: ${e.message}")
                updateNotification("Test error: ${e.message}")

                // Stop service after a delay
                delay(5000)
                stopSelf()
            } finally {
                _isReplayOngoing.postValue(false)
                _serviceRunning.postValue(false)
            }
        }
    }

    /**
     * Cancel the running tests
     */
    fun cancelTests() {
        viewModel?.cancel()
        serviceJob?.cancel()
        _isReplayOngoing.postValue(false)
        updateNotification("Tests cancelled")

        // Stop service after a delay
        serviceScope.launch {
            delay(1000)
            stopSelf()
        }
    }

    /**
     * Service binder class
     */
    inner class ReplayServiceBinder : Binder() {
        fun getService(): ReplayForegroundService = this@ReplayForegroundService
    }

    companion object {
        private const val TAG = "ReplayService"
        private const val CHANNEL_ID = "wehe_replay_channel"
        private const val NOTIFICATION_ID = 1
    }
}