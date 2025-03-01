package mobi.meddle.wehe.ui.replay

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter
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
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Objects
import java.util.Random
import java.util.Timer
import javax.websocket.DeploymentException
import kotlin.coroutines.coroutineContext
import kotlin.math.abs


/**
 * Runs the replays.
 * XML layout: activity_replay.xml
 * adapter.ImageReplayRecyclerViewAdapter.java for layout of each replay
 */
class ReplayActivity : AppCompatActivity() {
    // Methods for managing replayOngoing state
    var isReplayOngoing: Boolean = false
    var selectedApps: ArrayList<ApplicationBean>? = null //apps to run
        private set
    @JvmField
    val diffApps: ArrayList<ApplicationBean> = ArrayList() //apps with differentiation
    @JvmField
    val inconclusiveApps: ArrayList<ApplicationBean> = ArrayList()

    // Access to UI elements and app data
    var progressBar: ProgressBar? = null //progress bar at bottom of screen when tests are running
        private set
    var adapter: ImageReplayRecyclerViewAdapter? = null //layout for each replay
        private set
    private var context: Context? = null
    var runPortTests: Boolean = false
        private set

    var carrier: String? = null //carrier to display in results
        private set

    //Tomography tests determine where exactly in the network differentiation occurs. If differentiation
    //is detected in a test, the app will ask users if they want to run a tomography test. These
    //tests run 3 concurrent tests to 3 optimal MLab servers. Based on the times the packets are sent,
    //an algorithm can determine where differentiation occurs. All 3 of the tests count as one Wehe
    //"Test", so 1 historyCount is used for all 3 tests.
    var isTomography: Boolean = false //true if tomography test, false if normal test

    private var job: Job? = null

    // Data for the app being tested
    private var appData: CombinedAppJSONInfoBean? = null
    private var app: ApplicationBean? = null
    private val servers = ArrayList<String?>() //servers to run the replays to
    private var metadataServer: String? = null
    private val wsConns = ArrayList<WebSocketConnection>()
    private var updateUIBean: UpdateUIBean? = null
    private var doTest = false //add a tail for testing data if true
    private val analyzerServerUrls = ArrayList<String>()

    //true if confirmation replay should run if the first replay has differentiation
    private var confirmationReplays = false
    private var useDefaultThresholds = false
    private var a_threshold = 0
    private var ks2pvalue_threshold = 0
    private var settings: SharedPreferences? = null

    //randomID, historyCount, and testId identifies the user, test number, and replay number
    //server uses these to determine which results to send back to client
    private var randomID: String? = null //unique user ID for certain device

    //historyCount is the test number; current number can be seen as number of apps run
    //or number of times user hit the run button for ports
    private var historyCount = 0

    //testId is replay number in a test
    //for apps - 0 is original replay, 1 is random replay
    //for ports - 0 non-443 port, 1 is port 443
    private var testId = 0
    private var results: JSONArray? = null //results containing apps or the port arrays (below)
    private val timers = ArrayList<Timer>() //for stopping sendRequest timers
    private val numMLab = ArrayList<Int>() //number of tries before successful MLab connection
    private var mlabServerUsed = false
    private var serverDisplay: String? = null
    private var isIPv6 = false

    private val uiUpdateJobs = ArrayList<Job>()
    private var serverRepository : ServerRepository? = null

    private val doNothing =
        DialogInterface.OnClickListener { _, _ -> }

    //this happens if rerun differentiation or rerun inconclusive buttons clicked
    private val rerunButtons =
        DialogInterface.OnClickListener { _, which -> //change page title
            Objects.requireNonNull(supportActionBar)?.title =
                getString(R.string.replay_page_title)
            if (which == DialogInterface.BUTTON_POSITIVE) {
                selectedApps = ArrayList(diffApps)
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                selectedApps = ArrayList(inconclusiveApps)
            }
            //rearrange layout to hide rerun button
            val params =
                findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
            params.addRule(RelativeLayout.ABOVE, R.id.prgBarLayout)
            findViewById<View>(R.id.rerunButton).visibility = View.GONE
            findViewById<View>(R.id.localizeDiffButton).visibility =
                View.GONE
            adapter!!.setTomography(false)
            isTomography = false
            for (app in selectedApps!!) {
                app.isTomography = false
                app.arcepNeedsAlerting = false
                app.isAlertFCC = false
                app.status = getString(R.string.pending)
            }
            inconclusiveApps.clear()
            diffApps.clear()
            execute()
        }

    //rerun dialogue
    private val rerunListener = View.OnClickListener { //dialogue box to rerun tests
        val alertDialog = AlertDialog.Builder(
            this@ReplayActivity,
            AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
        )
            .setTitle(R.string.rerun_test_title)
            .setMessage(R.string.rerun_test_descr)
        //rerun tests with differentiation
        if (diffApps.size != 0) {
            alertDialog.setPositiveButton(R.string.rerun_diff_opt, rerunButtons)
        }
        //rerun only the inconclusive tests; doesn't appear if no tests inconclusive
        if (inconclusiveApps.size != 0) {
            alertDialog.setNegativeButton(R.string.rerun_incon_opt, rerunButtons)
        }
        alertDialog.setNeutralButton(android.R.string.cancel, doNothing) //cancel button
        val dialog = alertDialog.create()
        dialog.show()
        if (diffApps.size != 0) {
            centerAlignButton(dialog, AlertDialog.BUTTON_POSITIVE)
        }
        if (inconclusiveApps.size != 0) {
            centerAlignButton(dialog, AlertDialog.BUTTON_NEGATIVE)
        }
        centerAlignButton(dialog, AlertDialog.BUTTON_NEUTRAL)
    }

    //run tomography dialogue
    private val runTomoListener = View.OnClickListener {
        //automatically pops up when tests end if some tests have differentiation
        //asks if user wants to run tomography test
        AlertDialog.Builder(
            this@ReplayActivity,
            AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
        )
            .setTitle(R.string.localize_diff)
            .setMessage(R.string.rerun_tomography_descr)
            .setPositiveButton(
                R.string.yes
            ) { dialog, which ->
                Objects.requireNonNull(
                    supportActionBar
                )?.title = getString(R.string.tomography_page_title)
                //rearrange layout to hide rerun button
                val params =
                    findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
                params.addRule(RelativeLayout.ABOVE, R.id.prgBarLayout)
                findViewById<View>(R.id.rerunButton).visibility =
                    View.GONE
                findViewById<View>(R.id.localizeDiffButton).visibility =
                    View.GONE
                //run tomography tests if user clicks yes
                isTomography = true
                adapter!!.setTomography(true)
                selectedApps = ArrayList(diffApps)
                for (app in selectedApps!!) {
                    app.isTomography = true
                    app.arcepNeedsAlerting = false
                    app.isAlertFCC = false
                    app.status = getString(R.string.pending)
                }
                execute()
            }
            .setNegativeButton(R.string.no, doNothing).create().show()
    }

    /**
     * Force alert dialog to center align buttons.
     *
     * @param dialog the dialog to align
     * @param button the button to align
     */
    private fun centerAlignButton(dialog: AlertDialog, button: Int) {
        val b = dialog.getButton(button)
        val params = b.layoutParams as LinearLayout.LayoutParams
        params.gravity = Gravity.CENTER
        b.layoutParams = params
    }

    /**
     * When tests are finished and there are tests with differentiation or inconclusive tests, show
     * the rerun button, which allow users to rerun these tests. Also, if there is differentiation,
     * show the Localize Differentiation button to allow users to run tomography tests.
     */
    private fun displayRerunTomoButtons() {
        //set rerun button to be visible if differentiation or inconclusive apps
        val rerunButton = findViewById<Button>(R.id.rerunButton)
        rerunButton.visibility = View.VISIBLE
        rerunButton.setOnClickListener(rerunListener)
        //TODO: uncomment to allow users to run tomography tests

//        if (!isTomography && diffApps.size > 0) { //show tomography button if necessary
//            val runTomoButton : Button = findViewById(R.id.localizeDiffButton);
//            runTomoButton.visibility = View.VISIBLE;
//            runTomoButton.setOnClickListener(runTomoListener);
//        }

        //rearrange layout so progress bar disappears
        val params =
            findViewById<View>(R.id.appsRecyclerView).layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ABOVE, R.id.actionBtnsLayout)
        findViewById<View>(R.id.prgBar).visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //entry point coming from SelectionFragment
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_replay)
        val mToolbar = findViewById<Toolbar>(R.id.replay_bar)
        serverRepository = ServerRepository(this@ReplayActivity)
        setSupportActionBar(mToolbar)
        if (supportActionBar != null) {
            supportActionBar!!.title = getString(R.string.replay_page_title)
            supportActionBar!!.setHomeButtonEnabled(true)
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }
        // keep the screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // First check to see of Internet access is available
        // TODO integrate this with system events like WIFI_STATUS changes same for LTE,
        //  based on events received show appropriate messages to the user
        if (isNetworkUnavailable) {
            displayNoNetworkDialogue()
        }
        //get stuff from SelectionFragment
        val bundle = checkNotNull(intent.extras)
        runPortTests = bundle.getBoolean("runPortTests")
        carrier = bundle.getString("carrier")
        selectedApps = intent.getParcelableArrayListExtra("selectedApps")
        checkNotNull(selectedApps)
        for (app in selectedApps!!) {
            app.status = getString(R.string.pending)
        }
        //set the view for each selected app
        adapter = ImageReplayRecyclerViewAdapter(selectedApps!!, this, runPortTests)
        val appsRecyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView)
        val appsRecyclerViewLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        appsRecyclerView.layoutManager = appsRecyclerViewLayoutManager
        appsRecyclerView.adapter = adapter
        progressBar = findViewById(R.id.prgBar)
        context = applicationContext
        // This is the core of the Application
        if (!isNetworkUnavailable) {
            execute()
        }
    }

    override fun onDestroy() {
        //does this before going back to SelectionFragment
        super.onDestroy()
        if (isReplayOngoing) {
            cancel()
            Toast.makeText(
                this@ReplayActivity, getText(R.string.replay_aborted),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    public override fun onResume() {
        super.onResume()
        adapter!!.notifyDataSetChanged()
    }

    private val isNetworkUnavailable: Boolean
        /**
         * This Method checks the network Availability. For this NetworkInfo class is used and this
         * should also provide type of connectivity i.e. Wi-Fi, Cellular ..
         *
         * @return true if network is available, false otherwise
         */
        get() {
            val connectivityManager =
                getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
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
     * Display this popup message if there is no network
     */
    private fun displayNoNetworkDialogue() {
        this@ReplayActivity.runOnUiThread {
            AlertDialog.Builder(
                this@ReplayActivity,
                AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
            )
                .setTitle(getString(R.string.network_error))
                .setMessage(getString(R.string.text_network_error))
                .setPositiveButton(
                    android.R.string.ok
                ) { dialog, which -> replayStop() }.show()
        }
    }

    /**
     * User wants to leave the replay activity.
     */
    private fun replayStop() {
        if (!isReplayOngoing) {
            this@ReplayActivity.finish() //calls onDestroy
            this@ReplayActivity.overridePendingTransition(
                android.R.anim.slide_in_left, android.R.anim.slide_out_right
            )
        } else {
            AlertDialog.Builder(
                this@ReplayActivity,  //pop up box
                AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
            )
                .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                .setPositiveButton(
                    getString(android.R.string.yes)
                )  //yes button
                { dialog, which -> //calls onDestroy
                    this@ReplayActivity.finish() //go back to SelectionFragment
                    this@ReplayActivity.overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right
                    )
                }
                .setNegativeButton(getString(android.R.string.no), doNothing).show() //no button
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            replayStop()
            return if (!isReplayOngoing) {
                super.onKeyDown(keyCode, event)
            } else {
                true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        //the left arrow at the top of the screen - user wants to go back
        if (item.itemId == android.R.id.home) {
            replayStop()
        }
        return true
    }

    /**
     * Start the trace run in a coroutine
     */
    private fun execute() {
        isReplayOngoing = true

        job = CoroutineScope(Dispatchers.Main).launch {
            try {
                withContext(Dispatchers.IO) {
                    doInBackground()
                }
            } catch (e: Exception) {
                Log.e("TraceRun", "Error in coroutine", e)
            } finally {
                isReplayOngoing = false
            }
        }
    }

    /**
     * Cancel the running coroutine
     */
    private fun cancel() {
        job?.cancel()

        // Cancel all UI update jobs
        for (job in uiUpdateJobs) {
            job.cancel()
        }
        uiUpdateJobs.clear()

        isReplayOngoing = false
        Log.i("Replay", "Forced exit")
    }

    /**
     * Update UI from the Main thread
     */
    private suspend fun updateProgress(vararg values: String?) {
        withContext(Dispatchers.Main) {
            when {
                values[0].equals("updateStatus", ignoreCase = true) -> {
                    //values[1] is app name, values[2] is the status
                    if (values[1] == app!!.name) { //make status is being applied to correct app
                        app!!.status = values[2]
                    } else {
                        for (a in selectedApps!!) {
                            if (values[1] == a.name) {
                                a.status = values[2]
                                break
                            }
                        }
                    }
                    adapter!!.notifyDataSetChanged()
                }
                values[0].equals("updateUI", ignoreCase = true) -> {
                    //update progress bar
                    val prgBar = progressBar
                    if (prgBar!!.visibility == View.GONE || prgBar.visibility == View.INVISIBLE) {
                        prgBar.visibility = View.VISIBLE
                    }
                    prgBar.progress = updateUIBean!!.progress
                }
                values[0].equals("finishProgress", ignoreCase = true) -> {
                    //values[1] is 1 if just finished first replay; 2 if finished second replay
                    val iteration = values[1]?.toInt()
                    values[1]?.let { updateUIBean!!.finishProgress(it.toInt()) }
                    val prgBar = progressBar
                    if (iteration == 1) { //finished first replay
                        prgBar!!.progress = 50
                    } else { //finished test
                        prgBar!!.progress = 100
                        //hide progress bar
                        prgBar.visibility = View.GONE
                    }
                }
                values[0].equals("makeToast", ignoreCase = true) -> {
                    Toast.makeText(this@ReplayActivity, values[1], Toast.LENGTH_LONG).show()
                }
                values[0].equals("makeDialog", ignoreCase = true) -> {
                    //Display dialogue when replays finished or if there is an error that needs pop up.
                    //values[1] is title of dialogue, values[2] is message of dialogue
                    //values[3] true if the app should go back to the SelectionFragment when the user
                    //          clicks OK; else the app stays in the ReplayActivity
                    val exitReplays = values[3].toBoolean()
                    AlertDialog.Builder(
                        this@ReplayActivity,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
                    )
                        .setTitle(values[1])
                        .setMessage(values[2])
                        .setPositiveButton(getString(android.R.string.ok)) { dialog, which ->
                            if (exitReplays) {
                                replayStop()
                            }
                            //All tests just finished
                            if (diffApps.size != 0 ||
                                inconclusiveApps.size != 0
                            ) {
                                displayRerunButtons()
                            }
                        }.show()
                    if (exitReplays) {
                        Objects.requireNonNull(supportActionBar)?.setTitle(R.string.simple_error)
                    } else {
                        Objects.requireNonNull(supportActionBar)?.setTitle(R.string.test_results)
                    }
                }
                else -> {
                    Log.e("updateProgress", "unknown instruction!")
                }
            }
        }
    }

    /**
     * Reset the progress bar to 0. Need to reset the actual progress bar and the bean keeping
     * track of the progress.
     */
    private fun clearProgressBar() {
        progressBar!!.progress = 0
        updateUIBean!!.clearProgress()
    }

    /**
     * This method begins process to run tests.
     * Step 1: Initialize several variables.
     * Step 2: Run tests.
     * Step 3: Save results.
     */
    private suspend fun doInBackground(): Void? {
        //set each app's status to "Waiting"
        for (app in selectedApps!!) {
            app.status = resources.getString(R.string.pending)
        }

        // Keep checking if the job was cancelled
        if (!isActive) {
            return null
        }

        if (isNetworkUnavailable) {
            withContext(Dispatchers.Main) {
                displayNoNetworkDialogue()
            }
            return null
        }

        /*
         * Step 1: Initialize several variables.
         */
        updateUIBean = UpdateUIBean() // TODO remove this but with caution
        // TODO Test different configs by changing properties in assets.configuration.properties
        //  avoid changing the code directly
        context?.let { Config.readConfigFile(Consts.CONFIG_FILE, it) }

        //get settings from SettingsFragment
        val sharedPrefs = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        serverDisplay =
            if (isTomography) { //need to use MLab servers for tomography tests
                Consts.DEFAULT_SERVER
            } else {
                sharedPrefs?.getString(
                    getString(R.string.pref_server_key),
                    Consts.DEFAULT_SERVER
                ) //get server from SettingsFragment
            }

        // metadata here is user's network type device used geolocation if permitted etc
        metadataServer = Consts.METADATA_SERVER
        if (!setupServersAndCertificates(serverDisplay!!, metadataServer)) {
            return null
        }

        //get from preferences from SettingsFragment
        if (sharedPrefs != null) {
            confirmationReplays = sharedPrefs.getBoolean("pref_multiple_tests", true)
        }
        if (sharedPrefs != null) {
            useDefaultThresholds = sharedPrefs.getBoolean("pref_switch", true)
        }
        if (sharedPrefs != null) {
            a_threshold = Objects.requireNonNull(
                sharedPrefs.getString("pref_threshold_area", "10")
            )?.toInt()!!
        }
        if (sharedPrefs != null) {
            ks2pvalue_threshold = Objects.requireNonNull(
                sharedPrefs.getString("pref_threshold_ks2p", "5")
            )?.toInt()!!
        }

        // generate or retrieve an id for this phone
        val hasID = sharedPrefs?.getBoolean("hasID", false)
        if (!hasID!!) {
            randomID = RandomString(10).nextString()
            val editor = sharedPrefs?.edit()
            editor?.putBoolean("hasID", true)
            editor?.putString("ID", randomID)
            editor?.apply()
        } else {
            randomID = sharedPrefs.getString("ID", null)
        }

        // to get historyCount
        settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE)
        val localSettings = settings
        // generate or retrieve a historyCount for this phone
        val hasHistoryCount = localSettings?.getBoolean("hasHistoryCount", false)
        if (!hasHistoryCount!!) {
            historyCount = 0
            val editor = localSettings?.edit()
            editor?.putBoolean("hasHistoryCount", true)
            editor?.putInt("historyCount", historyCount)
            editor?.apply()
        } else {
            historyCount = localSettings?.getInt("historyCount", -1)!!
            if (historyCount == -1) { // check if retrieve historyCount succeeded
                throw RuntimeException()
            }
        }

        testId = -1
        doTest = false
        results = JSONArray() //init results

        //timing allows replays to be run with the same timing as when they were recorded
        //for example, if a YouTube video was paused for 2 seconds during recording, then the
        //replay will also pause for 2 seconds at that point in the replay
        //port tests try to run as fast as possible, so there is no timing for them
        Config.set("timing", if (runPortTests) "false" else "true")
        val serversStr = servers.toString()
        Config.set("server", serversStr.substring(1, serversStr.length - 1))
        val publicIP = serverRepository?.getPublicIP("80") //get user's IP address
        Config.set("publicIP", publicIP)
        Log.d("Replay", "public IP: $publicIP")

        //If cannot connect to server, display an error and stop tests
        if (publicIP == "-1") {
            updateProgress(
                "makeDialog", getString(R.string.simple_error),
                getString(R.string.error_no_connection), "true"
            )
            return null
        }

        if (!isActive) {
            return null
        }

        if (isNetworkUnavailable) {
            withContext(Dispatchers.Main) {
                displayNoNetworkDialogue()
            }
            return null
        }

        /*
         * Step 2: Run tests.
         */
        var firstApp = true
        for (app in selectedApps!!) {
            if (!isActive) {
                return null
            }

            if (!firstApp && mlabServerUsed) {
                if (!setupServersAndCertificates(serverDisplay!!, null)) {
                    return null
                }
            }

            this.app = app // Set the app to run test for
            this.app!!.arcepNeedsAlerting = false
            this.app!!.isAlertFCC = false

            if (!isActive) {
                return null
            }

            //make sure progress bar is clear
            clearProgressBar()
            updateProgress("updateUI") //make progress bar visible
            val rerun = runTest(false) // Run the test on this.app

            if (!isTomography && rerun) {
                //run confirmation test if confirmation tests are switched on in Settings and
                //first test was inconclusive or had differentiation
                //don't run confirmation tests for tomography tests
                //make sure progress bar is clear
                clearProgressBar()
                updateProgress("updateUI") //make progress bar visible
                runTest(true)
            }

            //clean up
            for (ws in wsConns) {
                ws?.close()
            }

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
                return null
            }
        }

        /*
         * Step 3: Save results.
         */
        if (results!!.length() > 0) {
            Log.i("Result Channel", "Storing results")
            saveResults()
        }
        if (!isActive) {
            return null
        }
        updateProgress(
            "makeDialog", getString(R.string.replay_finished_title),
            "", "false"
        )
        Log.i("Result Channel", "Exiting normally")

        return null
    }


    /**
     * Save results of the current tests to SharedPreference, so that it can be displayed in
     * the ResultsFragment.
     */
    private fun saveResults() {
        val dateFormat: DateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
        val strDate = dateFormat.format(Date())
        // get current results, if not exist, create a json
        // object with date as the key
        var resultsWithDate = try {
            JSONObject(settings!!.getString("lastResult", "{}"))
        } catch (e: JSONException) {
            JSONObject()
        }
        // remove one history result if there are too many
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

        val editor = settings!!.edit()
        editor.putString("lastResult", resultsWithDate.toString())
        editor.apply()
    }

    /**
     * Gets IPs of server and metadata server. Connects to MLab authentication WebSocket if
     * necessary. Gets necessary certificates for server and metadata server.
     *
     * @param server         the hostname of the server to connect to
     * @param metadataServer the hostname of the metadata server to connect to
     * @return true if everything properly sets up; false otherwise
     */
    private suspend fun setupServersAndCertificates(server: String, metadataServer: String?): Boolean {
        var server = server
        // We first resolve the IP of the server and then communicate with the server
        // Using IP only, because we have multiple server under same domain and we want
        // the client not to switch server during a test run
        //wehe4.meddle.mobi 90% returns 10.0.0.0 (use MLab), 10% legit IP (is Amazon)
        //version code 40 = version name 3.46
        if (BuildConfig.VERSION_CODE >= 40 && server == "wehe3.meddle.mobi") {
            server = "wehe4.meddle.mobi"
        }
        servers.clear()
        //extreme hack to temporarily get around French DNS look up issue
        if (server == "wehe4.meddle.mobi") {
            servers.add("10.0.0.0")
            Log.d("Serverhack", "hacking wehe4")
        } else {
            servers.add(serverRepository?.getServerIP(server))
            if (servers[servers.size - 1] == "") {
                updateProgress(
                    "makeDialog", getString(R.string.simple_error),
                    getString(R.string.error_unknown_host), "true"
                )
                return false
            }
        }
        // A hacky way to check server IP version
        var serverIPisV6 = false
        if (servers[0]!!.contains(":")) {
            serverIPisV6 = true
        }
        Log.d("ServerIPVersion", servers[0] + (if (serverIPisV6) "IPV6" else "IPV4"))
        //Connect to an MLab server if wehe4.meddle.mobi IP is 10.0.0.0 or if the client is
        //using ipv6. Steps to connect:
        //1) GET request to MLab site to get MLab servers that can be connected to
        //2) Parse first server to get MLab server URL and the authentication URL to connect to
        //3) Connect to authentication URL with WebSocket; have connection open for entire test
        //so SideChannel server doesn't disconnect (for security). URL valid for connection for
        //2 min after GET request made
        //4) Connect to SideChannel with MLab machine URL
        //5) Authentication URL has another 2 min timeout after connecting; every MLab test
        //needs to do this process.
        //Also connect to MLab server if running tomography tests
        var numTests = if (isTomography) Consts.NUM_TOMOGRAPHY_TESTS else 1
        mlabServerUsed = false
        if (servers[0] == "10.0.0.0" || serverIPisV6) {
            mlabServerUsed = true
            servers.removeAt(0)
            wsConns.clear()
            try {
                var numTries = 0 //tracks num tries before successful MLab connection
                var wsID: Int //WebSocket id
                val mLabResp =
                    serverRepository?.sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null)
                //TODO: make sure this outer try really necessary; check what happens if below line fails; will it exit gracefully?
                val mLabServers = mLabResp!!["results"] as JSONArray //get MLab servers list
                var i = 0
                while (wsConns.size < numTests && i < mLabServers.length()) {
                    //try the 4 servers before going to wehe2
                    try {
                        i++
                        wsID = wsConns.size
                        numTries++
                        val serverObj = mLabServers[i] as JSONObject //get first MLab server
                        server = "wehe-" + serverObj.getString("machine") //SideChannel URL
                        val mLabURL = (serverObj["urls"] as JSONObject)
                            .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY) //authentication URL

                        Log.d(
                            "WebSocket", ("Attempting to connect to server " + i
                                    + ": " + server)
                        )
                        wsConns.add(WebSocketConnection(wsID, URI(mLabURL))) //connect to WebSocket

                        //code below runs only if successful connection to WebSocket
                        Log.d(
                            "WebSocket", ("New WebSocket (id: " + wsID + ") connectivity check: "
                                    + (if (wsConns[wsID].isOpen) "CONNECTED" else "CLOSED") + " TO " + server)
                        )
                        servers.add(serverRepository?.getServerIP(server))
                        numMLab.add(numTries)
                        numTries = 0
                    } catch (e: URISyntaxException) {
                        //failed to connect to WebSocket, try next one
                        Log.w("WebSocket", "Failed to connect to WebSocket", e)
                    } catch (e: JSONException) {
                        Log.w("WebSocket", "Failed to connect to WebSocket", e)
                    } catch (e: DeploymentException) {
                        Log.w("WebSocket", "Failed to connect to WebSocket", e)
                    } catch (e: NullPointerException) {
                        Log.w("WebSocket", "Failed to connect to WebSocket", e)
                    } catch (e: InterruptedException) {
                        Log.w("WebSocket", "Failed to connect to WebSocket", e)
                    }
                    i++
                }
                if (wsConns.size != numTests) {
                    //if can't connect to mlab, try an amazon server using wehe2.meddle.mobi
                    Log.i("GetReplayServerIP", "Can't get MLab server, trying Amazon")
                    servers.clear()
                    for (ws in wsConns) { //close opened WebSockets
                        if (ws.isOpen) {
                            ws.close()
                        }
                    }
                    wsConns.clear()
                    if (isTomography) {
                        //user can't run tomography tests if can't connect to MLab servers
                        //exit tests in this case
                        updateProgress(
                            "makeDialog", getString(R.string.simple_error),
                            getString(R.string.tomography_not_supported), "true"
                        )
                        return false
                    }
                    numTests = 1
                    servers.add(serverRepository?.getServerIP("wehe2.meddle.mobi"))
                }
            } catch (e: JSONException) {
                Log.e("WebSocket", "Can't retrieve M-Lab servers", e)
            } catch (e: NullPointerException) {
                Log.e("WebSocket", "Can't retrieve M-Lab servers", e)
            }
        }

        for (i in 0 until numTests) {
            if (servers[i] == "") { //check to make sure IP was returned by getServerIP
                updateProgress(
                    "makeDialog", getString(R.string.simple_error),
                    getString(R.string.error_unknown_host), "true"
                )
                if (wsConns[i].isOpen) {
                    wsConns[i].close()
                }
                return false
            }
        }
        Log.d("GetReplayServerIP", "Server IP: $servers")
        serverRepository?.generateServerCertificate(true)

        //get URL(s) for analysis and results
        val port = Config.get("result_port").toInt() //get port to send tests through
        analyzerServerUrls.clear()
        for (srvr in servers) {
            analyzerServerUrls.add("https://$srvr:$port/Results")
            Log.d("Result Channel", "path: $srvr port: $port")
        }

        if (metadataServer != null) {
            this.metadataServer = serverRepository?.getServerIP(metadataServer)
            if (this.metadataServer == "") { //get IP and certificates for metadata server
                updateProgress(
                    "makeDialog", getString(R.string.simple_error),
                    getString(R.string.error_unknown_meta_host), "true"
                )
                return false
            }
            serverRepository?.generateServerCertificate(false)
        }
        serverRepository?.setServers(servers)
        return true
    }

    /**
     * Asks the server for analysis of a replay. For apps, server compares random replay to
     * original replay. For ports, server compares port 443 to a non-443 port. The original
     * replay and non-443 port have testId 0; the random replay and port 443 have testId 1.
     * The server compares the throughputs of testId 1 to testId 0 of the same history count.
     * The server then determines if there is differentiation and stores the result on the server.
     *
     * @param url          the url to the server where analysis will take place
     * @param id           the random ID assigned to specific user's device
     * @param historyCount the test to analyze
     * @return a JSONObject: { "success" : true | false }; true if server analyzes successfully
     */
    private fun ask4analysis(url: String, id: String?, historyCount: Int): JSONObject? {
        val pairs = HashMap<String, String?>()

        pairs["command"] = "analyze"
        pairs["userID"] = id
        pairs["historyCount"] = historyCount.toString()
        pairs["testID"] = "1"

        return serverRepository?.sendRequest(url, "POST", true, null, pairs)
    }

    /**
     * Retrieves a replay result from the server that it previously was requested to analyze.
     *
     * @param url          the url of the server to get the result
     * @param id           the random ID assigned to a specific user's device
     * @param historyCount the test containing the replay to retrieve
     * @return a JSONObject with a key named "success". If value of "success" is false, a key
     * named "error" is also contained in the result. If the value of "success" is true, a key
     * named "response" is the result. The value of "response" contains several keys:
     * "replayName", "date", "userID", "extraString", "historyCount", "testID", "area_test",
     * "ks2_ratio_test", "xput_avg_original", "xput_avg_test", "ks2dVal", "ks2pVal"
     */
    private fun getSingleResult(url: String, id: String?, historyCount: Int): JSONObject? {
        val data = ArrayList<String>()

        data.add("userID=$id")
        data.add("command=" + "singleResult")
        data.add("historyCount=$historyCount")
        data.add("testID=1")

        return serverRepository?.sendRequest(url, "GET", true, data, null)
    }

    /**
     * Reads the replay files and loads them into memory as a bean.
     *
     * @param filename filename of the replay
     * @param context  the application context
     * @return a bean containing information about the replay
     */
    private fun unpickleJSON(filename: String, context: Context): CombinedAppJSONInfoBean {
        val assetManager: AssetManager
        val inputStream: InputStream
        val appData = CombinedAppJSONInfoBean() //info about replay
        val Q = ArrayList<RequestSet>() //list of packets for replay
        try {
            assetManager = context.assets
            inputStream = assetManager.open(filename) //open replay file
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()

            //convert file contents to JSONArray object
            val jsonStr = String(buffer, StandardCharsets.UTF_8)
            val json = JSONArray(jsonStr)

            val qArray = json[0] as JSONArray //the packets in a replay file
            for (i in 0 until qArray.length()) {
                val tempRS = RequestSet()
                val dictionary = qArray.getJSONObject(i)
                tempRS.cSPair = dictionary["c_s_pair"] as String //client-server pair
                tempRS.payload = UtilsManager.hexStringToByteArray(
                    dictionary["payload"] as String
                )
                tempRS.timestamp = dictionary["timestamp"] as Double

                //for tcp
                if (dictionary.has("response_len")) { //expected length of response
                    tempRS.responseLen = dictionary["response_len"] as Int
                }
                if (dictionary.has("response_hash")) {
                    tempRS.responseHash = dictionary["response_hash"].toString()
                }
                //for udp
                if (dictionary.has("end")) tempRS.end = dictionary["end"] as Boolean

                Q.add(tempRS)
            }

            appData.q = Q

            //udp
            val portArray = json[1] as JSONArray //udp client ports
            val portStrArray = ArrayList<String>()
            for (i in 0 until portArray.length()) {
                portStrArray.add(portArray.getString(i))
            }
            appData.udpClientPorts = portStrArray

            //for tcp
            val csArray = json[2] as JSONArray //c_s_pairs
            val csStrArray = ArrayList<String>()
            for (i in 0 until csArray.length()) {
                csStrArray.add(csArray[i] as String)
            }
            appData.tcpCSPs = csStrArray
            appData.replayName = json[3] as String //name of replay
        } catch (e: JSONException) {
            Log.e("UnpickleJSON", "Error reading test files", e)
        } catch (e: IOException) {
            Log.e("UnpickleJSON", "Error reading test files", e)
        }
        return appData
    }

    /**
     * Sets the status of the app to be inconclusive if there is an error.
     *
     * @param msg error message to display to the user
     */
    private suspend fun setInconclusive(msg: String) {
        if (!inconclusiveApps.contains(app)) {
            inconclusiveApps.add(app!!)
        }
        app!!.error = msg
        updateProgress("updateStatus", app!!.name, getString(R.string.inconclusive))
    }

    /**
     * Run test. This method is called for every app/port the user selects. It is also called if
     * differentiation is detected for a test, and confirmation setting is enabled to run a
     * second test for the app/port to confirm if there is differentiation.
     *
     *
     * Each test has two replays. For apps, the replays consist of the original replay,
     * which contains actual traffic from that app, and a random replay, which replaces the
     * content of the original replay with random traffic. For ports, the "original" replay is
     * the port that is being tested. The "random" replay is port 443. The method uses "open" to
     * denote the "original" replay and "random" to denote the "random" replay.
     *
     *
     * There are three main steps in this method:
     * Step A: Flip a coin to decide which replay type to run first.
     * Step B: Run replays.
     * Step C: Determine if there is differentiation.
     *
     *
     * Step B has several sub-steps which run for each replay:
     * Step 0: Initialize variables.
     * Step 1: Tell server(s) about the replay that is about to happen.
     * Step 2: Ask server(s) for permission to run replay.
     * Step 3: Send noIperf.
     * Step 4: Send device info.
     * Step 5: Get port mapping from server(s).
     * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
     * Step 7: Start notifier(s) for UDP.
     * Step 8: Start receiver(s) to log throughputs on a given interval.
     * Step 8.5?: Start progress bar.
     * Step 9: Send packets to server(s).
     * Step 10: Tell server(s) that replay is finished.
     * Step 11: Send throughputs and slices to server(s).
     * Step 12: Close side channel(s) and TCP/UDP sockets.
     *
     * @param isConfirmation true if running confirmation test; false if running original test
     * @return true if test will be rerun; false otherwise
     */
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
            for (ws in wsConns) {
                if (ws != null) { //if using MLab, check that still connected
                    Log.d(
                        "WebSocket", ("Before running test WebSocket (id: "
                                + ws.id + ") connectivity check: "
                                + (if (ws.isOpen) "CONNECTED" else "CLOSED"))
                    )
                }
            }

            if (!isActive) { //user cancels running tests
                return false
            }
            if (isNetworkUnavailable) { //no network available
                displayNoNetworkDialogue()
                return false
            }

            /*
             * Step 0: Initialize variables.
             */
            // Based on the type selected load open or random trace of given application
            if (channel.equals("open", ignoreCase = true)) {
                this.appData = unpickleJSON(app!!.dataFile, activityContext)
            } else if (channel.equals("random", ignoreCase = true)) {
                this.appData = unpickleJSON(app!!.randomDataFile, activityContext)
            } else {
                Log.wtf("replayIndex", "replay name error: $channel")
            }

            try {
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + resources.getString(R.string.create_side_channel))
                )
                val sideChannelPort = Config.get("combined_sidechannel_port").toInt()

                Log.d("Servers", "$servers metadata $metadataServer")
                //The Side Channel communicates, in bytes mode, with the server to set up the
                //tests, start them, end them, and let the server know what exactly is going on.
                //The tests themselves are conducted over 2 other channels with the server -
                //the TCP channel for TCP tests and UDP channel for UDP tests. These channels
                //can be found in CTCPClientThread.java, CUDPClient.java, CombinedReceiverThread.java,
                //and CombinedNotifierThread.java
                //Server handles communication in handle() function in server_replay.py in server
                //code
                val sideChannels = ArrayList<CombinedSideChannel>()
                val jitterBeans = ArrayList<JitterBean>()
                //lots of for loops and ArrayLists in this method - tomography tests require
                //multiple tests to run at once; each test requires their own set of variables
                //so the variables for each test are stored in ArrayLists. Normal tests will only
                //need 1 test, so 1 element in the ArrayLists, but tomography tests will have more
                var id = 0
                for (server in servers) {
                    sideChannels.add(
                        CombinedSideChannel(
                            id, serverRepository?.sslSocketFactory!!,
                            server, sideChannelPort, appData!!.isTCP
                        )
                    )
                    jitterBeans.add(JitterBean())
                    id++
                }

                // increase history count only once during the run of a single test or set of
                // tomography tests
                if (iteration == 1) {
                    // First update historyCount
                    historyCount++
                    // Then write current historyCount to applicationBean
                    app!!.historyCount = historyCount
                    settings =
                        getSharedPreferences(STATUS, Context.MODE_PRIVATE)
                    val localSettings = settings
                    val editor = localSettings?.edit()
                    editor?.putInt("historyCount", historyCount)
                    editor?.apply()
                    Log.d("Replay", "historyCount: $historyCount")
                }

                // This random ID is used to map the test results to a specific instance of app
                // It is generated only once and saved thereafter
                if (randomID == null) {
                    Log.e("RecordReplay", "randomID does not exist!")
                    setInconclusive(getString(R.string.error_no_user_id))
                    return false
                }

                // initialize endOfTest value
                var endOfTest = false //true if last replay in this test is running
                if (channel.equals(types[types.size - 1], ignoreCase = true)) {
                    Log.i("Replay", "last replay running " + types[types.size - 1] + "!")
                    endOfTest = true
                }

                //Get user's IP address
                var replayPort = "80"
                var ipThroughProxy = "127.0.0.1"
                if (appData!!.isTCP) {
                    for (csp in appData!!.tcpCSPs) {
                        replayPort = csp.substring(csp.lastIndexOf('.') + 1)
                    }
                    ipThroughProxy = serverRepository?.getPublicIP(replayPort).toString()
                    if (ipThroughProxy == "-1") { //port is blocked; move on to next replay
                        //TODO: check if ui needed here
                        portBlocked = true
                        iteration++
                        continue
                    }
                }

                // testId is how server knows if the trace ran was open or random
                testId = if (channel.equals("open", ignoreCase = true)) 0 else 1

                if (doTest) {
                    Log.w("Replay", "include -Test string")
                }

                /*
                 * Step 1: Tell server(s) about the replay that is about to happen.
                 */
                var i = 0
                for (sc in sideChannels) {
                    // This is group of values that is used to track traces on server
                    // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
                    //set extra string to number tries needed to access MLab server
                    Config.set("extraString", if (numMLab.size == 0) "0" else numMLab[i].toString())
                    sc.declareID(
                        appData!!.replayName, if (endOfTest) "True" else "False",
                        randomID, historyCount.toString(), testId.toString(),
                        if (doTest) Config.get("extraString") + "-Test" else Config.get("extraString"),
                        ipThroughProxy, BuildConfig.VERSION_NAME
                    )

                    // This tuple tells the server if the server should operate on packets of traces
                    // and if so which packets to process
                    sc.sendChangeSpec(-1, "null", "null")
                    i++
                }

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable) {
                    displayNoNetworkDialogue()
                    return false
                }

                /*
                 * Step 2: Ask server(s) for permission to run replay.
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.ask4permission))
                )
                // Now to move forward we ask for server permission
                val numOfTimeSlices = ArrayList<Int>()
                for (sc in sideChannels) {
                    val permission = sc.ask4Permission()
                    val status = permission[0].trim { it <= ' ' }

                    Log.d(
                        "Replay", ("Channel " + sc.id + ": permission[0]: "
                                + status + " permission[1]: " + permission[1])
                    )

                    val permissionError = permission[1].trim { it <= ' ' }
                    var customError: String
                    if (status == "0") {
                        // These are the different errors that server can report
                        customError = when (permissionError) {
                            "1" -> getString(R.string.error_unknown_replay)
                            "2" -> getString(R.string.error_IP_connected)
                            "3" -> getString(R.string.error_low_resources)
                            else -> getString(R.string.error_unknown)
                        }
                        setInconclusive(customError)
                        return false
                    }
                    numOfTimeSlices.add(permission[2].trim { it <= ' ' }.toInt(10))
                }

                /*
                 * Step 3: Send noIperf.
                 */
                for (sc in sideChannels) {
                    sc.sendIperf() // always send noIperf here
                }

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable) {
                    displayNoNetworkDialogue()
                    return false
                }

                /*
                 * Step 4: Send device info.
                 */
                for (sc in sideChannels) {
                    sc.sendMobileStats(Config.get("sendMobileStats"), applicationContext)
                }

                /*
                 * Step 5: Get port mapping from server.
                 */
                /*
                 * Ask for port mapping from server. For some reason, port map
                 * info parsing was throwing error. so, I put while loop to do
                 * this until port mapping is parsed successfully.
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.receive_server_port_mapping))
                )

                val serverPortsMaps =
                    ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>>()
                val udpReplayInfoBeans = ArrayList<UDPReplayInfoBean>()
                for (sc in sideChannels) {
                    serverPortsMaps.add(sc.receivePortMappingNonBlock())
                    val udpReplayInfoBean = UDPReplayInfoBean()
                    udpReplayInfoBean.senderCount = sc.receiveSenderCount()
                    udpReplayInfoBeans.add(udpReplayInfoBean)
                    Log.i(
                        "Replay", ("Channel " + sc.id + ": Successfully"
                                + " received serverPortsMap and senderCount!")
                    )
                }

                /*
                 * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.create_tcp_client))
                )

                //map of all cs pairs to TCP clients for a replay
                val CSPairMappings = ArrayList<HashMap<String, CTCPClient>>()

                //create TCP clients
                for (sc in sideChannels) {
                    val CSPairMapping = HashMap<String, CTCPClient>()
                    for (csp in appData!!.tcpCSPs) {
                        //get server IP and port
                        val destIP = csp.substring(
                            csp.lastIndexOf('-') + 1,
                            csp.lastIndexOf(".")
                        )
                        var destPort = csp.substring(csp.lastIndexOf('.') + 1)
                        //pad port to 5 digits with 0s; ex. 00443 or 00080
                        destPort = String.format("%5s", destPort).replace(' ', '0')

                        //get the server
                        val instance: ServerInstance
                        try {
                            instance = serverPortsMaps[sc.id]["tcp"]
                                ?.get(destIP)
                                ?.get(destPort)!!
                        } catch (e: NullPointerException) {
                            Log.e("Replay", "Channel " + sc.id + ": Cannot get instance", e)
                            setInconclusive(getString(R.string.error_no_connection))
                            return false
                        } catch (e: AssertionError) {
                            Log.e("Replay", "Channel " + sc.id + ": Cannot get instance", e)
                            setInconclusive(getString(R.string.error_no_connection))
                            return false
                        }
                        if (instance.server.trim { it <= ' ' } == "")  // TODO: Use a setter instead probably
                            instance.server =
                                servers[sc.id].toString() // serverPortsMap.get(destPort);


                        //create the client
                        val c = CTCPClient(
                            csp, instance.server,
                            instance.port.toInt(),
                            appData!!.replayName, Config.get("publicIP"), false
                        )
                        CSPairMapping[csp] = c
                    }
                    CSPairMappings.add(CSPairMapping)
                    Log.i(
                        "Replay", ("Channel " + sc.id
                                + ": created clients from CSPairs")
                    )
                    Log.d("Replay", "Size of CSPairMapping is " + CSPairMapping.size)
                }

                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.create_udp_client))
                )

                //map of all client ports to UDP clients for a replay
                val udpPortMappings = ArrayList<HashMap<String, CUDPClient>>()

                //create client for each UDP port
                for (sc in sideChannels) {
                    val udpPortMapping = HashMap<String, CUDPClient>()
                    for (originalClientPort in appData!!.udpClientPorts) {
                        val c = CUDPClient(Config.get("publicIP"))
                        udpPortMapping[originalClientPort] = c
                    }
                    udpPortMappings.add(udpPortMapping)
                    Log.i(
                        "Replay", ("Channel " + sc.id
                                + ": created clients from udpClientPorts")
                    )
                    Log.d("Replay", "Size of udpPortMapping is " + udpPortMapping.size)
                }

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable) {
                    displayNoNetworkDialogue()
                    return false
                }

                /*
                 * Step 7: Start notifier(s) for UDP.
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.run_notf))
                )

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
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.run_receiver))
                )

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
                 * Step 8.5?: Start progress bar.
                 */
                // This thread runs in parallel keeps progressbar up to date
                val finalIteration = iteration

                //TODO: switch to ScheduledThreadExecutor?
                val uiUpdateJob = CoroutineScope(Dispatchers.Default).launch {
                    // Name the coroutine for debugging (optional)
                    currentCoroutineContext()[CoroutineName]?.let {
                        Thread.currentThread().name = "UIUpdateCoroutine"
                    }

                    if (finalIteration == 1) {
                        clearProgressBar()
                    }

                    while (updateUIBean!!.progress < 100 && isActive) {
                        updateProgress("updateUI")
                        // Delay in coroutine instead of Thread.sleep
                        delay(500) // 500ms delay
                    }
                }
                uiUpdateJobs.add(uiUpdateJob)

                /*
                 * Step 9: Send packets to server(s).
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.run_sender))
                )

                val udpServerMappings =
                    ArrayList<HashMap<String, HashMap<String, ServerInstance>>>()
                for (m in serverPortsMaps) {
                    udpServerMappings.add(m["udp"]!!)
                }

                val queue = CombinedQueue(
                    appData!!.q, jitterBeans, analyzerTasks,
                    if (runPortTests) Consts.REPLAY_PORT_TIMEOUT else Consts.REPLAY_APP_TIMEOUT
                )
                val timeStarted = System.nanoTime() //start time for sending
                //send packets
                updateUIBean?.let {
                    queue.run(
                        it, types.size, CSPairMappings,
                        udpPortMappings, udpReplayInfoBeans, udpServerMappings,
                        Config.get("timing").toBoolean(), servers, coroutineContext
                    )
                }

                //all packets sent - stop logging and receiving
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

                if (iteration == 1) { //make progress bar to 50%
                    updateProgress("finishProgress", "1")
                } else { //make progress bar to 100%
                    updateProgress("finishProgress", "2")
                    Log.i("UpdateUI", "completed!")
                }

                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable) {
                    displayNoNetworkDialogue()
                    return false
                }

                /*
                 * Step 10: Tell server(s) that replay is finished.
                 */
                updateProgress(
                    "updateStatus", app!!.name, (iteration.toString() + "/"
                            + types.size + " " + getString(R.string.send_done))
                )

                //time to send all packets
                val duration = ((System.nanoTime() - timeStarted).toDouble()) / 1000000000
                for (sc in sideChannels) {
                    sc.sendDone(duration)
                }
                Log.d("Replay", "replay(s) finished using time $duration s")

                /*
                 * Step 11: Send throughputs and slices to server(s).
                 */
                for (sc in sideChannels) {
                    sc.sendTimeSlices(analyzerTasks[sc.id].averageThroughputsAndSlices)
                }

                //set avg of port 443, so it can be displayed if port being tested is blocked
                if (runPortTests && channel.equals("random", ignoreCase = true)) {
                    app!!.randomThroughput =
                        analyzerTasks[0].avgThroughput //TODO: Multithread display
                }

                // TODO find a better way to do this
                // Send Result;No and wait for OK before moving forward
                for (sc in sideChannels) {
                    while (sc.getResult(Config.get("result"))) {
                        Thread.sleep(500)
                    }
                }

                /*
                 * Step 12: Close side channel(s) and TCP/UDP sockets.
                 */
                // closing side channel sockets
                for (sc in sideChannels) {
                    sc.closeSideChannelSocket()
                }

                //close TCP sockets
                for (mapping in CSPairMappings) {
                    for (csp in appData!!.tcpCSPs) {
                        val c = mapping[csp]
                        c?.close()
                    }
                }
                Log.i("CleanUp", "Closed CSPairs 1")

                //close UDP sockets
                for (mapping in udpPortMappings) {
                    for (originalClientPort in appData!!.udpClientPorts) {
                        val c = mapping[originalClientPort]
                        c?.close()
                    }
                }

                Log.i("CleanUp", "Closed CSPairs 2")
                iteration++
            } catch (e: InterruptedException) {
                Log.w("Replay", "Replay interrupted!", e)
            } catch (e: IOException) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
                Log.e("Replay", "Some IO issue with server", e)
                setInconclusive(getString(R.string.error_no_connection))
                return false
            }
        }

        /*
         * Step C: Determine if there is differentiation.
         */
        return getResults(portBlocked, isConfirmation)
    }

    /**
     * Determines if there is differentiation. If app is running, result of random test is
     * compared against original test. If port is running, result of port test is compared
     * against port 443.
     *
     *
     * If results are inconclusive or have differentiation, a confirmation test will run if the
     * confirmation setting is switched on.
     *
     *
     * For port tests, Step 1 and Step 2 are skipped if a port is blocked, as no throughputs are
     * sent to the server to analyze. A response is created for Step 3 instead of retrieving
     * from the server. When a port is blocked, the port throughput is 0 Mbps, while port 443
     * (which should not be blocked) has a throughput, which is calculated in Step 11 of
     * runTest().
     *
     *
     * Step 1: Ask sever to analyze a test.
     * Step 2: Get result of analysis from server.
     * Step 3: Parse the analysis results.
     * Step 4: Determine if there is differentiation.
     * Step 5: Save and display results to user. Rerun test if necessary.
     *
     * @param portBlocked    true if a port in the port tests is blocked; false otherwise
     * @param isConfirmation true if confirmation test; false if original test
     * @return true if confirmation test needs to be run; false otherwise
     */
    private suspend fun getResults(portBlocked: Boolean, isConfirmation: Boolean): Boolean {
        var portBlocked = portBlocked
        try {
            if (isCancelled) {
                return false
            }
            if (isNetworkUnavailable) {
                displayNoNetworkDialogue()
                return false
            }

            var id = 0
            for (w in wsConns) { //check websockets still connected if using MLab
                Log.d(
                    "WebSocket", ("WebSocket (id: " + id + ") connectivity check: "
                            + (if (w.isOpen) "CONNECTED" else "CLOSED"))
                )
                id++
            }

            val analysisResults = ArrayList<JSONObject>()
            if (!portBlocked) { //skip Step 1 and step 2 if port blocked
                /*
                 * Step 1: Ask server to analyze a test.
                 */
                var resp: JSONObject?
                for (server in analyzerServerUrls) {
                    for (ask4analysisRetry in 3 downTo 1) {
                        resp = ask4analysis(server, randomID, app!!.historyCount) //request analysis
                        if (resp == null) {
                            Log.e(
                                "Result Channel",
                                "$server: ask4analysis returned null!"
                            )
                        } else {
                            analysisResults.add(resp)
                            break
                        }
                    }
                }

                if (analysisResults.size != analyzerServerUrls.size) {
                    setInconclusive(getString(R.string.error_analysis_fail))
                    return false
                }

                var success: Boolean
                for (result in analysisResults) {
                    success = result.getBoolean("success")
                    if (!success) {
                        Log.e("Result Channel", "ask4analysis failed!")
                        setInconclusive(getString(R.string.error_analysis_fail))
                        return false
                    }
                }

                updateProgress("updateStatus", app!!.name, getString(R.string.waiting))

                // sanity check
                if (app!!.historyCount < 0) {
                    Log.e("Result Channel", "historyCount value not correct!")
                    return false
                }

                Log.i("Result Channel", "ask4analysis succeeded!")
                if (isCancelled) {
                    return false
                }
                if (isNetworkUnavailable) {
                    displayNoNetworkDialogue()
                    return false
                }

                /*
                 * Step 2: Get results of analysis from server.
                 */
                analysisResults.clear()
                for (url in analyzerServerUrls) {
                    var i = 0
                    while (true) {
                        //3 attempts to get analysis from sever
                        resp = getSingleResult(url, randomID, app!!.historyCount) //get results

                        if (resp == null) {
                            Log.e(
                                "Result Channel",
                                "$url: getSingleResult returned null!"
                            )
                        } else {
                            success = resp.getBoolean("success")
                            if (success) { //success
                                if (resp.has("response")) { //success and has response
                                    analysisResults.add(resp)
                                    Log.i(
                                        "Result Channel",
                                        "$url: retrieve result succeeded"
                                    )
                                    break
                                } else { //success but response is missing
                                    Log.w(
                                        "Result Channel",
                                        "$url: Server result not ready"
                                    )
                                }
                            } else if (resp.has("error")) {
                                Log.e(
                                    "Result Channel",
                                    "ERROR: " + url + ": " + resp.getString("error")
                                )
                            } else {
                                Log.e(
                                    "Result Channel",
                                    "Error: $url: Some error getting results."
                                )
                            }
                        }

                        if (i < 3) { //wait 2 seconds to try again
                            try {
                                Thread.sleep(2000)
                            } catch (e: InterruptedException) {
                                Log.w("Result Channel", "Sleep interrupted", e)
                            }
                        } else { //error after 3rd attempt
                            if (runPortTests) { //"the port 80 issue"
                                portBlocked = true
                                Log.i("Result Channel", "Can't retrieve result, port blocked")
                                break
                            } else {
                                setInconclusive(getString(R.string.not_all_tcp_sent_text))
                                return false
                            }
                        }
                        i++
                    }
                }
            }

            /*
             * Step 3: Parse the analysis results.
             */
            println("RESULLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLTS")
            for (j in analysisResults) {
                println(j)
            }

            var differentiationNetwork: String? = ""
            if (isTomography) {
                //TODO: tomography api call here
                val random = Random()
                if (random.nextInt(2) == 1) {
                    differentiationNetwork = carrier
                }
            }
            //TODO: put multithread display here
            val response =
                if (portBlocked) JSONObject() else analysisResults[0].getJSONObject("response")


            if (portBlocked) { //generate results if port blocked
                response.put("userID", randomID)
                response.put("historyCount", historyCount)
                response.put("replayName", app!!.dataFile)
                response.put("area_test", -1)
                response.put("ks2pVal", -1)
                response.put("ks2_ratio_test", -1)
                response.put("xput_avg_original", 0)
                response.put(
                    "xput_avg_test",
                    app!!.randomThroughput
                ) //calculated in runTest() Step 11
            }

            Log.d("Result Channel", "SERVER RESPONSE: $response")

            val userID = response.getString("userID")
            val historyCount = response.getInt("historyCount")
            val area_test = response.getDouble("area_test")
            val ks2pVal = response.getDouble("ks2pVal")
            val ks2RatioTest = response.getDouble("ks2_ratio_test")
            val xputOriginal = response.getDouble("xput_avg_original")
            val xputTest = response.getDouble("xput_avg_test")

            // sanity check
            if ((!userID.trim { it <= ' ' }.equals(randomID, ignoreCase = true))
                || (historyCount != app!!.historyCount)
            ) {
                Log.e(
                    "Result Channel", ("Result didn't pass sanity check! "
                            + "correct id: " + randomID
                            + " correct historyCount: " + app!!.historyCount)
                )
                Log.e("Result Channel", "Result content: $response")
                setInconclusive(getString(R.string.error_result))
                return false
            }

            /*
             * Step 4: Determine if there is differentiation.
             */
            //area test threshold default is 50%; ks2 p value test threshold default is 1%
            //if default switch is on and one of the throughputs is over 10 Mbps, change the
            //area threshold to 30%, which increases chance of Wehe finding differentiation.
            //If the throughputs are over 10 Mbps, the difference between the two throughputs
            //would need to be much larger than smaller throughputs for differentiation to be
            //triggered, which may confuse users
            //TODO: might have to relook at thresholds and do some formal research on optimal
            // thresholds. Currently thresholds chosen ad-hoc
            if (useDefaultThresholds && (xputOriginal > 10 || xputTest > 10)) {
                a_threshold = 30
            }

            val area_test_threshold = a_threshold.toDouble() / 100
            val ks2pVal_threshold = ks2pvalue_threshold.toDouble() / 100

            //double ks2RatioTest_threshold = (double) 95 / 100;
            val aboveArea = abs(area_test) >= area_test_threshold
            //boolean trustPValue = ks2RatioTest >= ks2RatioTest_threshold;
            val belowP = ks2pVal < ks2pVal_threshold
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

            // TODO uncomment following code when you want differentiation to occur
//                differentiation = true;
//                inconclusive = true;

            /*
             * Step 5: Save and display results to user. Rerun test if necessary.
             */
            //determine if the test needs to be rerun
            if ((inconclusive || differentiation) && confirmationReplays && !isConfirmation && !isTomography) {
                updateProgress(
                    "updateStatus",
                    app!!.name,
                    getString(R.string.confirmation_replay)
                )
                try { //wait 2 seconds so user can read message before it disappears
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    Log.w("Result Channel", "sleep interrupted", e)
                }
                //runTest();
                return true //return so that first result isn't saved
            }

            val displayStatus: String //display for the user in their language
            val saveStatus: String //save to disk, so it can appear in the correct language in prev results
            if (isTomography) {
                saveStatus = if (differentiationNetwork == "") "tomo failed" else "tomo succ"
                displayStatus = if (differentiationNetwork == "")
                    getString(R.string.tomo_failed)
                else
                    getString(R.string.tomo_succ)
            } else if (inconclusive) {
                saveStatus = "inconclusive"
                displayStatus = getString(R.string.inconclusive)
                inconclusiveApps.add(app!!)
            } else if (differentiation) {
                saveStatus = "has diff"
                displayStatus = getString(R.string.has_diff)

                var error =
                    if (runPortTests) getString(R.string.test_blocked_port_text) else getString(
                        R.string.test_blocked_app_text
                    )
                if (!portBlocked) {
                    error = if (xputOriginal > xputTest) {
                        if (runPortTests) getString(R.string.test_prioritized_port_text) else getString(
                            R.string.test_prioritized_app_text
                        )
                    } else {
                        if (runPortTests) getString(R.string.test_throttled_port_text) else getString(
                            R.string.test_throttled_app_text
                        )
                    }
                }
                app!!.error = error

                val current = resources.configuration.locale
                val country = current.country
                if (country == "FR") { //show alert arcep button
                    app!!.arcepNeedsAlerting = true
                } else if (country == "US") {
                    app!!.isAlertFCC = true
                }
                diffApps.add(app!!)
            } else {
                saveStatus = "no diff"
                displayStatus = getString(R.string.no_diff)
            }

            //for results display on the Run Test page
            app!!.status = displayStatus
            app!!.area_test = area_test
            app!!.ks2pVal = ks2pVal
            app!!.ks2pRatio = ks2RatioTest
            app!!.originalThroughput = xputOriginal
            app!!.randomThroughput = xputTest
            app!!.differentiationNetwork = differentiationNetwork

            Log.i("Result Channel", "writing results to json array")
            //for results display on the Previous Results page
            response.put("isPort", runPortTests)
            response.put("appName", app!!.name)
            response.put("appImage", app!!.image)
            response.put("status", saveStatus)
            response.put("date", Date().time)
            response.put("areaThreshold", area_test_threshold)
            response.put("ks2pThreshold", ks2pVal_threshold)
            response.put("isIPv6", isIPv6)
            response.put("server", serverDisplay)
            response.put("carrier", carrier)
            if (isTomography) {
                response.put("tomographyNetwork", differentiationNetwork)
            }
            Log.d("response", response.toString())
            results!!.put(response) //put response in array to save

            updateProgress("updateStatus", app!!.name, app!!.status) //display results to user
        } catch (e: JSONException) {
            Log.e("Result Channel", "parsing json error", e)
        }
        return false
        //todos: put description of tomography at top of reruns
        //fix ui pop up
        //does rerun button pop up after tomo tests?
        //do no diff tests appear during tomo tests?
    }


    val activityContext: Context
        get() = this

    private fun displayRerunButtons() {
        displayRerunTomoButtons()
    }

    companion object {
        const val STATUS: String = "ReplayActPrefsFile"
    }
}
