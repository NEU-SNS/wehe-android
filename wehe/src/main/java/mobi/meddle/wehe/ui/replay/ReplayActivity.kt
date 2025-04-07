package mobi.meddle.wehe.ui.replay

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.Toast
import android.app.AlertDialog
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter
import mobi.meddle.wehe.data.model.ApplicationBean

@AndroidEntryPoint
class ReplayActivity : AppCompatActivity() {

    private val viewModel: ReplayViewModel by viewModels()
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: ImageReplayRecyclerViewAdapter
    private lateinit var headerLayout: LinearLayout
    private lateinit var headerImage: ImageView
    private lateinit var headerText: TextView
    private val doNothing = DialogInterface.OnClickListener { _, _ -> }

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
                adapter = ImageReplayRecyclerViewAdapter(selectedApps, this, runPortTests)
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
                    viewModel.execute()
                }
            }
        }

        headerLayout = findViewById(R.id.headerLayout)
        headerImage = findViewById(R.id.headerImage)
        headerText = findViewById(R.id.headerText)

        // Observe LiveData from ViewModel
        setupObservers()
    }

    private fun setupObservers() {
        // Observe isReplayOngoing state
        viewModel.isReplayOngoing.observe(this) { isOngoing ->
            // Update UI based on replay state if needed
        }

        // Observe status updates
        viewModel.statusUpdateEvent.observe(this) { (appName, status) ->
            // Update status of app in adapter
            adapter.notifyDataSetChanged()
        }

        viewModel.currentTestingApp.observe(this) { appInfo ->
            if (appInfo != null) {

                val resourceId = resources.getIdentifier(appInfo.image, "drawable", packageName)

                // Update image based on current app
                if (appInfo.image != null) {
                    headerImage.setImageResource(resourceId)
                    headerLayout.visibility = View.VISIBLE
                }
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
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.isReplayOngoing.value == true) {
            viewModel.cancel()
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
            AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
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

    /**
     * Display buttons for rerunning tests or conducting tomography tests
     */
    private fun displayRerunTomoButtons() {
        // Set rerun button to be visible if there are differentiation or inconclusive apps
        val rerunButton = findViewById<Button>(R.id.rerunButton)
        rerunButton.visibility = View.VISIBLE
        rerunButton.setOnClickListener { showRerunDialog() }

//        // Show tomography button if there are apps with differentiation and not in tomography mode
//        if (!viewModel.isTomography && viewModel.diffApps.size > 0) {
//            val runTomoButton = findViewById<Button>(R.id.localizeDiffButton)
//            runTomoButton.visibility = View.VISIBLE
//            runTomoButton.setOnClickListener { showTomographyDialog() }
//        }

        // Rearrange layout so progress bar disappears
        val params = findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ABOVE, R.id.actionBtnsLayout)
        findViewById<View>(R.id.prgBar).visibility = View.GONE
    }

    /**
     * Show dialog to rerun tests
     */
    private fun showRerunDialog() {
        val alertDialog = AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
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
        supportActionBar?.title = getString(R.string.replay_page_title)

        // Rearrange layout to hide rerun button
        val params = findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.BELOW, R.id.prgBarLayout)
        findViewById<View>(R.id.rerunButton).visibility = View.GONE
        findViewById<View>(R.id.localizeDiffButton).visibility = View.GONE

        // Update adapter and prepare viewModel
        adapter.setTomography(false)
        val newApps = viewModel.prepareRerunTests(isRunningDifferentiation)
        adapter.updateApps(newApps)
        viewModel.execute()
    }

//    /**
//     * Show dialog to run tomography tests
//     */
//    private fun showTomographyDialog() {
//        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
//            .setTitle(R.string.localize_diff)
//            .setMessage(R.string.rerun_tomography_descr)
//            .setPositiveButton(R.string.yes) { _, _ ->
//                prepareTomographyTests()
//            }
//            .setNegativeButton(R.string.no, doNothing)
//            .create()
//            .show()
//    }

//    /**
//     * Prepare UI and ViewModel for tomography tests
//     */
//    private fun prepareTomographyTests() {
//        // Change page title
//        supportActionBar?.title = getString(R.string.tomography_page_title)
//
//        // Rearrange layout to hide rerun button
//        val params = findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
//        params.addRule(RelativeLayout.ABOVE, R.id.prgBarLayout)
//        findViewById<View>(R.id.rerunButton).visibility = View.GONE
//        findViewById<View>(R.id.localizeDiffButton).visibility = View.GONE
//
//        // Update adapter for tomography tests
//        adapter.setTomography(true)
//
//        // Prepare and execute tomography tests
//        viewModel.prepareTomographyTests()
//    }

    /**
     * Force alert dialog to center align buttons.
     */
    private fun centerAlignButton(dialog: AlertDialog, button: Int) {
        val b = dialog.getButton(button)
        val params = b.layoutParams as LinearLayout.LayoutParams
        params.gravity = Gravity.CENTER
        b.layoutParams = params
    }

    companion object {
        const val STATUS: String = "ReplayActPrefsFile"
    }
}