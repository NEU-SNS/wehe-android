package mobi.meddle.wehe.ui.replay

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import mobi.meddle.wehe.R
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter
import mobi.meddle.wehe.data.bean.ApplicationBean
import java.util.Objects

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
    private var traceRunner: TraceRunAsync? = null //runs the tests
    var runPortTests: Boolean = false
        private set
    private val isIPv6 = false //true if user's public IP is v6, use to display in results
    var carrier: String? = null //carrier to display in results
        private set
    private val serverDisplay: String? = null //server to display in the results
    private val mlabServerUsed = false

    //Tomography tests determine where exactly in the network differentiation occurs. If differentiation
    //is detected in a test, the app will ask users if they want to run a tomography test. These
    //tests run 3 concurrent tests to 3 optimal MLab servers. Based on the times the packets are sent,
    //an algorithm can determine where differentiation occurs. All 3 of the tests count as one Wehe
    //"Test", so 1 historyCount is used for all 3 tests.
    var isTomography: Boolean = false //true if tomography test, false if normal test
        private set
    private val doNothing =
        DialogInterface.OnClickListener { dialog, which -> }

    //this happens if rerun differentiation or rerun inconclusive buttons clicked
    private val rerunButtons =
        DialogInterface.OnClickListener { dialog, which -> //change page title
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
            traceRunner = TraceRunAsync(this@ReplayActivity)
            traceRunner!!.execute()
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
                traceRunner = TraceRunAsync(this@ReplayActivity)
                traceRunner!!.execute()
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
        adapter = ImageReplayRecyclerViewAdapter(selectedApps, this, runPortTests)
        val appsRecyclerView = findViewById<RecyclerView>(R.id.appsRecyclerView)
        val appsRecyclerViewLayoutManager: RecyclerView.LayoutManager = LinearLayoutManager(this)
        appsRecyclerView.layoutManager = appsRecyclerViewLayoutManager
        appsRecyclerView.adapter = adapter
        progressBar = findViewById(R.id.prgBar)
        context = applicationContext
        // This is the core of the Application
        if (!isNetworkUnavailable) {
            traceRunner = TraceRunAsync(this)
            traceRunner!!.execute()
        }
    }

    override fun onDestroy() {
        //does this before going back to SelectionFragment
        super.onDestroy()
        if (isReplayOngoing) {
            if (traceRunner != null) {
                traceRunner!!.cancel()
            }
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

    val isNetworkUnavailable: Boolean
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
    fun displayNoNetworkDialogue() {
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
    fun replayStop() {
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

    // Add app to different lists based on result
    fun addToDiffApps(app: ApplicationBean) {
        diffApps.add(app)
    }

    fun addToInconclusiveApps(app: ApplicationBean) {
        inconclusiveApps.add(app)
    }

    val activityContext: Context
        get() = this

    fun displayRerunButtons() {
        displayRerunTomoButtons()
    }

    companion object {
        const val STATUS: String = "ReplayActPrefsFile"
    }
}
