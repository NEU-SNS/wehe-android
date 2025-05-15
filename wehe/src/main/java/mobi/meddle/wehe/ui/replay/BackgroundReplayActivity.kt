package mobi.meddle.wehe.ui.replay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

    // Service connection object
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReplayForegroundService.ReplayServiceBinder
            replayService = binder.getService()
            serviceBound = true

            // Observe service state
            setupServiceObservers()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            replayService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_background_replay)

        // Setup UI elements
        setupUI()

        // Process Intent extras if coming from other activity
        processIntentExtras()

        // Setup WorkManager observer
        observeWorkStatus()
    }

    private fun setupUI() {
        // Setup start test button
        findViewById<Button>(R.id.btnStartTest).setOnClickListener {
            if (viewModel.isReplayOngoing.value == true) {
                Toast.makeText(this, "Test already running", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startBackgroundTest()
        }

        // Setup cancel button
        findViewById<Button>(R.id.btnCancelTest).setOnClickListener {
            cancelBackgroundTest()
        }

        // Observe ViewModel state
        viewModel.isReplayOngoing.observe(this) { isRunning ->
            findViewById<Button>(R.id.btnStartTest).isEnabled = !isRunning
            findViewById<Button>(R.id.btnCancelTest).isEnabled = isRunning

            // Update UI to show test status
            updateRunningTestUI(isRunning)
        }

        // Observe test results
        viewModel.testResults.observe(this) { results ->
            displayResults(results)
        }
    }

    private fun processIntentExtras() {
        intent?.extras?.let { extras ->
            val runPortTests = extras.getBoolean("runPortTests", false)
            val carrier = extras.getString("carrier")
//            val selectedApps = extras.getParcelableArrayListExtra<ApplicationBean>("selectedApps")

//            if (selectedApps != null) {
//                viewModel.setTestParameters(runPortTests, carrier, selectedApps)
//            }
        }
    }

    private fun observeWorkStatus() {
        WorkManager.getInstance(applicationContext)
            .getWorkInfosByTagLiveData("replay_test")
            .observe(this, Observer { workInfoList ->
                if (workInfoList.isNullOrEmpty()) return@Observer

                // Process work info state
                val workInfo = workInfoList[0]
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        viewModel.setReplayOngoing(true)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "WorkManager: Work completed successfully")
                    }
                    WorkInfo.State.FAILED -> {
                        viewModel.setReplayOngoing(false)
                        Toast.makeText(this, "Test failed", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.CANCELLED -> {
                        viewModel.setReplayOngoing(false)
                        Toast.makeText(this, "Test cancelled", Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            })
    }

    private fun setupServiceObservers() {
        replayService?.let { service ->
            // Observe test progress
            service.isReplayOngoing.observe(this) {
                viewModel.setReplayOngoing(it)
            }

            // Observe current app being tested
            service.currentTestingApp.observe(this) { app ->
                viewModel.setCurrentTestingApp(app)
            }

            // Observe test progress
            service.progressUpdate.observe(this) { progress ->
                viewModel.setProgress(progress)
            }

            // Observe test status
            service.statusUpdate.observe(this) { status ->
                viewModel.setStatus(status)
            }

            // Observe test results
            service.testResults.observe(this) { results ->
                viewModel.setTestResults(results)
            }

            // Observe test errors
            service.errorMessage.observe(this) { errorMsg ->
                errorMsg?.let {
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                }
            }
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

        // Schedule the work using ReplayWorker
        ReplayWorker.scheduleReplayTest(
            applicationContext,
            runPortTests,
            carrier,
            ArrayList(selectedApps)
        ).state.observe(this) { workState ->
            Log.d(TAG, "Work state: $workState")
        }

        // Bind to the service to receive updates
        val serviceIntent = Intent(this, ReplayForegroundService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        Toast.makeText(this, "Starting tests in background", Toast.LENGTH_SHORT).show()
    }

    private fun cancelBackgroundTest() {
        // Cancel through service if bound
        if (serviceBound && replayService != null) {
            replayService?.cancelTests()
        } else {
            // Cancel through WorkManager
            ReplayWorker.cancelAllReplayTests(applicationContext)
            viewModel.setReplayOngoing(false)
        }

        Toast.makeText(this, "Cancelling tests", Toast.LENGTH_SHORT).show()
    }

    private fun updateRunningTestUI(isRunning: Boolean) {
        // Update UI elements based on test status
        findViewById<View>(R.id.progressLayout).visibility = if (isRunning) View.VISIBLE else View.GONE

        // Update current app info if available
        viewModel.currentTestingApp.value?.let { app ->
            val resourceId = resources.getIdentifier(app.image, "drawable", packageName)
            // Update your UI with the app info and image
        }
    }

    private fun displayResults(results: Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>?) {
        if (results == null) return

        val (allApps, diffApps, inconclusiveApps) = results

        // Show results dialog
        val message = buildString {
            append("Tests completed\n\n")
            append("Total apps tested: ${allApps.size}\n")
            append("Apps with differentiation: ${diffApps.size}\n")
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
        // Bind to the service if tests are ongoing
        if (viewModel.isReplayOngoing.value == true && !serviceBound) {
            val serviceIntent = Intent(this, ReplayForegroundService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        // Unbind from service when activity is not visible
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    companion object {
        private const val TAG = "BackgroundReplayActivity"
    }
}