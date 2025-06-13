package mobi.meddle.wehe.ui.replay

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.ui.main.MainActivity

@AndroidEntryPoint
class BackgroundReplayActivity : AppCompatActivity() {

    private val viewModel: BackgroundReplayViewModel by viewModels()
    private var replayService: ReplayForegroundService? = null
    private var serviceBound = false
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ImageReplayRecyclerViewAdapter
    private lateinit var headerLayout: LinearLayout
    private lateinit var progressBarLayout: LinearLayout
    private lateinit var headerImage: ImageView
    private lateinit var headerText: TextView
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
        setContentView(R.layout.activity_replay)

        // Setup toolbar
        val mToolbar = findViewById<Toolbar>(R.id.replay_bar)
        setSupportActionBar(mToolbar)
        supportActionBar?.apply {
            title = getString(R.string.replay_page_title)
            setHomeButtonEnabled(true)
            setDisplayHomeAsUpEnabled(true)
        }

        // Keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get extras from intent
        val bundle = intent.extras
        if (bundle != null) {
            val runPortTests = bundle.getBoolean("runPortTests")
            val carrier = bundle.getString("carrier")
            val selectedApps = intent.getParcelableArrayListExtra<ApplicationBean>("selectedApps")

            if (selectedApps != null) {
                // Initialize the ViewModel with data
                viewModel.initializeData(runPortTests, carrier, selectedApps, applicationContext)

                // Setup RecyclerView
                adapter = ImageReplayRecyclerViewAdapter(this, selectedApps, this, runPortTests)
                val appsRecyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView)
                val layoutManager = LinearLayoutManager(this)
                appsRecyclerView.layoutManager = layoutManager
                appsRecyclerView.adapter = adapter

                // Setup progress bar
                progressBar = findViewById(R.id.prgBar)

                // Check network before starting tests
                if (viewModel.isNetworkUnavailable(this)) {
                    viewModel.showNoNetworkDialog()
                } else {
                    startBackgroundTest()
                }
            }
        }

        headerLayout = findViewById(R.id.headerLayout)
        progressBarLayout = findViewById(R.id.prgBarLayout)
        headerImage = findViewById(R.id.headerImage)
        headerText = findViewById(R.id.headerText)

        // Observe LiveData from ViewModel
        setupObservers()

        // Setup WorkManager observer
        observeWorkStatus()
    }

    private fun setupObservers() {
        // Observe status updates
        viewModel.statusUpdateEvent.observe(this) { (app, status) ->
            Log.d(TAG, "ViewModel - Status update for ${app.name}: $status")
            // Force adapter to refresh immediately
            runOnUiThread {
                adapter.notifyDataSetChanged()
            }
        }

        viewModel.currentTestingApp.observe(this) { appInfo ->
            if (appInfo != null) {
                val resourceId = resources.getIdentifier(appInfo.image, "drawable", packageName)

                // Update image based on current app
                if (appInfo.image != null) {
                    headerImage.setImageResource(resourceId)
                    headerLayout.visibility = View.VISIBLE
                    progressBarLayout.visibility = View.VISIBLE
                }
            }

            if (appInfo == null) {
                headerLayout.visibility = View.GONE
                progressBarLayout.visibility = View.GONE
            }
        }

        viewModel.iteration.observe(this) { iter ->
            if (iter != null) {
                headerText.text = getString(R.string.replay_header_text, iter.toString())
                headerLayout.visibility = View.VISIBLE
            }
        }

        // Observe progress updates
        viewModel.progressUpdateEvent.observe(this) { progress ->
            if (progressBar.visibility == View.GONE || progressBar.visibility == View.INVISIBLE) {
                progressBar.visibility = View.VISIBLE
            }
            progressBar.progress = progress
        }

        // Observe progress completion events
        viewModel.progressCompleteEvent.observe(this) { iteration ->
            if (iteration == 1) {
                progressBar.progress = 50
            } else {
                progressBar.progress = 100
                progressBar.visibility = View.GONE
            }
        }

        // Observe toast messages
        viewModel.toastEvent.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        // Observe dialog events
        viewModel.dialogEvent.observe(this) { (title, message, exitReplays) ->
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
                    if (exitReplays) {
                        replayStop()
                    } else {
                        // All tests just finished, check if we need to display rerun buttons
                        if (viewModel.diffApps.size != 0 || viewModel.inconclusiveApps.size != 0) {
                            viewModel.showRerunTomoButtonsEvent.value?.let {
                                if (it) displayRerunTomoButtons()
                            }
                        }
                    }
                }.show()

            if (exitReplays) {
                supportActionBar?.setTitle(R.string.simple_error)
            } else {
                supportActionBar?.setTitle(R.string.test_results)
            }
        }

        // Observe rerun tomography buttons event
        viewModel.showRerunTomoButtonsEvent.observe(this) { show ->
            if (show) displayRerunTomoButtons()
        }

        // update apps list in adapter
        viewModel.appsList.observe(this) { apps ->
            adapter.updateApps(ArrayList(apps))
        }

        // Observe test status
        viewModel.isReplayOngoing.observe(this) { isRunning ->
            // Update UI based on replay state if needed
        }
    }

    private fun setupServiceObservers() {
        replayService?.let { service ->
            Log.d(TAG, "Setting up service observers")

            // Sync ViewModel with service
            viewModel.syncWithService(service)

            // Direct service observations for immediate UI updates
            service.currentTestingApp.observe(this) { app ->
                Log.d(TAG, "Service - Current testing app: ${app?.name}")
                // ViewModel sync will handle this, but we can add immediate UI updates here if needed
            }

            service.progressUpdate.observe(this) { progress ->
                Log.d(TAG, "Service - Progress update: $progress")
                // ViewModel sync handles this
            }

            service.statusUpdate.observe(this) { (appName, status) ->
                Log.d(TAG, "Service - Status update: $appName -> $status")
                // Force adapter refresh to show status changes
                runOnUiThread {
                    adapter.notifyDataSetChanged()
                }
            }

            service.testResults.observe(this) { results ->
                Log.d(TAG, "Service - Test results received")
                // ViewModel sync handles this
            }

            service.errorMessage.observe(this) { errorMsg ->
                errorMsg?.let {
                    Log.e(TAG, "Service - Error message: $it")
                    Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                    if (errorMsg.equals(getString(R.string.server_unavailable), ignoreCase = true)) {
                        supportActionBar?.setTitle(R.string.test_results)
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "Service not available for observing")
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

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.isReplayOngoing.value == true) {
            cancelBackgroundTest()
            Toast.makeText(
                this, getText(R.string.replay_aborted),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged()
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
            AlertDialog.Builder(this)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            replayStop()
            return if (viewModel.isReplayOngoing.value != true) {
                super.onKeyDown(keyCode, event)
            } else {
                true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            replayStop()
        }
        return true
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
            unbindService(serviceConnection)
            serviceBound = false
        } else {
            // Cancel through WorkManager
            Log.d(TAG, "Cancelling through WorkManager")
            ReplayWorker.cancelAllReplayTests(applicationContext)
            viewModel.setReplayOngoing(false)
        }

        Toast.makeText(this, "Cancelling tests", Toast.LENGTH_SHORT).show()
    }

    /**
     * Display buttons for rerunning tests or conducting tomography tests
     */
    private fun displayRerunTomoButtons() {
        // Set rerun button to be visible if there are differentiation or inconclusive apps
        val rerunButton = findViewById<Button>(R.id.rerunButton)
        rerunButton.visibility = View.VISIBLE
        rerunButton.setOnClickListener { showRerunDialog() }

        // Rearrange layout so progress bar disappears
        val params = findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ABOVE, R.id.actionBtnsLayout)
        findViewById<View>(R.id.prgBarLayout).visibility = View.GONE
    }

    /**
     * Show dialog to rerun tests
     */
    private fun showRerunDialog() {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(R.string.rerun_test_title)
            .setMessage(R.string.rerun_test_descr)

        // Rerun tests with differentiation
        if (viewModel.diffApps.size != 0) {
            alertDialog.setPositiveButton(R.string.rerun_diff_opt) { _, _ ->
                prepareForRerun(true)
            }
        }

        // Rerun only the inconclusive tests
        if (viewModel.inconclusiveApps.size != 0) {
            alertDialog.setNegativeButton(R.string.rerun_incon_opt) { _, _ ->
                prepareForRerun(false)
            }
        }

        alertDialog.setNeutralButton(android.R.string.cancel, doNothing)
        val dialog = alertDialog.create()
        dialog.show()

        // Center align buttons
        if (viewModel.diffApps.size != 0) {
            centerAlignButton(dialog, AlertDialog.BUTTON_POSITIVE)
        }
        if (viewModel.inconclusiveApps.size != 0) {
            centerAlignButton(dialog, AlertDialog.BUTTON_NEGATIVE)
        }
        centerAlignButton(dialog, AlertDialog.BUTTON_NEUTRAL)
    }

    /**
     * Prepare UI and ViewModel for rerun tests
     */
    private fun prepareForRerun(isRunningDifferentiation: Boolean) {
        // Change page title
        supportActionBar?.title = getString(R.string.background_tests)

        // Rearrange layout to hide rerun button
        val params = findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.BELOW, R.id.prgBarLayout)
        findViewById<View>(R.id.rerunButton).visibility = View.GONE
        findViewById<View>(R.id.localizeDiffButton).visibility = View.GONE

        // Update adapter and prepare viewModel
        adapter.setTomography(false)
        val newApps = viewModel.prepareRerunTests(isRunningDifferentiation)
        adapter.updateApps(newApps)
        startBackgroundTest()
    }

    /**
     * Force alert dialog to center align buttons.
     */
    private fun centerAlignButton(dialog: AlertDialog, button: Int) {
        val b = dialog.getButton(button)
        val params = b.layoutParams as LinearLayout.LayoutParams
        params.gravity = Gravity.CENTER
        b.layoutParams = params
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

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle updates if needed (optional)
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
//    // Unbind from service when activity is not visible
//        if (serviceBound) {
//            Log.d(TAG, "Unbinding from service")
//            unbindService(serviceConnection)
//            serviceBound = false
//        }
    }

    companion object {
        private const val TAG = "BackgroundReplayActivity"
    }
}