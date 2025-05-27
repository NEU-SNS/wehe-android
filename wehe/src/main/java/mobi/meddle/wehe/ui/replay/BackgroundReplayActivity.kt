package mobi.meddle.wehe.ui.replay

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.model.ApplicationBean


@AndroidEntryPoint
class BackgroundReplayActivity : AppCompatActivity() {

    private val viewModel: BackgroundReplayViewModel by viewModels()
    private var replayService: ReplayForegroundService? = null
    private var serviceBound = false
    private lateinit var progressBar: ProgressBar
    private lateinit var instructionsText: TextView
    private lateinit var currentAppTextView: TextView
    private lateinit var currentStatusTextView: TextView
    private lateinit var currentAppImageView: ImageView
    private lateinit var btnStartTest: Button
    private lateinit var btnCancelTest: Button
    private lateinit var tvCarrier: TextView
    private lateinit var tvAppsCount: TextView
    private lateinit var tvTestType: TextView
    private lateinit var headerLayout: View
    private val doNothing = DialogInterface.OnClickListener { _, _ -> }

    // Service connection object
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            val binder = service as ReplayForegroundService.ReplayServiceBinder
            replayService = binder.getService()
            serviceBound = true

            // Observe service state
            setupServiceObservers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            serviceBound = false
            replayService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view
        setContentView(R.layout.activity_background_replay)

        // Setup UI elements
        setupUI()

        // Process Intent extras if coming from other activity
        processIntentExtras()

        // Setup WorkManager observer
        observeWorkStatus()
    }

    private fun setupUI() {
        // Get UI components
        progressBar = findViewById(R.id.progressBar)
        currentAppTextView = findViewById(R.id.tvCurrentApp)
        currentStatusTextView = findViewById(R.id.tvStatus)
        currentAppImageView = findViewById(R.id.headerImage)
        btnStartTest = findViewById(R.id.btnStartTest)
        btnCancelTest = findViewById(R.id.btnCancelTest)
        headerLayout = findViewById(R.id.headerLayout)
        instructionsText = findViewById(R.id.tvInstructions)

        // Setup toolbar
        val mToolbar = findViewById<Toolbar>(R.id.background_replay_bar)
        setSupportActionBar(mToolbar)
        supportActionBar?.apply {
            title = getString(R.string.background_tests)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Setup start test button
        btnStartTest.setOnClickListener {
            if (viewModel.isReplayOngoing.value == true) {
                Toast.makeText(this, "Test already running", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startBackgroundTest()
        }

        // Setup cancel button
        btnCancelTest.setOnClickListener {
            cancelBackgroundTest()
        }

        // Observe ViewModel state
        viewModel.isReplayOngoing.observe(this) { isRunning ->
            btnStartTest.isEnabled = !isRunning
            btnCancelTest.isEnabled = isRunning

            // Update UI to show test status
            updateRunningTestUI(isRunning)
        }

        // Observe test progress
        viewModel.progress.observe(this) { progress ->
            progressBar.progress = progress
        }

        // Observe current app
        viewModel.currentTestingApp.observe(this) { app ->
            app?.let {
                currentAppTextView.text = it.name
                val resourceId = resources.getIdentifier(it.image, "drawable", packageName)
                if (resourceId != 0) {
                    currentAppImageView.setImageResource(resourceId)
                }
            }
        }

        // Observe test status
        viewModel.status.observe(this) { statusPair ->
            currentStatusTextView.text = "${statusPair.first}: ${statusPair.second}"
        }

        // Observe test results
        viewModel.testResults.observe(this) { results ->
            displayResults(results)
        }
    }

    private fun processIntentExtras() {
        // Get extras from intent
        val bundle = intent.extras
        if (bundle != null) {
            val runPortTests = bundle.getBoolean("runPortTests", false)
            val carrier = bundle.getString("carrier")
            val selectedApps = intent.getParcelableArrayListExtra<ApplicationBean>("selectedApps")

            tvCarrier = findViewById(R.id.tvCarrier)
            tvCarrier.text = carrier

            tvAppsCount = findViewById(R.id.tvAppsCount)
            tvAppsCount.text = selectedApps?.size.toString()

            tvTestType = findViewById(R.id.tvTestType)
            tvTestType.text = if (runPortTests) {
                getString(R.string.port_test)
            } else {
                getString(R.string.diff_test)
            }

            if (selectedApps != null) {
                // Initialize the ViewModel with data
                viewModel.initializeData(runPortTests, carrier, selectedApps, applicationContext)
                Log.d(TAG, "Initialized with ${selectedApps.size} apps")

                // Check network before starting tests
                if (viewModel.isNetworkUnavailable(this)) {
                    Toast.makeText(this, "Network unavailable", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "No apps received in intent")
                Toast.makeText(this, "No apps selected for testing", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "No extras in intent")
        }
    }

    private fun observeWorkStatus() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData("replay_test")
            .observe(this, Observer { workInfoList ->
                if (workInfoList.isNullOrEmpty()) {
                    Log.d(TAG, "No work info available")
                    return@Observer
                }

                // Process work info state
                val workInfo = workInfoList[0]
                Log.d(TAG, "Work state: ${workInfo.state}")

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        Log.d(TAG, "Work enqueued")
                        viewModel.setReplayOngoing(true)
                    }
                    WorkInfo.State.RUNNING -> {
                        Log.d(TAG, "Work running")
                        viewModel.setReplayOngoing(true)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "WorkManager: Work completed successfully")
                        // The service should handle the completion
                    }
                    WorkInfo.State.FAILED -> {
                        Log.e(TAG, "Work failed")
                        viewModel.setReplayOngoing(false)
                        Toast.makeText(this, "Test failed", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.CANCELLED -> {
                        Log.d(TAG, "Work cancelled")
                        viewModel.setReplayOngoing(false)
                        Toast.makeText(this, "Test cancelled", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Log.d(TAG, "Other work state: ${workInfo.state}")
                    }
                }
            })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            replayStop()
        }
        return true
    }

    /**
     * User wants to leave the replay activity.
     */
    private fun replayStop() {
        if (viewModel.isReplayOngoing.value != true) {
            finish()
            overridePendingTransition(
                android.R.anim.slide_in_left, android.R.anim.slide_out_right
            )
        } else {
            android.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                .setPositiveButton(getString(android.R.string.yes)) { _, _ ->
                    finish()
                    overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                }
                .setNegativeButton(getString(android.R.string.no), doNothing)
                .show()
        }
    }

    private fun setupServiceObservers() {
        replayService?.let { service ->
            Log.d(TAG, "Setting up service observers")

            // Observe test progress
            service.isReplayOngoing.observe(this) {
                Log.d(TAG, "Service replay ongoing: $it")
                viewModel.setReplayOngoing(it)
            }

            // Observe current app being tested
            service.currentTestingApp.observe(this) { app ->
                Log.d(TAG, "Current testing app: ${app?.name}")
                viewModel.setCurrentTestingApp(app)
            }

            // Observe test progress
            service.progressUpdate.observe(this) { progress ->
                Log.d(TAG, "Progress update: $progress")
                viewModel.setProgress(progress)
            }

            // Observe test status
            service.statusUpdate.observe(this) { status ->
                Log.d(TAG, "Status update: ${status.first} ${status.second}")
                viewModel.setStatus(status)
            }

            // Observe test results
            service.testResults.observe(this) { results ->
                Log.d(TAG, "Test results received")
                viewModel.setTestResults(results)
            }

            // Observe test errors
            service.errorMessage.observe(this) { errorMsg ->
                errorMsg?.let {
                    Log.e(TAG, "Error message: $it")
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
        } ?: run {
            Log.e(TAG, "Service not available for observing")
        }
    }

    private fun startBackgroundTest() {
        val runPortTests = viewModel.runPortTests
        val carrier = viewModel.carrier
        val selectedApps = viewModel.selectedApps

        if (selectedApps.isNullOrEmpty()) {
            Toast.makeText(this, "No apps selected for testing", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Starting background test with ${selectedApps.size} apps")

        // Schedule the work using ReplayWorker
        val operation = ReplayWorker.scheduleReplayTest(
            applicationContext,
            runPortTests,
            carrier,
            selectedApps
        )

        operation.state.observe(this) { workState ->
            Log.d(TAG, "Work state: $workState")
        }

        // Bind to the service to receive updates
        val serviceIntent = Intent(this, ReplayForegroundService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        Toast.makeText(this, "Starting tests in background", Toast.LENGTH_SHORT).show()
    }

    private fun cancelBackgroundTest() {
        Log.d(TAG, "Cancelling background test")

        // Cancel through service if bound
        if (serviceBound && replayService != null) {
            Log.d(TAG, "Cancelling through service")
            replayService?.cancelTests()
        } else {
            // Cancel through WorkManager
            Log.d(TAG, "Cancelling through WorkManager")
            ReplayWorker.cancelAllReplayTests(applicationContext)
            viewModel.setReplayOngoing(false)
        }

        Toast.makeText(this, "Cancelling tests", Toast.LENGTH_SHORT).show()
    }

    private fun updateRunningTestUI(isRunning: Boolean) {
        // Update UI elements based on test status
        findViewById<View>(R.id.progressLayout).visibility = if (isRunning) View.VISIBLE else View.GONE
        headerLayout.visibility = if (isRunning) View.VISIBLE else View.GONE
        instructionsText.visibility = if (isRunning) View.VISIBLE else View.GONE
        btnStartTest.visibility = if (isRunning) View.GONE else View.VISIBLE
        btnCancelTest.visibility = if (isRunning) View.VISIBLE else View.GONE
        progressBar.progress = if (isRunning) View.VISIBLE else 0
    }

    private fun displayResults(results: Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>?) {
        if (results == null) return

        val (allApps, diffApps, inconclusiveApps) = results

        // Show results dialog
        val message = buildString {
            append("Go to Previous Results to get detailed results.\n\n")
            append("Total apps tested: ${allApps.size}\n")
            append("Apps with differentiation: ${diffApps.size}\n")
            append("Apps without differentiation: ${allApps.size-inconclusiveApps.size-diffApps.size}\n")
            append("Inconclusive tests: ${inconclusiveApps.size}\n")
        }

        AlertDialog.Builder(this)
            .setTitle("Test Results")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        // Bind to the service if tests are ongoing
        if (viewModel.isReplayOngoing.value == true && !serviceBound) {
            Log.d(TAG, "Binding to service on start")
            val serviceIntent = Intent(this, ReplayForegroundService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        // Unbind from service when activity is not visible
        if (serviceBound) {
            Log.d(TAG, "Unbinding from service")
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        private const val TAG = "BackgroundReplayActivity"
    }
}