package mobi.meddle.wehe.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.websocket.DeploymentException;

import mobi.meddle.wehe.BuildConfig;
import mobi.meddle.wehe.R;
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.CombinedAppJSONInfoBean;
import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.bean.UpdateUIBean;
import mobi.meddle.wehe.combined.CTCPClient;
import mobi.meddle.wehe.combined.CUDPClient;
import mobi.meddle.wehe.combined.CombinedAnalyzerTask;
import mobi.meddle.wehe.combined.CombinedNotifierThread;
import mobi.meddle.wehe.combined.CombinedQueue;
import mobi.meddle.wehe.combined.CombinedReceiverThread;
import mobi.meddle.wehe.combined.CombinedSideChannel;
import mobi.meddle.wehe.combined.WebSocketConnection;
import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.RandomString;
import mobi.meddle.wehe.util.UtilsManager;

/**
 * Runs the replays.
 * XML layout: activity_replay.xml
 * adapter.ImageReplayRecyclerViewAdapter.java for layout of each replay
 */
public class ReplayActivity extends AppCompatActivity {
    public static final String STATUS = "ReplayActPrefsFile";
    private boolean replayOngoing = false;
    private ArrayList<ApplicationBean> selectedApps = null; //apps to run
    private final ArrayList<ApplicationBean> diffApps = new ArrayList<>(); //apps with differentiation
    private final ArrayList<ApplicationBean> inconclusiveApps = new ArrayList<>();
    private ProgressBar prgBar; //progress bar at bottom of screen when tests are running
    private ImageReplayRecyclerViewAdapter adapter = null; //layout for each replay
    private Context context;
    private TraceRunAsync traceRunner; //runs the tests
    private boolean runPortTests;
    private boolean isIPv6; //true if user's public IP is v6, use to display in results
    private String carrier; //carrier to display in results
    private String serverDisplay; //server to display in the results
    private boolean mlabServerUsed;
    //Tomography tests determine where exactly in the network differentiation occurs. If differentiation
    //is detected in a test, the app will ask users if they want to run a tomography test. These
    //tests run 3 concurrent tests to 3 optimal MLab servers. Based on the times the packets are sent,
    //an algorithm can determine where differentiation occurs. All 3 of the tests count as one Wehe
    //"Test", so 1 historyCount is used for all 3 tests.
    private boolean isTomography = false; //true if tomography test, false if normal test

    private final DialogInterface.OnClickListener doNothing = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {

        }
    };

    //this happens if rerun differentiation or rerun inconclusive buttons clicked
    private final DialogInterface.OnClickListener rerunButtons = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            //change page title
            Objects.requireNonNull(getSupportActionBar()).setTitle(getString(R.string.replay_page_title));

            if (which == DialogInterface.BUTTON_POSITIVE) {
                selectedApps = new ArrayList<>(diffApps);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                selectedApps = new ArrayList<>(inconclusiveApps);
            }

            //rearrange layout to hide rerun button
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                    findViewById(R.id.appsRecyclerView).getLayoutParams();
            params.addRule(RelativeLayout.ABOVE, R.id.prgBarLayout);
            findViewById(R.id.rerunButton).setVisibility(View.GONE);
            findViewById(R.id.localizeDiffButton).setVisibility(View.GONE);
            adapter.setTomography(false);
            isTomography = false;
            for (ApplicationBean app : selectedApps) {
                app.setTomography(false);
                app.setArcepNeedsAlerting(false);
                app.setStatus(getString(R.string.pending));
            }
            inconclusiveApps.clear();
            diffApps.clear();
            traceRunner = new TraceRunAsync();
            traceRunner.execute("");
        }
    };

    //rerun dialogue
    private final View.OnClickListener rerunListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //dialogue box to rerun tests
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(ReplayActivity.this,
                    AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle(R.string.rerun_test_title)
                    .setMessage(R.string.rerun_test_descr);
            //rerun tests with differentiation
            if (diffApps.size() != 0) {
                alertDialog.setPositiveButton(R.string.rerun_diff_opt, rerunButtons);
            }
            //rerun only the inconclusive tests; doesn't appear if no tests inconclusive
            if (inconclusiveApps.size() != 0) {
                alertDialog.setNegativeButton(R.string.rerun_incon_opt, rerunButtons);
            }
            alertDialog.setNeutralButton(android.R.string.cancel, doNothing); //cancel button
            AlertDialog dialog = alertDialog.create();
            dialog.show();
            if (diffApps.size() != 0) {
                centerAlignButton(dialog, AlertDialog.BUTTON_POSITIVE);
            }
            if (inconclusiveApps.size() != 0) {
                centerAlignButton(dialog, AlertDialog.BUTTON_NEGATIVE);
            }
            centerAlignButton(dialog, AlertDialog.BUTTON_NEUTRAL);
        }
    };

    //run tomography dialogue
    private final View.OnClickListener runTomoListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //automatically pops up when tests end if some tests have differentiation
            //asks if user wants to run tomography test
            new AlertDialog.Builder(ReplayActivity.this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle(R.string.localize_diff)
                    .setMessage(R.string.rerun_tomography_descr)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Objects.requireNonNull(getSupportActionBar()).setTitle(getString(R.string.tomography_page_title));

                            //rearrange layout to hide rerun button
                            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                                    findViewById(R.id.appsRecyclerView).getLayoutParams();
                            params.addRule(RelativeLayout.ABOVE, R.id.prgBarLayout);
                            findViewById(R.id.rerunButton).setVisibility(View.GONE);
                            findViewById(R.id.localizeDiffButton).setVisibility(View.GONE);

                            //run tomography tests if user clicks yes
                            isTomography = true;
                            adapter.setTomography(true);
                            selectedApps = new ArrayList<>(diffApps);
                            for (ApplicationBean app : selectedApps) {
                                app.setTomography(true);
                                app.setArcepNeedsAlerting(false);
                                app.setStatus(getString(R.string.pending));
                            }
                            traceRunner = new TraceRunAsync();
                            traceRunner.execute("");
                        }
                    })
                    .setNegativeButton(R.string.no, doNothing).create().show();
        }
    };

    /**
     * Force alert dialog to center align buttons.
     *
     * @param dialog the dialog to align
     * @param button the button to align
     */
    private void centerAlignButton(@NonNull AlertDialog dialog, int button) {
        Button b = dialog.getButton(button);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) b.getLayoutParams();
        params.gravity = Gravity.CENTER;
        b.setLayoutParams(params);
    }

    /**
     * When tests are finished and there are tests with differentiation or inconclusive tests, show
     * the rerun button, which allow users to rerun these tests. Also, if there is differentiation,
     * show the Localize Differentiation button to allow users to run tomography tests.
     */
    private void displayRerunTomoButtons() {
        //set rerun button to be visible if differentiation or inconclusive apps
        Button rerunButton = findViewById(R.id.rerunButton);
        rerunButton.setVisibility(View.VISIBLE);
        rerunButton.setOnClickListener(rerunListener);

        //TODO: uncomment to allow users to run tomography tests
        /*
        if (!isTomography && diffApps.size() > 0) { //show tomography button if necessary
            Button runTomoButton = findViewById(R.id.localizeDiffButton);
            runTomoButton.setVisibility(View.VISIBLE);
            runTomoButton.setOnClickListener(runTomoListener);
        }*/

        //rearrange layout so progress bar disappears
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                findViewById(R.id.appsRecyclerView).getLayoutParams();
        params.addRule(RelativeLayout.ABOVE, R.id.actionBtnsLayout);
        findViewById(R.id.prgBar).setVisibility(View.GONE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //entry point coming from SelectionFragment
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay);
        Toolbar mToolbar = findViewById(R.id.replay_bar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.replay_page_title));
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // First check to see of Internet access is available
        // TODO integrate this with system events like WIFI_STATUS changes same for LTE,
        //  based on events received show appropriate messages to the user
        if (isNetworkUnavailable()) {
            displayNoNetworkDialogue();
        }

        //get stuff from SelectionFragment
        Bundle bundle = getIntent().getExtras();
        assert bundle != null;
        runPortTests = bundle.getBoolean("runPortTests");
        carrier = bundle.getString("carrier");
        selectedApps = getIntent().getParcelableArrayListExtra("selectedApps");
        assert selectedApps != null;
        for (ApplicationBean app : selectedApps) {
            app.setStatus(getString(R.string.pending));
        }

        //set the view for each selected app
        adapter = new ImageReplayRecyclerViewAdapter(selectedApps, this, runPortTests);
        RecyclerView appsRecyclerView = findViewById(R.id.appsRecyclerView);
        RecyclerView.LayoutManager appsRecyclerViewLayoutManager = new LinearLayoutManager(this);
        appsRecyclerView.setLayoutManager(appsRecyclerViewLayoutManager);
        appsRecyclerView.setAdapter(adapter);

        prgBar = findViewById(R.id.prgBar);
        context = getApplicationContext();
        // This is the core of the Application
        if (!isNetworkUnavailable()) {
            traceRunner = new TraceRunAsync();
            traceRunner.execute("");
        }
    }

    @Override
    protected void onDestroy() {
        //does this before going back to SelectionFragment
        super.onDestroy();
        if (replayOngoing) {
            if (traceRunner != null) {
                traceRunner.cancel(true);
            }
            Toast.makeText(ReplayActivity.this, getText(R.string.replay_aborted),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    /**
     * This Method checks the network Availability. For this NetworkInfo class is used and this
     * should also provide type of connectivity i.e. Wi-Fi, Cellular ..
     *
     * @return true if network is available, false otherwise
     */
    private boolean isNetworkUnavailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        //NetworkInfo deprecated in AndroidX - can use a different library, but that would mean
        //having to increase the minimum Android version this app can support
        //TODO: Switch to non-deprecated library without increasing minSDK?
        NetworkInfo activeNetworkInfo =
                connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo == null || !activeNetworkInfo.isConnected();
    }

    /**
     * Display this popup message if there is no network
     */
    private void displayNoNetworkDialogue() {
        ReplayActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ReplayActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(getString(R.string.network_error))
                        .setMessage(getString(R.string.text_network_error))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                replayStop();
                            }
                        }).show();
            }
        });
    }

    /**
     * User wants to leave the replay activity.
     */
    private void replayStop() {
        if (!replayOngoing) {
            ReplayActivity.this.finish(); //calls onDestroy
            ReplayActivity.this.overridePendingTransition(
                    android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        } else {
            new AlertDialog.Builder(ReplayActivity.this, //pop up box
                    AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                    .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                    .setPositiveButton(getString(android.R.string.yes), //yes button
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    //calls onDestroy
                                    ReplayActivity.this.finish(); //go back to SelectionFragment
                                    ReplayActivity.this.overridePendingTransition(
                                            android.R.anim.slide_in_left,
                                            android.R.anim.slide_out_right);
                                }
                            })
                    .setNegativeButton(getString(android.R.string.no), doNothing).show(); //no button
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            replayStop();
            if (!replayOngoing) {
                return super.onKeyDown(keyCode, event);
            } else {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        //the left arrow at the top of the screen - user wants to go back
        if (item.getItemId() == android.R.id.home) {
            replayStop();
        }
        return true;
    }

    /**
     * TODO abstract it out to a separate class for better OOD.
     * AsyncTask was also deprecated in the Summer of 2020.
     * <p>
     * This is the main part of the app, which runs the tests. To fully understand this part refer
     * to the API documentation and research paper by Fangfan Li. This class implements the test
     * procedure in a serial fashion i.e. step by step.
     * <p>
     * Because tests take a long time to run, AsyncTask is used to have the tests run on a dedicated
     * thread instead of running on the UI (main) thread, which makes UI thread more responsive to
     * user. AsyncTask makes it easier to update UI.
     */
    private class TraceRunAsync extends AsyncTask<String, String, Void> {
        // TODO switch to better data structure, beans are not suitable for android
        private CombinedAppJSONInfoBean appData;
        private ApplicationBean app;
        private final ArrayList<String> servers = new ArrayList<>(); //servers to run the replays to
        private String metadataServer;
        private final ArrayList<WebSocketConnection> wsConns = new ArrayList<>();
        private UpdateUIBean updateUIBean;
        private boolean doTest; //add a tail for testing data if true
        private final ArrayList<String> analyzerServerUrls = new ArrayList<>();
        //true if confirmation replay should run if the first replay has differentiation
        private boolean confirmationReplays;
        private boolean useDefaultThresholds;
        private int a_threshold;
        private int ks2pvalue_threshold;
        private SharedPreferences settings;
        private SSLSocketFactory sslSocketFactory = null;
        private HostnameVerifier hostnameVerifier = null;
        //private boolean rerun = false; //true if confirmation replay
        //randomID, historyCount, and testId identifies the user, test number, and replay number
        //server uses these to determine which results to send back to client
        private String randomID; //unique user ID for certain device
        //historyCount is the test number; current number can be seen as number of apps run
        //or number of times user hit the run button for ports
        private int historyCount;
        //testId is replay number in a test
        //for apps - 0 is original replay, 1 is random replay
        //for ports - 0 non-443 port, 1 is port 443
        private int testId;
        private JSONArray results; //results containing apps or the port arrays (below)
        private final ArrayList<Timer> timers = new ArrayList<>(); //for stopping sendRequest timers
        private final ArrayList<Integer> numMLab = new ArrayList<>(); //number of tries before successful MLab connection

        // this method handles all UI updates, running on main thread
        @Override
        protected void onProgressUpdate(@NonNull String... values) {
            if (values[0].equalsIgnoreCase("updateStatus")) {
                //values[1] is app name, values[2] is the status
                //this method runs on UI thread, so timing is undefined. If this method is called
                //right before the next test starts, the app might be different by the time this
                //method is run
                if (values[1].equals(app.getName())) { //make status is being applied to correct app
                    app.setStatus(values[2]);
                } else {
                    for (ApplicationBean a : selectedApps) {
                        if (values[1].equals(a.getName())) {
                            a.setStatus(values[2]);
                            break;
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            } else if (values[0].equalsIgnoreCase("updateUI")) {
                //update progress bar
                if (prgBar.getVisibility() == View.GONE || prgBar.getVisibility() == View.INVISIBLE) {
                    prgBar.setVisibility(View.VISIBLE);
                }
                prgBar.setProgress(updateUIBean.getProgress());
            } else if (values[0].equalsIgnoreCase("finishProgress")) {
                //values[1] is 1 if just finished first replay; 2 if finished second replay
                int iteration = Integer.parseInt(values[1]);
                updateUIBean.finishProgress(Integer.parseInt(values[1]));
                if (iteration == 1) { //finished first replay
                    prgBar.setProgress(50);
                } else { //finished test
                    prgBar.setProgress(100);
                    //hide progress bar
                    prgBar.setVisibility(View.GONE);
                }
            } else if (values[0].equalsIgnoreCase("makeToast")) {
                Toast.makeText(ReplayActivity.this, values[1], Toast.LENGTH_LONG).show();
            } else if (values[0].equalsIgnoreCase("makeDialog")) {
                //Display dialogue when replays finished or if there is an error that needs pop up.
                //values[1] is title of dialogue, values[2] is message of dialogue
                //values[3] true if the app should go back to the SelectionFragment when the user
                //          clicks OK; else the app stays in the ReplayActivity
                boolean exitReplays = Boolean.parseBoolean(values[3]);
                new AlertDialog.Builder(ReplayActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(values[1])
                        .setMessage(values[2])
                        .setPositiveButton(getString(android.R.string.ok),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (exitReplays) {
                                            replayStop();
                                        }

                                        //All tests just finished
                                        if (diffApps.size() != 0 || inconclusiveApps.size() != 0) {
                                            displayRerunTomoButtons();
                                        }
                                    }
                                }).show();
                if (exitReplays) {
                    Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.simple_error);
                } else {
                    Objects.requireNonNull(getSupportActionBar()).setTitle(R.string.test_results);
                }
            } else {
                Log.e("onProgressUpdate", "unknown instruction!");
            }
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            replayOngoing = true;
        }

        @Override
        protected void onPostExecute(Void none) {
            replayOngoing = false;
        }

        @Override
        protected void onCancelled() {
            Log.i("Replay", "Forced exit");
        }

        /**
         * Reset the progress bar to 0. Need to reset the actual progress bar and the bean keeping
         * track of the progress.
         */
        private void clearProgressBar() {
            prgBar.setProgress(0);
            updateUIBean.clearProgress();
        }

        /**
         * This method begins process to run tests.
         * Step 1: Initialize several variables.
         * Step 2: Run tests.
         * Step 3: Save results.
         *
         * @param args no args
         * @return null
         */
        @Override
        protected Void doInBackground(String... args) {
            //set each app's status to "Waiting"
            for (ApplicationBean app : selectedApps) {
                app.setStatus(getResources().getString(R.string.pending));
            }

            //adapter.notifyDataSetChanged();

            // Keep checking if the user exited from ReplayActivity or not
            // TODO find a better way stop the tests immediately without continues checking
            if (isCancelled()) {
                return null;
            }
            if (isNetworkUnavailable()) { // TODO subscribe to network events instead of using this
                displayNoNetworkDialogue();
                return null;
            }

            /*
             * Step 1: Initialize several variables.
             */
            updateUIBean = new UpdateUIBean(); // TODO remove this but with caution
            // TODO Test different configs by changing properties in assets.configuration.properties
            //  avoid changing the code directly
            Config.readConfigFile(Consts.CONFIG_FILE, context);
            //get settings from SettingsFragment
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (isTomography) { //need to use MLab servers for tomography tests
                serverDisplay = Consts.DEFAULT_SERVER;
            } else {
                serverDisplay = sharedPrefs.getString(getString(R.string.pref_server_key),
                        Consts.DEFAULT_SERVER); //get server from SettingsFragment
            }
            // metadata here is user's network type device used geolocation if permitted etc
            metadataServer = Consts.METADATA_SERVER;
            if (!setupServersAndCertificates(serverDisplay, metadataServer)) {
                return null;
            }

            //get from preferences from SettingsFragment
            confirmationReplays = sharedPrefs.getBoolean("pref_multiple_tests", true);
            useDefaultThresholds = sharedPrefs.getBoolean("pref_switch", true);
            a_threshold = Integer.parseInt(Objects.requireNonNull(
                    sharedPrefs.getString("pref_threshold_area", "10")));
            ks2pvalue_threshold = Integer.parseInt(Objects.requireNonNull(
                    sharedPrefs.getString("pref_threshold_ks2p", "5")));

            // generate or retrieve an id for this phone
            boolean hasID = sharedPrefs.getBoolean("hasID", false);
            if (!hasID) {
                randomID = new RandomString(10).nextString();
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putBoolean("hasID", true);
                editor.putString("ID", randomID);
                editor.apply();
            } else {
                randomID = sharedPrefs.getString("ID", null);
            }

            // to get historyCount
            settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);

            // generate or retrieve a historyCount for this phone
            boolean hasHistoryCount = settings.getBoolean("hasHistoryCount", false);
            if (!hasHistoryCount) {
                historyCount = 0;
                SharedPreferences.Editor editor = settings.edit();
                editor.putBoolean("hasHistoryCount", true);
                editor.putInt("historyCount", historyCount);
                editor.apply();
            } else {
                historyCount = settings.getInt("historyCount", -1);
                if (historyCount == -1) { // check if retrieve historyCount succeeded
                    throw new RuntimeException();
                }
            }

            testId = -1;
            doTest = false;
            results = new JSONArray(); //init results

            //timing allows replays to be run with the same timing as when they were recorded
            //for example, if a YouTube video was paused for 2 seconds during recording, then the
            //replay will also pause for 2 seconds at that point in the replay
            //port tests try to run as fast as possible, so there is no timing for them
            Config.set("timing", runPortTests ? "false" : "true");
            String serversStr = servers.toString();
            Config.set("server", serversStr.substring(1, serversStr.length() - 1));
            String publicIP = getPublicIP("80"); //get user's IP address
            Config.set("publicIP", publicIP);
            Log.d("Replay", "public IP: " + publicIP);
            //If cannot connect to server, display an error and stop tests
            if (publicIP.equals("-1")) {
                publishProgress("makeDialog", getString(R.string.simple_error),
                        getString(R.string.error_no_connection), "true");
                return null;
            }

            if (isCancelled()) {
                return null;
            }
            if (isNetworkUnavailable()) {
                displayNoNetworkDialogue();
                return null;
            }

            /*
             * Step 2: Run tests.
             */
            boolean firstApp = true;
            for (ApplicationBean app : selectedApps) {
                if (isCancelled()) {
                    return null;
                }

                if (!firstApp && mlabServerUsed) {
                    if (!setupServersAndCertificates(serverDisplay, null)) {
                        return null;
                    }
                }
                this.app = app; // Set the app to run test for
                this.app.setArcepNeedsAlerting(false);

                if (isCancelled()) {
                    return null;
                }

                //make sure progress bar is clear
                clearProgressBar();
                publishProgress("updateUI"); //make progress bar visible
                boolean rerun = runTest(false); // Run the test on this.app
                if (!isTomography && rerun) {
                    //run confirmation test if confirmation tests are switched on in Settings and
                    //first test was inconclusive or had differentiation
                    //don't run confirmation tests for tomography tests
                    //make sure progress bar is clear
                    clearProgressBar();
                    publishProgress("updateUI"); //make progress bar visible
                    runTest(true);
                }

                //clean up
                for (WebSocketConnection ws : wsConns) {
                    if (ws != null) {
                        ws.close();
                    }
                }
                for (Timer t : timers) {
                    t.cancel();
                }
                timers.clear();

                firstApp = false;

                if (isCancelled()) {
                    return null;
                }
            }

            /*
             * Step 3: Save results.
             */
            if (results.length() > 0) {
                Log.i("Result Channel", "Storing results");
                saveResults();
            }
            if (isCancelled()) {
                return null;
            }
            publishProgress("makeDialog", getString(R.string.replay_finished_title),
                    "", "false");
            Log.i("Result Channel", "Exiting normally");

            return null;
        }

        /**
         * Save results of the current tests to SharedPreference, so that it can be displayed in
         * the ResultsFragment.
         */
        private void saveResults() {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US);
            String strDate = dateFormat.format(new Date());
            // get current results, if not exist, create a json
            // object with date as the key
            JSONObject resultsWithDate;
            try {
                resultsWithDate = new JSONObject(settings.getString("lastResult", "{}"));
            } catch (JSONException e) {
                resultsWithDate = new JSONObject();
            }
            // remove one history result if there are too many
            if (resultsWithDate.length() >= 10) {
                Iterator<String> it = resultsWithDate.keys();
                if (it.hasNext()) {
                    resultsWithDate.remove(it.next());
                } else {
                    Log.w("Result Channel", "iterator doesn't have next but length is not 0");
                }
            }

            try {
                resultsWithDate.put(strDate, results);
            } catch (JSONException e) {
                Log.e("saveResults", "Error saving results, " + e);
                return;
            }

            SharedPreferences.Editor editor = settings.edit();
            editor.putString("lastResult", resultsWithDate.toString());
            editor.apply();
        }

        /**
         * Gets IPs of server and metadata server. Connects to MLab authentication WebSocket if
         * necessary. Gets necessary certificates for server and metadata server.
         *
         * @param server         the hostname of the server to connect to
         * @param metadataServer the hostname of the metadata server to connect to
         * @return true if everything properly sets up; false otherwise
         */
        private boolean setupServersAndCertificates(@NonNull String server, String metadataServer) {
            // We first resolve the IP of the server and then communicate with the server
            // Using IP only, because we have multiple server under same domain and we want
            // the client not to switch server during a test run
            //wehe4.meddle.mobi 90% returns 10.0.0.0 (use MLab), 10% legit IP (is Amazon)
            if ((Double.parseDouble(BuildConfig.VERSION_NAME) >= 3.46) && (server.equals("wehe3.meddle.mobi"))) {
                server = "wehe4.meddle.mobi";
            }
            servers.clear();
            //extreme hack to temporarily get around French DNS look up issue
            if (server.equals("wehe4.meddle.mobi")) {
                servers.add("10.0.0.0");
                Log.d("Serverhack", "hacking wehe4");
            } else {
                servers.add(getServerIP(server));
                if (servers.get(servers.size() - 1).equals("")) {
                    publishProgress("makeDialog", getString(R.string.simple_error),
                            getString(R.string.error_unknown_host), "true");
                    return false;
                }
            }
            // A hacky way to check server IP version
            boolean serverIPisV6 = false;
            if (servers.get(0).contains(":")) {
                serverIPisV6 = true;
            }
            Log.d("ServerIPVersion", servers.get(0) + (serverIPisV6 ? "IPV6" : "IPV4"));
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
            int numTests = isTomography ? Consts.NUM_TOMOGRAPHY_TESTS : 1;
            mlabServerUsed = false;
            if (servers.get(0).equals("10.0.0.0") || serverIPisV6) {
                mlabServerUsed = true;
                servers.remove(0);
                wsConns.clear();
                try {
                    int numTries = 0; //tracks num tries before successful MLab connection
                    int wsID; //WebSocket id
                    JSONObject mLabResp = sendRequest(Consts.MLAB_SERVERS, "GET", false, null, null);
                    //TODO: make sure this outer try really necessary; check what happens if below line fails; will it exit gracefully?
                    JSONArray mLabServers = (JSONArray) mLabResp.get("results"); //get MLab servers list
                    for (int i = 0; wsConns.size() < numTests && i < mLabServers.length(); i++) {
                        //try the 4 servers before going to wehe2
                        try {
                            i++;
                            wsID = wsConns.size();
                            numTries++;
                            JSONObject serverObj = (JSONObject) mLabServers.get(i); //get first MLab server
                            server = "wehe-" + serverObj.getString("machine"); //SideChannel URL
                            String mLabURL = ((JSONObject) serverObj.get("urls"))
                                    .getString(Consts.MLAB_WEB_SOCKET_SERVER_KEY); //authentication URL

                            Log.d("WebSocket", "Attempting to connect to server " + i
                                    + ": " + server);
                            wsConns.add(new WebSocketConnection(wsID, new URI(mLabURL))); //connect to WebSocket

                            //code below runs only if successful connection to WebSocket
                            Log.d("WebSocket", "New WebSocket (id: " + wsID + ") connectivity check: "
                                    + (wsConns.get(wsID).isOpen() ? "CONNECTED" : "CLOSED") + " TO " + server);
                            servers.add(getServerIP(server));
                            numMLab.add(numTries);
                            numTries = 0;
                        } catch (URISyntaxException | JSONException | DeploymentException
                                | NullPointerException | InterruptedException e) {
                            //failed to connect to WebSocket, try next one
                            Log.w("WebSocket", "Failed to connect to WebSocket", e);
                        }
                    }
                    if (wsConns.size() != numTests) {
                        //if can't connect to mlab, try an amazon server using wehe2.meddle.mobi
                        Log.i("GetReplayServerIP", "Can't get MLab server, trying Amazon");
                        servers.clear();
                        for (WebSocketConnection ws : wsConns) { //close opened WebSockets
                            if (ws.isOpen()) {
                                ws.close();
                            }
                        }
                        wsConns.clear();
                        if (isTomography) {
                            //user can't run tomography tests if can't connect to MLab servers
                            //exit tests in this case
                            publishProgress("makeDialog", getString(R.string.simple_error),
                                    getString(R.string.tomography_not_supported), "true");
                            return false;
                        }
                        numTests = 1;
                        servers.add(getServerIP("wehe2.meddle.mobi"));
                    }
                } catch (JSONException | NullPointerException e) {
                    Log.e("WebSocket", "Can't retrieve M-Lab servers", e);
                }
            }

            for (int i = 0; i < numTests; i++) {
                if (servers.get(i).equals("")) { //check to make sure IP was returned by getServerIP
                    publishProgress("makeDialog", getString(R.string.simple_error),
                            getString(R.string.error_unknown_host), "true");
                    if (wsConns.get(i) != null && wsConns.get(i).isOpen()) {
                        wsConns.get(i).close();
                    }
                    return false;
                }
            }
            Log.d("GetReplayServerIP", "Server IP: " + servers);
            generateServerCertificate(true);

            //get URL(s) for analysis and results
            int port = Integer.parseInt(Config.get("result_port")); //get port to send tests through
            analyzerServerUrls.clear();
            for (String srvr : servers) {
                analyzerServerUrls.add("https://" + srvr + ":" + port + "/Results");
                Log.d("Result Channel", "path: " + srvr + " port: " + port);
            }

            if (metadataServer != null) {
                this.metadataServer = getServerIP(metadataServer);
                if (this.metadataServer.equals("")) { //get IP and certificates for metadata server
                    publishProgress("makeDialog", getString(R.string.simple_error),
                            getString(R.string.error_unknown_meta_host), "true");
                    return false;
                }
                generateServerCertificate(false);
            }
            return true;
        }

        /**
         * Does a DNS lookup on a hostname.
         *
         * @param server the hostname to be resolved
         * @return the IP of the host; empty string if there is an error doing so.
         */
        private String getServerIP(String server) {
            Log.d("getServerIP", "Server hostname: " + server);
            InetAddress address;
            for (int i = 0; i < 5; i++) { //5 attempts to lookup the IP
                try {
                    server = InetAddress.getByName(server).getHostAddress(); //DNS lookup
                    address = InetAddress.getByName(server);
                    if (address instanceof Inet4Address) {
                        return server;
                    }
                    if (address instanceof Inet6Address) {
                        return "[" + server + "]";
                    }
                } catch (UnknownHostException e) {
                    if (i == 4) {
                        Log.e("getServerIP", "Failed to get IP of server", e);
                    } else {
                        Log.w("getServerIP", "Failed to get IP of server, trying again");
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Log.w("getServerIP", "Sleep interrupted", ex);
                    }
                }
            }
            return "";
        }

        /**
         * Gets the certificates for the servers
         *
         * @param main true if main server; false if metadata server
         */
        private void generateServerCertificate(boolean main) {
            try {
                String server = main ? "main" : "metadata";
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate ca;
                try (InputStream caInput = getResources().openRawResource(main ? R.raw.main : R.raw.metadata)) {
                    ca = cf.generateCertificate(caInput);
                    Log.d("Certificate", server + "=" + ((X509Certificate) ca).getIssuerDN());
                }

                // Create a KeyStore containing our trusted CAs
                String keyStoreType = KeyStore.getDefaultType();
                KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                keyStore.load(null, null);
                keyStore.setCertificateEntry(server, ca);

                // Create a TrustManager that trusts the CAs in our KeyStore
                String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                tmf.init(keyStore);

                // Create an SSLContext that uses our TrustManager
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, tmf.getTrustManagers(), null);
                if (main) {
                    sslSocketFactory = context.getSocketFactory();
                    hostnameVerifier = (hostname, session) -> true;
                }
            } catch (CertificateException | NoSuchAlgorithmException | KeyStoreException
                    | KeyManagementException | IOException e) {
                Log.e("Certificates", "Error generating certificates", e);
            }
        }

        /**
         * Get IP of user's device.
         *
         * @param port port to run replays
         * @return user's public IP or -1 if cannot connect to the server
         */
        private String getPublicIP(String port) {
            String publicIP = "127.0.0.1";

            if (servers.size() != 0 && !servers.get(0).equals("127.0.0.1")) {
                String url = "http://" + servers.get(0) + ":" + port + "/WHATSMYIPMAN";
                Log.d("getPublicIP", "url: " + url);

                int numFails = 0;
                while (publicIP.equals("127.0.0.1")) {
                    try {
                        URL u = new URL(url);
                        //go to server
                        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(5000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(
                                conn.getInputStream()));
                        StringBuilder buffer = new StringBuilder();
                        String input;

                        while ((input = in.readLine()) != null) { //read IP address
                            buffer.append(input);
                        }
                        in.close();
                        conn.disconnect();
                        publicIP = buffer.toString();
                        InetAddress address = InetAddress.getByName(publicIP);
                        if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
                            Log.e("getPublicIP", "wrong format of public IP: " + publicIP);
                            throw new UnknownHostException();
                        }
                        isIPv6 = address instanceof Inet6Address;
                        if (publicIP.equals("")) {
                            publicIP = "-1";
                        }
                        Log.d("getPublicIP", "public IP: " + publicIP);
                    } catch (UnknownHostException e) {
                        Log.w("getPublicIP", "failed to get public IP!", e);
                        publicIP = "127.0.0.1";
                        break;
                    } catch (IOException e) {
                        Log.w("getPublicIP", "Can't connect to server");
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e1) {
                            Log.w("getPublicIP", "Sleep interrupted", e1);
                        }
                        if (++numFails == 5) { //Cannot connect to server after 5 tries
                            Log.w("getPublicIP", "Returning -1", e);
                            publicIP = "-1";
                            break;
                        }
                    }
                }
            } else {
                Log.w("getPublicIP", "server ip is not available: " + servers.get(0));
            }
            return publicIP;
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
        private JSONObject ask4analysis(String url, String id, int historyCount) {
            HashMap<String, String> pairs = new HashMap<>();

            pairs.put("command", "analyze");
            pairs.put("userID", id);
            pairs.put("historyCount", String.valueOf(historyCount));
            pairs.put("testID", "1");

            return sendRequest(url, "POST", true, null, pairs);
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
        private JSONObject getSingleResult(String url, String id, int historyCount) {
            ArrayList<String> data = new ArrayList<>();

            data.add("userID=" + id);
            data.add("command=" + "singleResult");
            data.add("historyCount=" + historyCount);
            data.add("testID=1");

            return sendRequest(url, "GET", true, data, null);
        }

        /**
         * Send a GET or POST request to the server.
         *
         * @param url    URL to the server
         * @param method either GET or POST
         * @param main   true if request is to main server; false otherwise
         * @param data   data to send to server in a GET request, null if a POST request or if no
         *               data to send to server
         * @param pairs  data to send to server in a POST request, null if a GET request
         * @return a response from the server in the form of a JSONObject, null if error
         */
        private JSONObject sendRequest(String url, @NonNull String method, boolean main,
                                       ArrayList<String> data, HashMap<String, String> pairs) {
            final JSONObject[] json = {null};
            final HttpsURLConnection[] conn = new HttpsURLConnection[1];
            final boolean[] readyToReturn = {false};
            Thread serverComm = new Thread(new Runnable() {
                @Override
                public void run() {
                    String url_string = url;
                    if (method.equalsIgnoreCase("GET")) {
                        if (data != null) {
                            String dataURL = URLEncoder(data);
                            url_string += "?" + dataURL;
                        }
                        Log.d("Send GET Request", url_string);

                        for (int i = 0; i < 3; i++) {
                            try {
                                //connect to server
                                URL u = new URL(url_string);
                                //send data to server
                                conn[0] = (HttpsURLConnection) u.openConnection();
                                if (main && hostnameVerifier != null && sslSocketFactory != null) {
                                    conn[0].setHostnameVerifier(hostnameVerifier);
                                    conn[0].setSSLSocketFactory(sslSocketFactory);
                                }
                                conn[0].setConnectTimeout(8000);
                                conn[0].setReadTimeout(8000);
                                BufferedReader in = new BufferedReader(new InputStreamReader(
                                        conn[0].getInputStream()));
                                StringBuilder buffer = new StringBuilder();
                                String input;

                                // parse BufferReader rd to StringBuilder res
                                while ((input = in.readLine()) != null) { //read response from server
                                    buffer.append(input);
                                }

                                in.close();
                                conn[0].disconnect();
                                json[0] = new JSONObject(buffer.toString()); // parse String to json file
                                break;
                            } catch (IOException e) {
                                Log.e("Send Request", "sendRequest GET failed", e);
                            } catch (JSONException e) {
                                Log.e("Send Request", "JSON Parse failed", e);
                            }
                        }
                    } else if (method.equalsIgnoreCase("POST")) {
                        Log.d("Send POST Request", url_string);

                        try {
                            //connect to server
                            URL u = new URL(url_string);
                            conn[0] = (HttpsURLConnection) u.openConnection();
                            conn[0].setHostnameVerifier(hostnameVerifier);
                            conn[0].setSSLSocketFactory(sslSocketFactory);
                            conn[0].setConnectTimeout(5000);
                            conn[0].setReadTimeout(5000);
                            conn[0].setRequestMethod("POST");
                            conn[0].setDoInput(true);
                            conn[0].setDoOutput(true);

                            OutputStream os = conn[0].getOutputStream();
                            BufferedWriter writer = new BufferedWriter(
                                    new OutputStreamWriter(os, StandardCharsets.UTF_8));
                            writer.write(paramsToPostData(pairs)); //send data to server

                            writer.flush();
                            writer.close();
                            os.close();

                            BufferedReader in = new BufferedReader(new InputStreamReader(
                                    conn[0].getInputStream()));
                            StringBuilder buffer = new StringBuilder();
                            String input;

                            // parse BufferReader rd to StringBuilder res
                            while ((input = in.readLine()) != null) { //read response from server
                                buffer.append(input);
                            }
                            in.close();
                            conn[0].disconnect();
                            json[0] = new JSONObject(buffer.toString()); // parse String to json file.
                        } catch (JSONException e) {
                            Log.e("Send Request", "convert string to json failed", e);
                            json[0] = null;
                        } catch (IOException e) {
                            Log.e("Send Request", "sendRequest POST failed", e);
                            json[0] = null;
                        }
                    }
                    readyToReturn[0] = true;
                }
            });
            serverComm.start();
            Timer t = new Timer();
            timers.add(t);
            //timeout server after 8 sec; server timeout field times out only when nothing is sent;
            //if stuff sends too slowly, it could take forever, so this external timer prevents that
            t.schedule(new TimerTask() {
                @Override
                public void run() { //set timer to timeout the thread if max time has been reached for replay
                    if (conn[0] != null) {
                        conn[0].disconnect();
                    }
                    readyToReturn[0] = true;
                }
            }, 8000);
            //wait until ready to move on (i.e. when result retrieved or timeout), as threads don't
            //block execution
            while (!readyToReturn[0]) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Log.w("Send Request", "Interrupted", e);
                }
            }
            return json[0];
        }

        /**
         * Overload URLEncoder to encode map to a url for a GET request.
         *
         * @param map data to be converted into a string to send to the server
         * @return encoded string containing data to send to server
         */
        @NonNull
        private String URLEncoder(@NonNull ArrayList<String> map) {
            StringBuilder data = new StringBuilder();
            for (String s : map) {
                if (data.length() > 0) {
                    data.append("&");
                }
                data.append(s);
            }
            return data.toString();
        }

        /**
         * Encodes data into a string to send POST request to server.
         *
         * @param params data to convert into string to send to server
         * @return an encoded string to send to the server
         */
        @NonNull
        private String paramsToPostData(@NonNull HashMap<String, String> params) {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    result.append("&");
                }

                try {
                    result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    result.append("=");
                    result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    Log.e("paramsToPostData", "Encoding error", e);
                }
            }
            return result.toString();
        }

        /**
         * Reads the replay files and loads them into memory as a bean.
         *
         * @param filename filename of the replay
         * @param context  the application context
         * @return a bean containing information about the replay
         */
        @NonNull
        private CombinedAppJSONInfoBean unpickleJSON(String filename, @NonNull Context context) {
            AssetManager assetManager;
            InputStream inputStream;
            CombinedAppJSONInfoBean appData = new CombinedAppJSONInfoBean(); //info about replay
            ArrayList<RequestSet> Q = new ArrayList<>(); //list of packets for replay
            try {
                assetManager = context.getAssets();
                inputStream = assetManager.open(filename); //open replay file
                int size = inputStream.available();
                byte[] buffer = new byte[size];
                inputStream.read(buffer);
                inputStream.close();

                //convert file contents to JSONArray object
                String jsonStr = new String(buffer, StandardCharsets.UTF_8);
                JSONArray json = new JSONArray(jsonStr);

                JSONArray qArray = (JSONArray) json.get(0); //the packets in a replay file
                for (int i = 0; i < qArray.length(); i++) {
                    RequestSet tempRS = new RequestSet();
                    JSONObject dictionary = qArray.getJSONObject(i);
                    tempRS.setc_s_pair((String) dictionary.get("c_s_pair")); //client-server pair
                    tempRS.setPayload(UtilsManager.hexStringToByteArray(
                            (String) dictionary.get("payload")));
                    tempRS.setTimestamp((Double) dictionary.get("timestamp"));

                    //for tcp
                    if (dictionary.has("response_len")) { //expected length of response
                        tempRS.setResponse_len((Integer) dictionary.get("response_len"));
                    }
                    if (dictionary.has("response_hash")) {
                        tempRS.setResponse_hash(dictionary.get("response_hash").toString());
                    }
                    //for udp
                    if (dictionary.has("end"))
                        tempRS.setEnd((Boolean) dictionary.get("end"));

                    Q.add(tempRS);
                }

                appData.setQ(Q);

                //udp
                JSONArray portArray = (JSONArray) json.get(1); //udp client ports
                ArrayList<String> portStrArray = new ArrayList<>();
                for (int i = 0; i < portArray.length(); i++) {
                    portStrArray.add(portArray.getString(i));
                }
                appData.setUdpClientPorts(portStrArray);

                //for tcp
                JSONArray csArray = (JSONArray) json.get(2); //c_s_pairs
                ArrayList<String> csStrArray = new ArrayList<>();
                for (int i = 0; i < csArray.length(); i++) {
                    csStrArray.add((String) csArray.get(i));
                }
                appData.setTcpCSPs(csStrArray);
                appData.setReplayName((String) json.get(3)); //name of replay

            } catch (JSONException | IOException e) {
                Log.e("UnpickleJSON", "Error reading test files", e);
            }
            return appData;
        }

        /**
         * Sets the status of the app to be inconclusive if there is an error.
         *
         * @param msg error message to display to the user
         */
        private void setInconclusive(String msg) {
            if (!inconclusiveApps.contains(app)) {
                inconclusiveApps.add(app);
            }
            app.setError(msg);
            publishProgress("updateStatus", app.getName(), getString(R.string.inconclusive));
        }

        /**
         * Run test. This method is called for every app/port the user selects. It is also called if
         * differentiation is detected for a test, and confirmation setting is enabled to run a
         * second test for the app/port to confirm if there is differentiation.
         * <p>
         * Each test has two replays. For apps, the replays consist of the original replay,
         * which contains actual traffic from that app, and a random replay, which replaces the
         * content of the original replay with random traffic. For ports, the "original" replay is
         * the port that is being tested. The "random" replay is port 443. The method uses "open" to
         * denote the "original" replay and "random" to denote the "random" replay.
         * <p>
         * There are three main steps in this method:
         * Step A: Flip a coin to decide which replay type to run first.
         * Step B: Run replays.
         * Step C: Determine if there is differentiation.
         * <p>
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
        private boolean runTest(boolean isConfirmation) {
            String[] types;
            /*
             * Step A: Flip a coin to decide which replay type to run first.
             */
            //"random" test for ports is port 443
            if (Math.random() < 0.5) {
                types = new String[]{"open", "random"};
            } else {
                types = new String[]{"random", "open"};
            }

            /*
             * Step B: Run replays.
             */
            int iteration = 1;
            boolean portBlocked = false;
            for (String channel : types) {
                for (WebSocketConnection ws : wsConns) {
                    if (ws != null) { //if using MLab, check that still connected
                        Log.d("WebSocket", "Before running test WebSocket (id: "
                                + ws.getId() + ") connectivity check: "
                                + (ws.isOpen() ? "CONNECTED" : "CLOSED"));
                    }
                }

                if (isCancelled()) { //user cancels running tests
                    return false;
                }
                if (isNetworkUnavailable()) { //no network available
                    displayNoNetworkDialogue();
                    return false;
                }

                /*
                 * Step 0: Initialize variables.
                 */
                // Based on the type selected load open or random trace of given application
                if (channel.equalsIgnoreCase("open")) {
                    this.appData = unpickleJSON(app.getDataFile(), context);
                } else if (channel.equalsIgnoreCase("random")) {
                    this.appData = unpickleJSON(app.getRandomDataFile(), context);
                } else {
                    Log.wtf("replayIndex", "replay name error: " + channel);
                }

                try {
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getResources().getString(R.string.create_side_channel));
                    int sideChannelPort = Integer.parseInt(Config.get("combined_sidechannel_port"));

                    Log.d("Servers", servers + " metadata " + metadataServer);
                    //The Side Channel communicates, in bytes mode, with the server to set up the
                    //tests, start them, end them, and let the server know what exactly is going on.
                    //The tests themselves are conducted over 2 other channels with the server -
                    //the TCP channel for TCP tests and UDP channel for UDP tests. These channels
                    //can be found in CTCPClientThread.java, CUDPClient.java, CombinedReceiverThread.java,
                    //and CombinedNotifierThread.java
                    //Server handles communication in handle() function in server_replay.py in server
                    //code
                    ArrayList<CombinedSideChannel> sideChannels = new ArrayList<>();
                    ArrayList<JitterBean> jitterBeans = new ArrayList<>();
                    //lots of for loops and ArrayLists in this method - tomography tests require
                    //multiple tests to run at once; each test requires their own set of variables
                    //so the variables for each test are stored in ArrayLists. Normal tests will only
                    //need 1 test, so 1 element in the ArrayLists, but tomography tests will have more
                    int id = 0;
                    for (String server : servers) {
                        sideChannels.add(new CombinedSideChannel(id, sslSocketFactory,
                                server, sideChannelPort, appData.isTCP()));
                        jitterBeans.add(new JitterBean());
                        id++;
                    }

                    // increase history count only once during the run of a single test or set of
                    // tomography tests
                    if (iteration == 1) {
                        // First update historyCount
                        historyCount++;
                        // Then write current historyCount to applicationBean
                        app.setHistoryCount(historyCount);
                        settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("historyCount", historyCount);
                        editor.apply();
                        Log.d("Replay", "historyCount: " + historyCount);
                    }

                    // This random ID is used to map the test results to a specific instance of app
                    // It is generated only once and saved thereafter
                    if (randomID == null) {
                        Log.e("RecordReplay", "randomID does not exist!");
                        setInconclusive(getString(R.string.error_no_user_id));
                        return false;
                    }

                    // initialize endOfTest value
                    boolean endOfTest = false; //true if last replay in this test is running
                    if (channel.equalsIgnoreCase(types[types.length - 1])) {
                        Log.i("Replay", "last replay running " + types[types.length - 1] + "!");
                        endOfTest = true;
                    }

                    //Get user's IP address
                    String replayPort = "80";
                    String ipThroughProxy = "127.0.0.1";
                    if (appData.isTCP()) {
                        for (String csp : appData.getTcpCSPs()) {
                            replayPort = csp.substring(csp.lastIndexOf('.') + 1);
                        }
                        ipThroughProxy = getPublicIP(replayPort);
                        if (ipThroughProxy.equals("-1")) { //port is blocked; move on to next replay
                            //TODO: check if ui needed here
                            portBlocked = true;
                            iteration++;
                            continue;
                        }
                    }

                    // testId is how server knows if the trace ran was open or random
                    testId = channel.equalsIgnoreCase("open") ? 0 : 1;

                    if (doTest) {
                        Log.w("Replay", "include -Test string");
                    }

                    /*
                     * Step 1: Tell server(s) about the replay that is about to happen.
                     */
                    int i = 0;
                    for (CombinedSideChannel sc : sideChannels) {
                        // This is group of values that is used to track traces on server
                        // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
                        //set extra string to number tries needed to access MLab server
                        Config.set("extraString", numMLab.size() == 0 ? "0" : numMLab.get(i).toString());
                        sc.declareID(appData.getReplayName(), endOfTest ? "True" : "False",
                                randomID, String.valueOf(historyCount), String.valueOf(testId),
                                doTest ? Config.get("extraString") + "-Test" : Config.get("extraString"),
                                ipThroughProxy, BuildConfig.VERSION_NAME);

                        // This tuple tells the server if the server should operate on packets of traces
                        // and if so which packets to process
                        sc.sendChangeSpec(-1, "null", "null");
                        i++;
                    }

                    if (isCancelled()) {
                        return false;
                    }
                    if (isNetworkUnavailable()) {
                        displayNoNetworkDialogue();
                        return false;
                    }

                    /*
                     * Step 2: Ask server(s) for permission to run replay.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.ask4permission));
                    // Now to move forward we ask for server permission
                    ArrayList<Integer> numOfTimeSlices = new ArrayList<>();
                    for (CombinedSideChannel sc : sideChannels) {
                        String[] permission = sc.ask4Permission();
                        String status = permission[0].trim();

                        Log.d("Replay", "Channel " + sc.getId() + ": permission[0]: "
                                + status + " permission[1]: " + permission[1]);

                        String permissionError = permission[1].trim();
                        String customError;
                        if (status.equals("0")) {
                            // These are the different errors that server can report
                            switch (permissionError) {
                                case "1": //server cannot identify replay
                                    customError = getString(R.string.error_unknown_replay);
                                    break;
                                case "2": //only one replay can run at a time per IP
                                    customError = getString(R.string.error_IP_connected);
                                    break;
                                case "3": //server CPU > 95%, disk > 95%, or bandwidth > 2000 Mbps
                                    customError = getString(R.string.error_low_resources);
                                    break;
                                default:
                                    customError = getString(R.string.error_unknown);
                            }
                            setInconclusive(customError);
                            return false;
                        }
                        numOfTimeSlices.add(Integer.parseInt(permission[2].trim(), 10));
                    }

                    /*
                     * Step 3: Send noIperf.
                     */
                    for (CombinedSideChannel sc : sideChannels) {
                        sc.sendIperf(); // always send noIperf here
                    }

                    if (isCancelled()) {
                        return false;
                    }
                    if (isNetworkUnavailable()) {
                        displayNoNetworkDialogue();
                        return false;
                    }

                    /*
                     * Step 4: Send device info.
                     */
                    for (CombinedSideChannel sc : sideChannels) {
                        sc.sendMobileStats(Config.get("sendMobileStats"), getApplicationContext());
                    }

                    /*
                     * Step 5: Get port mapping from server.
                     */
                    /*
                     * Ask for port mapping from server. For some reason, port map
                     * info parsing was throwing error. so, I put while loop to do
                     * this until port mapping is parsed successfully.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.receive_server_port_mapping));

                    ArrayList<HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>> serverPortsMaps
                            = new ArrayList<>();
                    ArrayList<UDPReplayInfoBean> udpReplayInfoBeans = new ArrayList<>();
                    for (CombinedSideChannel sc : sideChannels) {
                        serverPortsMaps.add(sc.receivePortMappingNonBlock());
                        UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();
                        udpReplayInfoBean.setSenderCount(sc.receiveSenderCount());
                        udpReplayInfoBeans.add(udpReplayInfoBean);
                        Log.i("Replay", "Channel " + sc.getId() + ": Successfully"
                                + " received serverPortsMap and senderCount!");
                    }

                    /*
                     * Step 6: Create TCP clients from CSPairs and UDP clients from client ports.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.create_tcp_client));

                    //map of all cs pairs to TCP clients for a replay
                    ArrayList<HashMap<String, CTCPClient>> CSPairMappings = new ArrayList<>();

                    //create TCP clients
                    for (CombinedSideChannel sc : sideChannels) {
                        HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();
                        for (String csp : appData.getTcpCSPs()) {
                            //get server IP and port
                            String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                                    csp.lastIndexOf("."));
                            String destPort = csp.substring(csp.lastIndexOf('.') + 1);
                            //pad port to 5 digits with 0s; ex. 00443 or 00080
                            destPort = String.format("%5s", destPort).replace(' ', '0');

                            //get the server
                            ServerInstance instance;
                            try {
                                instance = Objects.requireNonNull(Objects.requireNonNull(
                                        serverPortsMaps.get(sc.getId()).get("tcp")).get(destIP)).get(destPort);
                                assert instance != null;
                            } catch (NullPointerException | AssertionError e) {
                                Log.e("Replay", "Channel " + sc.getId() + ": Cannot get instance", e);
                                setInconclusive(getString(R.string.error_no_connection));
                                return false;
                            }
                            if (instance.server.trim().equals(""))
                                // TODO: Use a setter instead probably
                                instance.server = servers.get(sc.getId()); // serverPortsMap.get(destPort);

                            //create the client
                            CTCPClient c = new CTCPClient(csp, instance.server,
                                    Integer.parseInt(instance.port),
                                    appData.getReplayName(), Config.get("publicIP"), false);
                            CSPairMapping.put(csp, c);
                        }
                        CSPairMappings.add(CSPairMapping);
                        Log.i("Replay", "Channel " + sc.getId()
                                + ": created clients from CSPairs");
                        Log.d("Replay", "Size of CSPairMapping is " + CSPairMapping.size());
                    }

                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.create_udp_client));

                    //map of all client ports to UDP clients for a replay
                    ArrayList<HashMap<String, CUDPClient>> udpPortMappings = new ArrayList<>();

                    //create client for each UDP port
                    for (CombinedSideChannel sc : sideChannels) {
                        HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();
                        for (String originalClientPort : appData.getUdpClientPorts()) {
                            CUDPClient c = new CUDPClient(Config.get("publicIP"));
                            udpPortMapping.put(originalClientPort, c);
                        }
                        udpPortMappings.add(udpPortMapping);
                        Log.i("Replay", "Channel " + sc.getId()
                                + ": created clients from udpClientPorts");
                        Log.d("Replay", "Size of udpPortMapping is " + udpPortMapping.size());
                    }

                    if (isCancelled()) {
                        return false;
                    }
                    if (isNetworkUnavailable()) {
                        displayNoNetworkDialogue();
                        return false;
                    }

                    /*
                     * Step 7: Start notifier(s) for UDP.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.run_notf));

                    ArrayList<CombinedNotifierThread> notifiers = new ArrayList<>();
                    ArrayList<Thread> notfThreads = new ArrayList<>();
                    for (CombinedSideChannel sc : sideChannels) {
                        CombinedNotifierThread notifier = sc.notifierCreator(udpReplayInfoBeans.get(sc.getId()));
                        notifiers.add(notifier);
                        Thread notfThread = new Thread(notifier);
                        notfThread.start();
                        notfThreads.add(notfThread);
                    }

                    /*
                     * Step 8: Start receiver(s) to log throughputs on a given interval.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.run_receiver));

                    ArrayList<CombinedAnalyzerTask> analyzerTasks = new ArrayList<>();
                    ArrayList<Timer> analyzerTimers = new ArrayList<>();
                    ArrayList<CombinedReceiverThread> receivers = new ArrayList<>();
                    ArrayList<Thread> rThreads = new ArrayList<>();
                    for (CombinedSideChannel sc : sideChannels) {
                        CombinedAnalyzerTask analyzerTask = new CombinedAnalyzerTask(app.getTime() / 2.0,
                                appData.isTCP(), numOfTimeSlices.get(sc.getId()), runPortTests); //throughput logger
                        Timer analyzerTimer = new Timer(true); //timer to log throughputs on interval
                        analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());
                        analyzerTasks.add(analyzerTask);
                        analyzerTimers.add(analyzerTimer);

                        CombinedReceiverThread receiver = new CombinedReceiverThread(
                                udpReplayInfoBeans.get(sc.getId()), jitterBeans.get(sc.getId()), analyzerTask); //receiver for udp
                        receivers.add(receiver);
                        Thread rThread = new Thread(receiver);
                        rThread.start();
                        rThreads.add(rThread);
                    }

                    /*
                     * Step 8.5?: Start progress bar.
                     */
                    // This thread runs in parallel keeps progressbar up to date
                    int finalIteration = iteration;
                    //TODO: switch to ScheduledThreadExecutor?
                    Thread UIUpdateThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Thread.currentThread().setName("UIUpdateThread (Thread)");
                            if (finalIteration == 1) {
                                clearProgressBar();
                            }
                            while (updateUIBean.getProgress() < 100 && !isCancelled()) {
                                publishProgress("updateUI");
                                try { //update progress bar every 1/2 second
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    Log.w("UpdateUI", "sleeping interrupted!", e);
                                }
                            }
                        }
                    });


                    UIUpdateThread.start(); //yay! progress bar now moving

                    /*
                     * Step 9: Send packets to server(s).
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.run_sender));

                    ArrayList<HashMap<String, HashMap<String, ServerInstance>>> udpServerMappings = new ArrayList<>();
                    for (HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> m : serverPortsMaps) {
                        udpServerMappings.add(m.get("udp"));
                    }

                    CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBeans, analyzerTasks,
                            runPortTests ? Consts.REPLAY_PORT_TIMEOUT : Consts.REPLAY_APP_TIMEOUT);
                    long timeStarted = System.nanoTime(); //start time for sending
                    //send packets
                    queue.run(updateUIBean, types.length, CSPairMappings,
                            udpPortMappings, udpReplayInfoBeans, udpServerMappings,
                            Boolean.valueOf(Config.get("timing")), servers, this);

                    //all packets sent - stop logging and receiving
                    queue.stopTimers();
                    for (Timer t : analyzerTimers) {
                        t.cancel();
                    }
                    for (CombinedNotifierThread n : notifiers) {
                        n.doneSending = true;
                    }
                    for (Thread t : notfThreads) {
                        t.join();
                    }
                    for (CombinedReceiverThread r : receivers) {
                        r.keepRunning = false;
                    }
                    for (Thread t : rThreads) {
                        t.join();
                    }

                    if (iteration == 1) { //make progress bar to 50%
                        publishProgress("finishProgress", "1");
                    } else { //make progress bar to 100%
                        publishProgress("finishProgress", "2");
                        Log.i("UpdateUI", "completed!");
                    }

                    if (isCancelled()) {
                        return false;
                    }
                    if (isNetworkUnavailable()) {
                        displayNoNetworkDialogue();
                        return false;
                    }

                    /*
                     * Step 10: Tell server(s) that replay is finished.
                     */
                    publishProgress("updateStatus", app.getName(), iteration + "/"
                            + types.length + " " + getString(R.string.send_done));

                    //time to send all packets
                    double duration = ((double) (System.nanoTime() - timeStarted)) / 1000000000;
                    for (CombinedSideChannel sc : sideChannels) {
                        sc.sendDone(duration);
                    }
                    Log.d("Replay", "replay(s) finished using time " + duration + " s");

                    /*
                     * Step 11: Send throughputs and slices to server(s).
                     */
                    for (CombinedSideChannel sc : sideChannels) {
                        sc.sendTimeSlices(analyzerTasks.get(sc.getId()).getAverageThroughputsAndSlices());
                    }

                    //set avg of port 443, so it can be displayed if port being tested is blocked
                    if (runPortTests && channel.equalsIgnoreCase("random")) {
                        app.randomThroughput = analyzerTasks.get(0).getAvgThroughput(); //TODO: Multithread display
                    }

                    // TODO find a better way to do this
                    // Send Result;No and wait for OK before moving forward
                    for (CombinedSideChannel sc : sideChannels) {
                        while (sc.getResult(Config.get("result"))) {
                            Thread.sleep(500);
                        }
                    }

                    /*
                     * Step 12: Close side channel(s) and TCP/UDP sockets.
                     */
                    // closing side channel sockets
                    for (CombinedSideChannel sc : sideChannels) {
                        sc.closeSideChannelSocket();
                    }

                    //close TCP sockets
                    for (HashMap<String, CTCPClient> mapping : CSPairMappings) {
                        for (String csp : appData.getTcpCSPs()) {
                            CTCPClient c = mapping.get(csp);
                            if (c != null) {
                                c.close();
                            }
                        }
                    }
                    Log.i("CleanUp", "Closed CSPairs 1");

                    //close UDP sockets
                    for (HashMap<String, CUDPClient> mapping : udpPortMappings) {
                        for (String originalClientPort : appData.getUdpClientPorts()) {
                            CUDPClient c = mapping.get(originalClientPort);
                            if (c != null) {
                                c.close();
                            }
                        }
                    }

                    Log.i("CleanUp", "Closed CSPairs 2");
                    iteration++;
                } catch (InterruptedException e) {
                    Log.w("Replay", "Replay interrupted!", e);
                } catch (IOException e) { //something wrong with receiveKbytes() or constructor in CombinedSideChannel
                    Log.e("Replay", "Some IO issue with server", e);
                    setInconclusive(getString(R.string.error_no_connection));
                    return false;
                }
            }

            /*
             * Step C: Determine if there is differentiation.
             */
            return getResults(portBlocked, isConfirmation);
        }

        /**
         * Determines if there is differentiation. If app is running, result of random test is
         * compared against original test. If port is running, result of port test is compared
         * against port 443.
         * <p>
         * If results are inconclusive or have differentiation, a confirmation test will run if the
         * confirmation setting is switched on.
         * <p>
         * For port tests, Step 1 and Step 2 are skipped if a port is blocked, as no throughputs are
         * sent to the server to analyze. A response is created for Step 3 instead of retrieving
         * from the server. When a port is blocked, the port throughput is 0 Mbps, while port 443
         * (which should not be blocked) has a throughput, which is calculated in Step 11 of
         * runTest().
         * <p>
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
        private boolean getResults(boolean portBlocked, boolean isConfirmation) {
            try {
                if (isCancelled()) {
                    return false;
                }
                if (isNetworkUnavailable()) {
                    displayNoNetworkDialogue();
                    return false;
                }

                int id = 0;
                for (WebSocketConnection w : wsConns) { //check websockets still connected if using MLab
                    Log.d("WebSocket", "WebSocket (id: " + id + ") connectivity check: "
                            + (w.isOpen() ? "CONNECTED" : "CLOSED"));
                    id++;
                }

                ArrayList<JSONObject> analysisResults = new ArrayList<>();
                if (!portBlocked) { //skip Step 1 and step 2 if port blocked
                    /*
                     * Step 1: Ask server to analyze a test.
                     */
                    JSONObject resp;
                    for (String server : analyzerServerUrls) {
                        for (int ask4analysisRetry = 3; ask4analysisRetry > 0; ask4analysisRetry--) {
                            resp = ask4analysis(server, randomID, app.getHistoryCount()); //request analysis
                            if (resp == null) {
                                Log.e("Result Channel", server + ": ask4analysis returned null!");
                            } else {
                                analysisResults.add(resp);
                                break;
                            }
                        }
                    }

                    if (analysisResults.size() != analyzerServerUrls.size()) {
                        setInconclusive(getString(R.string.error_analysis_fail));
                        return false;
                    }

                    boolean success;
                    for (JSONObject result : analysisResults) {
                        success = result.getBoolean("success");
                        if (!success) {
                            Log.e("Result Channel", "ask4analysis failed!");
                            setInconclusive(getString(R.string.error_analysis_fail));
                            return false;
                        }
                    }

                    publishProgress("updateStatus", app.getName(), getString(R.string.waiting));

                    // sanity check
                    if (app.getHistoryCount() < 0) {
                        Log.e("Result Channel", "historyCount value not correct!");
                        return false;
                    }

                    Log.i("Result Channel", "ask4analysis succeeded!");
                    if (isCancelled()) {
                        return false;
                    }
                    if (isNetworkUnavailable()) {
                        displayNoNetworkDialogue();
                        return false;
                    }

                    /*
                     * Step 2: Get results of analysis from server.
                     */
                    analysisResults.clear();
                    for (String url : analyzerServerUrls) {
                        for (int i = 0; ; i++) { //3 attempts to get analysis from sever
                            resp = getSingleResult(url, randomID, app.getHistoryCount()); //get results

                            if (resp == null) {
                                Log.e("Result Channel", url + ": getSingleResult returned null!");
                            } else {
                                success = resp.getBoolean("success");
                                if (success) { //success
                                    if (resp.has("response")) { //success and has response
                                        analysisResults.add(resp);
                                        Log.i("Result Channel", url + ": retrieve result succeeded");
                                        break;
                                    } else { //success but response is missing
                                        Log.w("Result Channel", url + ": Server result not ready");
                                    }
                                } else if (resp.has("error")) {
                                    Log.e("Result Channel", "ERROR: " + url + ": " + resp.getString("error"));
                                } else {
                                    Log.e("Result Channel", "Error: " + url + ": Some error getting results.");
                                }
                            }

                            if (i < 3) { //wait 2 seconds to try again
                                try {
                                    Thread.sleep(2000);
                                } catch (InterruptedException e) {
                                    Log.w("Result Channel", "Sleep interrupted", e);
                                }
                            } else { //error after 3rd attempt
                                if (runPortTests) { //"the port 80 issue"
                                    portBlocked = true;
                                    Log.i("Result Channel", "Can't retrieve result, port blocked");
                                    break;
                                } else {
                                    setInconclusive(getString(R.string.not_all_tcp_sent_text));
                                    return false;
                                }
                            }
                        }
                    }
                }

                /*
                 * Step 3: Parse the analysis results.
                 */
                System.out.println("RESULLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLTS");
                for (JSONObject j : analysisResults) {
                    System.out.println(j);
                }

                String differentiationNetwork = "";
                if (isTomography) {
                    //TODO: tomography api call here
                    Random random = new Random();
                    if (random.nextInt(2) == 1) {
                        differentiationNetwork = carrier;
                    }
                }
                //TODO: put multithread display here
                JSONObject response = portBlocked ? new JSONObject() : analysisResults.get(0).getJSONObject("response");


                if (portBlocked) { //generate results if port blocked
                    response.put("userID", randomID);
                    response.put("historyCount", historyCount);
                    response.put("replayName", app.getDataFile());
                    response.put("area_test", -1);
                    response.put("ks2pVal", -1);
                    response.put("ks2_ratio_test", -1);
                    response.put("xput_avg_original", 0);
                    response.put("xput_avg_test", app.randomThroughput); //calculated in runTest() Step 11
                }

                Log.d("Result Channel", "SERVER RESPONSE: " + response.toString());

                String userID = response.getString("userID");
                int historyCount = response.getInt("historyCount");
                double area_test = response.getDouble("area_test");
                double ks2pVal = response.getDouble("ks2pVal");
                double ks2RatioTest = response.getDouble("ks2_ratio_test");
                double xputOriginal = response.getDouble("xput_avg_original");
                double xputTest = response.getDouble("xput_avg_test");

                // sanity check
                if ((!userID.trim().equalsIgnoreCase(randomID))
                        || (historyCount != app.getHistoryCount())) {
                    Log.e("Result Channel", "Result didn't pass sanity check! "
                            + "correct id: " + randomID
                            + " correct historyCount: " + app.getHistoryCount());
                    Log.e("Result Channel", "Result content: " + response.toString());
                    setInconclusive(getString(R.string.error_result));
                    return false;
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
                    a_threshold = 30;
                }

                double area_test_threshold = (double) a_threshold / 100;
                double ks2pVal_threshold = (double) ks2pvalue_threshold / 100;
                //double ks2RatioTest_threshold = (double) 95 / 100;

                boolean aboveArea = Math.abs(area_test) >= area_test_threshold;
                //boolean trustPValue = ks2RatioTest >= ks2RatioTest_threshold;
                boolean belowP = ks2pVal < ks2pVal_threshold;
                boolean differentiation = false;
                boolean inconclusive = false;

                if (portBlocked) {
                    differentiation = true;
                } else if (aboveArea) {
                    if (belowP) {
                        differentiation = true;
                    } else {
                        inconclusive = true;
                    }
                }

                // TODO uncomment following code when you want differentiation to occur
                //differentiation = true;
                //inconclusive = true;

                /*
                 * Step 5: Save and display results to user. Rerun test if necessary.
                 */
                //determine if the test needs to be rerun
                if ((inconclusive || differentiation) && confirmationReplays && !isConfirmation && !isTomography) {
                    publishProgress("updateStatus", app.getName(), getString(R.string.confirmation_replay));
                    try { //wait 2 seconds so user can read message before it disappears
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Log.w("Result Channel", "sleep interrupted", e);
                    }
                    //runTest();
                    return true; //return so that first result isn't saved
                }

                String displayStatus; //display for the user in their language
                String saveStatus; //save to disk, so it can appear in the correct language in prev results
                if (isTomography) {
                    saveStatus = differentiationNetwork.equals("") ? "tomo failed" : "tomo succ";
                    displayStatus = differentiationNetwork.equals("") ? getString(R.string.tomo_failed)
                            : getString(R.string.tomo_succ);
                } else if (inconclusive) {
                    saveStatus = "inconclusive";
                    displayStatus = getString(R.string.inconclusive);
                    inconclusiveApps.add(app);
                } else if (differentiation) {
                    saveStatus = "has diff";
                    displayStatus = getString(R.string.has_diff);

                    String error = runPortTests ? getString(R.string.test_blocked_port_text) :
                            getString(R.string.test_blocked_app_text);
                    if (!portBlocked) {
                        if (xputOriginal > xputTest) {
                            error = runPortTests ? getString(R.string.test_prioritized_port_text) :
                                    getString(R.string.test_prioritized_app_text);
                        } else {
                            error = runPortTests ? getString(R.string.test_throttled_port_text) :
                                    getString(R.string.test_throttled_app_text);
                        }
                    }
                    app.setError(error);

                    Locale current = getResources().getConfiguration().locale;
                    String country = current.getCountry();
                    if (country.equals("FR")) { //show alert arcep button
                        app.setArcepNeedsAlerting(true);
                    }
                    diffApps.add(app);
                } else {
                    saveStatus = "no diff";
                    displayStatus = getString(R.string.no_diff);
                }

                //for results display on the Run Test page
                app.setStatus(displayStatus);
                app.area_test = area_test;
                app.ks2pVal = ks2pVal;
                app.ks2pRatio = ks2RatioTest;
                app.originalThroughput = xputOriginal;
                app.randomThroughput = xputTest;
                app.setDifferentiationNetwork(differentiationNetwork);

                Log.i("Result Channel", "writing results to json array");
                //for results display on the Previous Results page
                response.put("isPort", runPortTests);
                response.put("appName", app.getName());
                response.put("appImage", app.getImage());
                response.put("status", saveStatus);
                response.put("date", new Date().getTime());
                response.put("areaThreshold", area_test_threshold);
                response.put("ks2pThreshold", ks2pVal_threshold);
                response.put("isIPv6", isIPv6);
                response.put("server", serverDisplay);
                response.put("carrier", carrier);
                if (isTomography) {
                    response.put("tomographyNetwork", differentiationNetwork);
                }
                Log.d("response", response.toString());
                results.put(response); //put response in array to save

                publishProgress("updateStatus", app.getName(), app.getStatus()); //display results to user
            } catch (JSONException e) {
                Log.e("Result Channel", "parsing json error", e);
            }
            return false;
            //todos: put description of tomography at top of reruns
            //fix ui pop up
            //does rerun button pop up after tomo tests?
            //do no diff tests appear during tomo tests?
        }
    }
}
