package mobi.meddle.wehe.ui.replay;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Objects;
import mobi.meddle.wehe.R;
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter;
import mobi.meddle.wehe.data.bean.ApplicationBean;

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
                app.setAlertFCC(false);
                app.setStatus(getString(R.string.pending));
            }
            inconclusiveApps.clear();
            diffApps.clear();
            traceRunner = new TraceRunAsync(ReplayActivity.this);
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
                                app.setAlertFCC(false);
                                app.setStatus(getString(R.string.pending));
                            }
                            traceRunner = new TraceRunAsync(ReplayActivity.this);
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
            traceRunner = new TraceRunAsync(this);
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
    public boolean isNetworkUnavailable() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // Check if connectivityManager is not null
        if (connectivityManager != null) {
            // Get the active network
            Network activeNetwork = connectivityManager.getActiveNetwork();
            // If there is no active network, the network is unavailable
            if (activeNetwork == null) {
                return true;
            }
            // Get network capabilities and check for connectivity
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
            // Check if the network is connected to Wi-Fi or mobile data
            if (networkCapabilities != null) {
                // Return true if the network is connected to the internet (either Wi-Fi or mobile data)
                return !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
        }
        // If the connectivityManager is null, consider the network unavailable
        return true;
    }
    /**
     * Display this popup message if there is no network
     */
    public void displayNoNetworkDialogue() {
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
    public void replayStop() {
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

    // Methods for managing replayOngoing state
    void setReplayOngoing(boolean replayOngoing) {
        this.replayOngoing = replayOngoing;
    }

    boolean isReplayOngoing() {
        return replayOngoing;
    }

    // Add app to different lists based on result
    void addToDiffApps(ApplicationBean app) {
        diffApps.add(app);
    }

    void addToInconclusiveApps(ApplicationBean app) {
        inconclusiveApps.add(app);
    }

    // Access to UI elements and app data
    ProgressBar getProgressBar() {
        return prgBar;
    }

    Context getActivityContext() {
        return this;
    }

    ImageReplayRecyclerViewAdapter getAdapter() {
        return adapter;
    }

    ArrayList<ApplicationBean> getSelectedApps() {
        return selectedApps;
    }

    boolean isTomography() {
        return isTomography;
    }

    String getCarrier() {
        return carrier;
    }

    boolean getRunPortTests() {
        return runPortTests;
    }

    ArrayList<ApplicationBean> getDiffApps() {
        return diffApps;
    }

    ArrayList<ApplicationBean> getInconclusiveApps() {
        return inconclusiveApps;
    }

    void displayRerunButtons() {
        displayRerunTomoButtons();
    }
}