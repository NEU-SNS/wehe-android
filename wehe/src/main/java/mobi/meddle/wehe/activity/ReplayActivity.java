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
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import java.net.MalformedURLException;
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
import java.util.Timer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import mobi.meddle.wehe.BuildConfig;
import mobi.meddle.wehe.R;
import mobi.meddle.wehe.adapter.ImageReplayRecyclerViewAdapter;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.JitterBean;
import mobi.meddle.wehe.bean.RequestSet;
import mobi.meddle.wehe.bean.ServerInstance;
import mobi.meddle.wehe.bean.SocketInstance;
import mobi.meddle.wehe.bean.UDPReplayInfoBean;
import mobi.meddle.wehe.bean.UpdateUIBean;
import mobi.meddle.wehe.bean.combinedAppJSONInfoBean;
import mobi.meddle.wehe.combined.CTCPClient;
import mobi.meddle.wehe.combined.CUDPClient;
import mobi.meddle.wehe.combined.CombinedAnalyzerTask;
import mobi.meddle.wehe.combined.CombinedNotifierThread;
import mobi.meddle.wehe.combined.CombinedQueue;
import mobi.meddle.wehe.combined.CombinedReceiverThread;
import mobi.meddle.wehe.combined.CombinedSideChannel;
import mobi.meddle.wehe.constant.ReplayConstants;
import mobi.meddle.wehe.util.Config;
import mobi.meddle.wehe.util.RandomString;
import mobi.meddle.wehe.util.UtilsManager;


public class ReplayActivity extends AppCompatActivity {

    public static final String STATUS = "ReplayActPrefsFile";
    public boolean replayOngoing = false;
    boolean networkAvailable = true;
    ArrayList<ApplicationBean> selectedApps = null;
    RecyclerView appsRecyclerView = null;
    RecyclerView.LayoutManager appsRecyclerViewLayoutManager;
    ProgressBar prgBar;
    ImageReplayRecyclerViewAdapter adapter = null;
    Toolbar mToolbar;
    private Context context;
    private String server;
    private String metadataServer;
    private String enableTiming;
    private UpdateUIBean updateUIBean;
    private boolean doTest;
    private String analyzerServerUrl;
    private boolean confirmationReplays;
    private Date date;
    private JSONArray results;
    private String randomID;
    private int a_threshold;
    private int ks2pvalue_threshold;
    private SharedPreferences settings;
    private int historyCount;
    private TraceRunAsync traceRunner;
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;
    private SSLSocketFactory metadataSocketFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replay);
        mToolbar = findViewById(R.id.replay_bar);
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
        if (!isNetworkAvailable()) {
            new AlertDialog.Builder(this,
                    AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                    .setTitle(getString(R.string.network_error))
                    .setMessage(
                            getString(R.string.text_network_error))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int which) {
                                    ReplayActivity.this.finish();
                                }
                            }).show();
            networkAvailable = false;
        }
        selectedApps = getIntent().getParcelableArrayListExtra("selectedApps");
        for (ApplicationBean app : selectedApps) {
            app.status = getString(R.string.pending);
        }

        adapter = new ImageReplayRecyclerViewAdapter(selectedApps, this);
        appsRecyclerView = findViewById(R.id.appsRecyclerView);
        appsRecyclerViewLayoutManager = new LinearLayoutManager(this);
        appsRecyclerView.setLayoutManager(appsRecyclerViewLayoutManager);
        appsRecyclerView.setAdapter(adapter);

        prgBar = findViewById(R.id.prgBar);
        context = getApplicationContext();
        // This is the core of the Application
        traceRunner = new TraceRunAsync();
        traceRunner.execute("");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (replayOngoing) {
            ReplayActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    if (traceRunner != null) {
                        traceRunner.cancel(true);
                    }
                    Toast.makeText(ReplayActivity.this, "Replays aborted",
                            Toast.LENGTH_LONG).show();
                }

            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private String getPublicIP(String port) {
        String publicIP = "127.0.0.1";

        if (server != null && !server.equals("127.0.0.1")) {

            String url = "http://" + server + ":" + port + "/WHATSMYIPMAN";
            Log.d("getPublicIP", "url: " + url);

            while (publicIP.equals("127.0.0.1")) {
                try {
                    URL u = new URL(url);
                    HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                    conn.setConnectTimeout(3000);
                    conn.setReadTimeout(5000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream()));
                    StringBuilder buffer = new StringBuilder();
                    String input;

                    while ((input = in.readLine()) != null) {
                        buffer.append(input);
                    }
                    in.close();
                    conn.disconnect();
                    publicIP = buffer.toString();
                    InetAddress address = InetAddress.getByName(publicIP);
                    if (!(address instanceof Inet4Address) && !(address instanceof Inet6Address)) {
                        Log.e("getPublicIP", "wrong format of public IP: "
                                + publicIP);
                        throw new UnknownHostException();
                    } else
                        Log.d("getPublicIP", "public IP: " + publicIP);

                } catch (UnknownHostException e) {
                    Log.w("getPublicIP", "failed to get public IP!");
                    e.printStackTrace();
                    publicIP = "127.0.0.1";
                    break;
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            Log.w("getPublicIP", "server ip is not available: " + server);
        }
        return publicIP;
    }

    /**
     * This Method checks the network Availability. For this NetworkInfo class
     * is used and this should also provide type of connectivity i.e. Wi-Fi,
     * Cellular ..
     *
     * @return
     */
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void showFinishDialog() {
        ReplayActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                new AlertDialog.Builder(ReplayActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(R.string.replay_finished_title)
                        .setMessage(getString(R.string.replay_finished))
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        // do nothing
                                    }
                                }).show();
                getSupportActionBar().setTitle(R.string.test_results);
            }

        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!replayOngoing) {
                ReplayActivity.this.finish();
                ReplayActivity.this.overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);

                return super.onKeyDown(keyCode, event);
            } else {
                new AlertDialog.Builder(ReplayActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                        .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                        .setPositiveButton(getString(android.R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        ReplayActivity.this.finish();
                                        ReplayActivity.this
                                                .overridePendingTransition(
                                                        android.R.anim.slide_in_left,
                                                        android.R.anim.slide_out_right);
                                    }
                                })
                        .setNegativeButton(getString(android.R.string.no),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        // do nothing
                                    }
                                }).show();

                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (!replayOngoing) {
                    ReplayActivity.this.finish();
                    ReplayActivity.this.overridePendingTransition(
                            android.R.anim.slide_in_left,
                            android.R.anim.slide_out_right);
                } else {
                    new AlertDialog.Builder(ReplayActivity.this,
                            AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                            .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                            .setPositiveButton(getString(android.R.string.yes),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            ReplayActivity.this.finish();
                                            ReplayActivity.this
                                                    .overridePendingTransition(
                                                            android.R.anim.slide_in_left,
                                                            android.R.anim.slide_out_right);
                                        }
                                    })
                            .setNegativeButton(getString(android.R.string.no),
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            // do nothing
                                        }
                                    }).show();

                    return true;
                }
                break;
        }
        return true;
    }

    private void updateUI() {
        this.runOnUiThread(new Runnable() {
            public void run() {
                adapter.notifyDataSetChanged();
                Log.d("Result Channel", "updated UI");
            }

        });
    }

    // TODO abtract it out to a separate class for better OOD
    // To fully understand this part refer to the API documentation and research paper by Fangfan Li
    // This class implements the test procedure in a serial fashion i.e. step by step
    private class TraceRunAsync extends AsyncTask<String, String, Void> {
        // TODO switch to better data structure, beans are not suitable for android
        private combinedAppJSONInfoBean appData;
        private ApplicationBean app;
        private long timeStarted;
        private boolean rerun = false;

        // this method handles all UI updates, running on main thread
        protected void onProgressUpdate(String... values) {
            if (values[0].equalsIgnoreCase("updateStatus")) {
                app.status = values[1];
                adapter.notifyDataSetChanged();
            } else if (values[0].equalsIgnoreCase("updateUI")) {
                if (prgBar.getVisibility() == View.GONE)
                    prgBar.setVisibility(View.VISIBLE);
                prgBar.setProgress(updateUIBean.getProgress());
            } else if (values[0].equalsIgnoreCase("finishProgress")) {
                prgBar.setProgress(0);
                prgBar.setVisibility(View.GONE);
            } else if (values[0].equalsIgnoreCase("makeToast")) {
                Toast.makeText(ReplayActivity.this, values[1],
                        Toast.LENGTH_LONG).show();
            } else if (values[0].equalsIgnoreCase("makeDialog")) {
                new AlertDialog.Builder(ReplayActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle("Error")
                        .setMessage(values[1])
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        traceRunner.cancel(true);
                                        ReplayActivity.this.finish();
                                    }
                                }).show();
            } else
                Log.e("onProgressUpdate", "unknown instruction!");
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
            Log.d("Testing", "Forced exit");
        }

        @Override
        protected Void doInBackground(String... args) {
            if (isCancelled()) {
                return null;
            }
            // TODO subscribe to network events instead of using this
            if (!networkAvailable) {
                Toast.makeText(
                        context,
                        getString(R.string.toast_nonetwork),
                        Toast.LENGTH_LONG).show();
            } else {
                // TODO remove this but with caution
                updateUIBean = new UpdateUIBean();
                // TODO to test different configs change the files poiting by CONFIG_FILE
                //  avoid changing the code directly
                Config.readConfigFile(ReplayConstants.CONFIG_FILE, context);
                SharedPreferences sharedPrefs =
                        PreferenceManager.getDefaultSharedPreferences(context);
                try {
                    // metadata here is user's network type device used geolocation if permitted etc
                    // Google storage forbids to store user related data
                    // So we send that data to a private server
                    server = sharedPrefs.getString("pref_server", "wehe2.meddle.mobi");
                    metadataServer = "wehe-metadata.meddle.mobi";
                    // We first resolve the IP of the server and then communicate with the server
                    // Using IP only, because we have multiple server under same domain and we want
                    // the client not to switch server during a test run
                    // TODO come up with a better way to handle Inet related queries, since this
                    //  introduced inefficiency
                    final InetAddress[] address = {null, null};
                    new Thread() {
                        public void run() {
                            while (!(address[0] instanceof Inet4Address || address[0] instanceof Inet6Address)) {
                                try {
                                    server = InetAddress.getByName(server).getHostAddress();
                                    address[0] = InetAddress.getByName(server);
                                } catch (UnknownHostException e) {
                                    Log.w("GetReplayServerIP", "get IP of replay server failed!");
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.start();

                    new Thread() {
                        public void run() {
                            while (!(address[1] instanceof Inet4Address || address[1] instanceof Inet6Address)) {
                                try {
                                    metadataServer = InetAddress.getByName(metadataServer).getHostAddress();
                                    address[1] = InetAddress.getByName(metadataServer);
                                } catch (UnknownHostException e) {
                                    Log.w("GetReplayServerIP", "get IP of replay server failed!");
                                    e.printStackTrace();
                                }
                            }
                        }
                    }.start();

                    int maxWaitTime = 5000;
                    int currentWaitTime = 500;
                    while (!(address[0] instanceof Inet4Address || address[0] instanceof Inet6Address)
                            && !(address[1] instanceof Inet4Address || address[1] instanceof Inet6Address)) {
                        try {
                            if (currentWaitTime <= maxWaitTime) {
                                Thread.sleep(currentWaitTime);
                                currentWaitTime += 500;
                            } else {
                                Toast.makeText(context, R.string.server_unavailable,
                                        Toast.LENGTH_LONG).show();
                                return null;
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    if (address[0] instanceof Inet6Address)
                        server = "[" + server + "]";
                    if (address[1] instanceof Inet6Address)
                        metadataServer = "[" + metadataServer + "]";

                    try {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        Certificate ca;
                        try (InputStream caInput = getResources().openRawResource(R.raw.main)) {
                            ca = cf.generateCertificate(caInput);
                            System.out.println("main=" + ((X509Certificate) ca).getIssuerDN());
                        }

                        // Create a KeyStore containing our trusted CAs
                        String keyStoreType = KeyStore.getDefaultType();
                        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                        keyStore.load(null, null);
                        keyStore.setCertificateEntry("main", ca);

                        // Create a TrustManager that trusts the CAs in our KeyStore
                        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                        tmf.init(keyStore);

                        // Create an SSLContext that uses our TrustManager
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, tmf.getTrustManagers(), null);
                        sslSocketFactory = context.getSocketFactory();
                        hostnameVerifier = (hostname, session) -> true;
                    } catch (CertificateException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (KeyStoreException e) {
                        e.printStackTrace();
                    } catch (KeyManagementException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    try {
                        CertificateFactory cf = CertificateFactory.getInstance("X.509");
                        Certificate ca;
                        try (InputStream caInput = getResources().openRawResource(R.raw.metadata)) {
                            ca = cf.generateCertificate(caInput);
                            System.out.println("metadata=" + ((X509Certificate) ca).getIssuerDN());
                        }

                        // Create a KeyStore containing our trusted CAs
                        String keyStoreType = KeyStore.getDefaultType();
                        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
                        keyStore.load(null, null);
                        keyStore.setCertificateEntry("metadata", ca);

                        // Create a TrustManager that trusts the CAs in our KeyStore
                        String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
                        tmf.init(keyStore);

                        // Create an SSLContext that uses our TrustManager
                        SSLContext context = SSLContext.getInstance("TLS");
                        context.init(null, tmf.getTrustManagers(), null);
                        metadataSocketFactory = context.getSocketFactory();
                    } catch (CertificateException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (KeyStoreException e) {
                        e.printStackTrace();
                    } catch (KeyManagementException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Log.d("GetReplayServerIP", "Server IP: " + server);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    Log.w("GetReplayServerIP", "Invalid IP address!");
                }

                // Extract data that was sent by previous activity. In our case, list of
                // apps, server and timing
                enableTiming = "true";
                doTest = false;
                int port = Integer.valueOf(Config.get("result_port"));
                analyzerServerUrl = ("https://" + server + ":" + port + "/Results");
                date = new Date();

                results = new JSONArray();
                Log.d("Result Channel",
                        "path: " + server + " port: " + port);

                confirmationReplays = sharedPrefs.getBoolean("confirmationReplays", true);
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

                a_threshold = Integer.parseInt(Objects.requireNonNull(sharedPrefs.getString("pref_threshold_area", "10")));
                ks2pvalue_threshold = Integer.parseInt(Objects.requireNonNull(sharedPrefs.getString("pref_threshold_ks2p", "5")));
                confirmationReplays = sharedPrefs.getBoolean("pref_multiple_tests", true);

                // to get historyCount
                settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);

                // generate or retrieve an historyCount for this phone
                boolean hasHistoryCount = settings.getBoolean("hasHistoryCount", false);
                if (!hasHistoryCount) {
                    historyCount = 0;
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("hasHistoryCount", true);
                    editor.putInt("historyCount", historyCount);
                    editor.apply();
                } else {
                    historyCount = settings.getInt("historyCount", -1);
                }
                // check if retrieve historyCount succeeded
                if (historyCount == -1)
                    throw new RuntimeException();

                Config.set("timing", enableTiming);

                // make sure server is initialized!
                while (server == null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Config.set("server", server);
                Config.set("jitter", "true");
                Config.set("publicIP", "");
                new Thread(new Runnable() {
                    public void run() {
                        Config.set("publicIP", getPublicIP("80"));
                    }

                }).start();
                // Find a way to switch to no wait
                while (Config.get("publicIP").equals("")) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("Replay", "public IP: " + Config.get("publicIP"));
            }
            for (ApplicationBean app : selectedApps) {
                rerun = false;
                // Set the app to run test for
                this.app = app;
                // Run the test on this.app
                runTest();
            }
            // Keep checking if the user exited from ReplayActivity or not
            // TODO find a better way stop the tests immediately without continues checking
            if (isCancelled()) {
                return null;
            }
            if (results.length() > 0) {
                Log.d("Result Channel", "Storing results");
                saveResults();
            }
            if (isCancelled()) {
                return null;
            }
            showFinishDialog();
            Log.d("Result Channel", "Exiting normally");
            return null;
        }

        private JSONObject ask4analysis(String id, int historyCount) {
            HashMap<String, String> pairs = new HashMap<>();

            pairs.put("command", "analyze");
            pairs.put("userID", id);
            pairs.put("historyCount", String.valueOf(historyCount));
            pairs.put("testID", "1");

            return sendRequest("POST", null, pairs);
        }

        private JSONObject getSingleResult(String id, int historyCount) {
            ArrayList<String> data = new ArrayList<>();
            data.add("userID=" + id);
            data.add("command=" + "singleResult");
            data.add("historyCount=" + historyCount);
            data.add("testID=1");

            return sendRequest("GET", data, null);

        }

        //This function is used to send get or post request to the server
        private JSONObject sendRequest(@NonNull String method, ArrayList<String> data,
                                       HashMap<String, String> pairs) {

            JSONObject json = null;
            if (method.equalsIgnoreCase("GET")) {
                String dataURL = URLEncoder(data);
                String url_string = analyzerServerUrl + "?" + dataURL;
                Log.d("Result Channel", url_string);

                try {
                    URL u = new URL(url_string);
                    HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
                    conn.setHostnameVerifier(hostnameVerifier);
                    conn.setSSLSocketFactory(sslSocketFactory);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream()));
                    StringBuilder buffer = new StringBuilder();
                    String input;

                    // parse BufferReader rd to StringBuilder res
                    while ((input = in.readLine()) != null) {
                        buffer.append(input);
                    }

                    in.close();
                    conn.disconnect();

                    // parse String to json file.
                    json = new JSONObject(buffer.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("Result Channel", "sendRequest GET failed");
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("Result Channel", "JSON Parse failed");
                }
            } else if (method.equalsIgnoreCase("POST")) {
                String url_string = analyzerServerUrl;
                Log.d("Result Channel", url_string);

                try {
                    URL u = new URL(url_string);
                    HttpsURLConnection conn = (HttpsURLConnection) u.openConnection();
                    conn.setHostnameVerifier(hostnameVerifier);
                    conn.setSSLSocketFactory(sslSocketFactory);
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestMethod("POST");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(os, StandardCharsets.UTF_8));
                    writer.write(paramsToPostData(pairs));

                    writer.flush();
                    writer.close();
                    os.close();

                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(
                                    conn.getInputStream()));
                    StringBuilder buffer = new StringBuilder();
                    String input;

                    // parse BufferReader rd to StringBuilder res
                    while ((input = in.readLine()) != null) {
                        buffer.append(input);
                    }
                    in.close();
                    conn.disconnect();
                    // parse String to json file.
                    json = new JSONObject(buffer.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("Result Channel", "convert string to json failed");
                    json = null;
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("Result Channel", "sendRequest POST failed");
                    json = null;
                }
            }
            return json;
        }

        // Save results of the current tests to SharedPreference
        private void saveResults() {
            DateFormat dateFormat = new SimpleDateFormat(
                    "yyyy/MM/dd HH:mm:ss", Locale.US);
            String strDate = dateFormat.format(date);
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
                if (it.hasNext())
                    resultsWithDate.remove(it.next());
                else
                    Log.w("Result Channel",
                            "iterator doesn't have next but length is not 0");
            }

            try {
                resultsWithDate.put(strDate, results);
            } catch (JSONException e) {
                Log.d("saveResults", "Error saving results, " + e);
                return;
            }

            SharedPreferences.Editor editor = settings.edit();
            editor.putString("lastResult",
                    resultsWithDate.toString());
            editor.apply();
        }

        private String paramsToPostData(HashMap<String, String> params) {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (first)
                    first = false;
                else
                    result.append("&");

                try {
                    result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
                    result.append("=");
                    result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            return result.toString();
        }

        // overload URLencoder to encode map to an url.
        private String URLEncoder(ArrayList<String> map) {
            StringBuilder data = new StringBuilder();
            for (String s : map) {
                if (data.length() > 0) {
                    data.append("&");
                }
                data.append(s);
            }
            return data.toString();
        }
        // This function reads the pickles files and loads them into memory as bean
        combinedAppJSONInfoBean unpickleJSON(String filename,
                                             Context context) {
            AssetManager assetManager;
            InputStream inputStream;
            combinedAppJSONInfoBean appData = new combinedAppJSONInfoBean();
            ArrayList<RequestSet> Q = new ArrayList<>();
            try {
                assetManager = context.getAssets();
                inputStream = assetManager.open(filename);
                int size = inputStream.available();
                byte[] buffer = new byte[size];
                inputStream.read(buffer);
                inputStream.close();

                String jsonStr = new String(buffer, StandardCharsets.UTF_8);

                JSONArray json = new JSONArray(jsonStr);

                JSONArray qArray = (JSONArray) json.get(0);
                for (int i = 0; i < qArray.length(); i++) {
                    RequestSet tempRS = new RequestSet();
                    JSONObject dictionary = qArray.getJSONObject(i);
                    tempRS.setc_s_pair((String) dictionary.get("c_s_pair"));
                    tempRS.setPayload(UtilsManager.hexStringToByteArray((String) dictionary.get("payload")));
                    tempRS.setTimestamp((Double) dictionary.get("timestamp"));

                    if (dictionary.has("response_len"))
                        tempRS.setResponse_len((Integer) dictionary.get("response_len"));

                    if (dictionary.has("response_hash"))
                        tempRS.setResponse_hash(dictionary.get("response_hash").toString());

                    if (dictionary.has("end"))
                        tempRS.setEnd((Boolean) dictionary.get("end"));

                    Q.add(tempRS);
                }

                appData.setQ(Q);

                JSONArray portArray = (JSONArray) json.get(1);
                ArrayList<String> portStrArray = new ArrayList<>();
                for (int i = 0; i < portArray.length(); i++)
                    portStrArray.add(portArray.getString(i));
                appData.setUdpClientPorts(portStrArray);


                JSONArray csArray = (JSONArray) json.get(2);
                ArrayList<String> csStrArray = new ArrayList<>();
                for (int i = 0; i < csArray.length(); i++)
                    csStrArray.add((String) csArray.get(i));

                appData.setTcpCSPs(csStrArray);
                appData.setReplayName((String) json.get(3));

            } catch (JSONException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return appData;
        }

        private void runTest() {
            String[] types;
            // Flip a coin to decide which type to run first
            if (Math.random() < 0.5) {
                types = new String[]{"open", "random"};
            } else {
                types = new String[]{"random", "open"};
            }

            int iteration = 1;
            for (String channel : types) {
                if (isCancelled()) {
                    return;
                }
                // Based on the type selected load open or random trace of given application
                if (channel.equalsIgnoreCase("open")) {
                    this.appData = unpickleJSON(app.getDataFile(), context);
                } else if (channel.equalsIgnoreCase("random")) {
                    this.appData = unpickleJSON(app.getRandomDataFile(), context);
                } else
                    Log.w("replayIndex", "replay name error: " + channel);

                HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();
                HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();

                try {
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getResources()
                                    .getString(R.string.create_side_channel));
                    int sideChannelPort = Integer.valueOf(Config
                            .get("combined_sidechannel_port"));

                    // This random ID is used to map the test results to a specific intsance of app
                    // It is generated only once and saved thereafter
                    if (randomID == null) {
                        Log.d("RecordReplay", "randomID does not exist!");
                        return;
                    }

                    Log.d("Server", server + " metadata " + metadataServer);
                    // This side channel is used to communicate with the server in bytes mode and to
                    // run traces, it send tcp and udp packets and receives the same from the server
                    CombinedSideChannel sideChannel = new CombinedSideChannel(sslSocketFactory,
                            server, sideChannelPort, randomID);

                    HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> serverPortsMap = null;
                    UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();

                    JitterBean jitterBean = new JitterBean();
                    // increase history count only once during the run of a single app
                    if (iteration == 1) {
                        // First update historyCount
                        historyCount += 1;
                        // Then write current historyCount to applicationBean
                        app.historyCount = historyCount;
                        settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putInt("historyCount", historyCount);
                        editor.apply();
                        Log.d("Replay",
                                "historyCount: " + historyCount);
                    }

                    // initialize endOfTest value
                    boolean endOfTest = false;
                    if (channel.equalsIgnoreCase(types[1])) {
                        Log.w("Replay", "last replay running " + types[1] + "!");
                        endOfTest = true;
                    }

                    if (doTest)
                        Log.w("Replay", "include -Test string");

                    String replayPort = "80";
                    String ipThroughProxy = "127.0.0.1";
                    if (!appData.isUDP()) {
                        for (String csp : appData.getTcpCSPs()) {
                            replayPort = csp.substring(csp.lastIndexOf('.') + 1);
                        }
                        ipThroughProxy = getPublicIP(replayPort);
                    }

                    // This is group of values that is used to track traces on server
                    // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
                    sideChannel.declareID(appData.getReplayName(),
                            // for indicating end of test
                            endOfTest ? "True" : "False",

                            channel.equalsIgnoreCase("random") ? "1" : "0",
                            // add a tail for testing data
                            doTest ? Config.get("extraString") + "-Test" : Config
                                    .get("extraString"), String
                                    .valueOf(historyCount), ipThroughProxy, BuildConfig.VERSION_NAME);

                    // This tuple tells the server if the server should operate on pachets of traces
                    // and if so which packets to process
                    sideChannel.sendChangeSpec(-1, "null", "null");

                    app.status = getResources().getString(
                            R.string.ask4permission);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.ask4permission));
                    if (isCancelled()) {
                        return;
                    }
                    // Now to move forward we ask for server permission
                    String[] permission = sideChannel.ask4Permission();
                    String status = permission[0].trim();

                    Log.d("Replay", "permission[0]: " + status
                            + " permission[1]: " + permission[1]);

                    String permissionError = permission[1].trim();
                    String customError;
                    if (status.equals("0")) {
                        // These are the different errors that server can report
                        switch (permissionError) {

                            case "1":
                                customError = getString(R.string.error_unknown_replay);
                                break;

                            case "2":
                                customError = getString(R.string.error_IP_connected);
                                break;

                            case "3":
                                customError = getString(R.string.error_low_resources);
                                break;

                            default:
                                customError = getString(R.string.error_unknown);
                                break;
                        }
                        app.status = customError;
                        updateUI();
                        publishProgress("makeToast", customError);
                        return;
                    }

                    int numOfTimeSlices = Integer.parseInt(permission[2].trim(), 10);
                    // always send noIperf here
                    sideChannel.sendIperf();
                    if (isCancelled()) {
                        return;
                    }
                    // send device info
                    // testId is how server knows if the trace ran was open or random
                    // testId greater than one represents DPI rules analysis
                    int testId = 1;
                    if (channel.equals("open"))
                        testId = 0;

                    sideChannel.sendMobileStats(metadataSocketFactory, metadataServer, sideChannelPort,
                            Config.get("sendMobileStats"), getApplicationContext(),
                            randomID, historyCount, testId);

                    /*
                     * Ask for port mapping from server. For some reason, port map
                     * info parsing was throwing error. so, I put while loop to do
                     * this until port mapping is parsed successfully.
                     */
                    app.status = getResources().getString(
                            R.string.receive_server_port_mapping);
                    publishProgress(
                            "updateStatus", iteration + "/" + types.length + " " +
                                    getString(
                                            R.string.receive_server_port_mapping));

                    serverPortsMap = sideChannel.receivePortMappingNonBlock();
                    udpReplayInfoBean.setSenderCount(sideChannel
                            .receiveSenderCount());
                    Log.d("Replay",
                            "Successfully received serverPortsMap and senderCount!");

                    /*
                     * Create clients from CSPairs
                     */
                    app.status = getResources().getString(
                            R.string.create_tcp_client);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.create_tcp_client));

                    for (String csp : appData.getTcpCSPs()) {
                        String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                                csp.lastIndexOf("."));
                        String destPort = csp.substring(csp.lastIndexOf('.') + 1);


                        ServerInstance instance = serverPortsMap.get("tcp")
                                .get(destIP).get(destPort);
                        if (instance.server.trim().equals(""))
                            // Use a setter instead probably
                            instance.server = server; // serverPortsMap.get(destPort);

                        CTCPClient c = new CTCPClient(csp, instance.server,
                                Integer.valueOf(instance.port), randomID,
                                appData.getReplayName(), Config.get("publicIP"),
                                /*Boolean.valueOf(Config.get("addHeader"))*/false);
                        CSPairMapping.put(csp, c);
                    }
                    Log.d("Replay", "created clients from CSPairs");

                    app.status = getResources().getString(
                            R.string.create_udp_client);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.create_udp_client));

                    for (String originalClientPort : appData.getUdpClientPorts()) {
                        CUDPClient c = new CUDPClient(Config.get("publicIP"));
                        udpPortMapping.put(originalClientPort, c);
                    }

                    Log.d("Replay", "created clients from udpClientPorts");

                    Log.d("Replay",
                            "Size of CSPairMapping is "
                                    + CSPairMapping.size());
                    Log.d("Replay",
                            "Size of udpPortMapping is "
                                    + udpPortMapping.size());

                    app.status = getResources().getString(
                            R.string.run_notf);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.run_notf));

                    CombinedNotifierThread notifier = sideChannel
                            .notifierCreater(udpReplayInfoBean);
                    Thread notfThread = new Thread(notifier);
                    notfThread.start();

                    app.status = getResources().getString(
                            R.string.run_receiver);
                    CombinedAnalyzerTask analyzerTask;
                    if (channel.equals("random")) {
                        Log.d("Intervalcalc", "Time is " + app.getRandomTime() + " and slices are " + numOfTimeSlices);
                        analyzerTask = new CombinedAnalyzerTask(app.getRandomTime(), numOfTimeSlices);
                    } else {
                        Log.d("Intervalcalc", "Time is " + app.getTime() + " and slices are " + numOfTimeSlices);
                        analyzerTask = new CombinedAnalyzerTask(app.getTime(), numOfTimeSlices);
                    }
                    Timer analyzerTimer = new Timer(true);
                    analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());
                    //resultChannelThread.gotError = false;
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.run_receiver));

                    CombinedReceiverThread receiver = new CombinedReceiverThread(
                            udpReplayInfoBean, jitterBean, analyzerTask);
                    Thread rThread = new Thread(receiver);
                    rThread.start();
                    // This thread runs in parallel keeps progressbar up to date
                    Thread UIUpdateThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            updateUIBean.setProgress(0);
                            Thread.currentThread().setName(
                                    "UIUpdateThread (Thread)");
                            while (updateUIBean.getProgress() < 100) {
                                publishProgress("updateUI");
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    Log.d("UpdateUI", "sleeping interrupted!");
                                }
                            }
                            // make progress bar to be 100%
                            publishProgress("updateUI");
                            Log.d("UpdateUI", "completed!");
                        }
                    });

                    UIUpdateThread.start();
                    if (isCancelled()) {
                        return;
                    }

                    app.status = getResources().getString(
                            R.string.run_sender);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.run_sender));

                    CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBean, analyzerTask);
                    this.timeStarted = System.nanoTime();
                    queue.run(updateUIBean, iteration, types.length, CSPairMapping, udpPortMapping,
                            udpReplayInfoBean, serverPortsMap.get("udp"),
                            Boolean.valueOf(Config.get("timing")), server);

                    if (isCancelled()) {
                        return;
                    }

                    analyzerTimer.cancel();
                    notifier.doneSending = true;
                    notfThread.join();
                    receiver.keepRunning = false;
                    rThread.join();

                    // Telling server done with replaying
                    double duration = ((double) (System.nanoTime() - this.timeStarted)) / 1000000000;

                    app.status = getResources().getString(
                            R.string.send_done);
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            getString(R.string.send_done));

                    Log.d("Replay", "replay finished using time " + duration + " s");

                    String message;
                    // Check if an Abort occured
                    if (queue.ABORT) {
                        Log.w("Replay", "replay aborted!");
                        Log.w("Replay", queue.abort_reason);
                        message = getString(R.string.simple_error);
                        if (message.equals("error_proxy"))
                            message = getString(R.string.error_proxy);
                    } else {
                        message = getString(R.string.finish_random);
                    }

                    app.status = message;
                    publishProgress("updateStatus", iteration + "/" + types.length + " " +
                            message);

                    if (CSPairMapping.size() < udpPortMapping.size()) { // Check for UDP
                        // Wait for DONE from server before going forward // // UDP ONLY
                        while (!sideChannel.getResult()) {
                            Thread.sleep(500);
                        }
                    }

                    sideChannel.sendDone(duration);
                    // Send slices of throughput samples to server
                    sideChannel.sendTimeSlices(analyzerTask.getAverageThroughputsAndSlices());

                    // Get OK from server to make sure that slices are received
                    // TODO find a better way to do this and the next step as well
                    while (!sideChannel.getResult()) {
                        Thread.sleep(500);
                    }
                    // Send Result;No and wait for OK before moving forward
                    while (!sideChannel.getResult(Config.get("result"))) {
                        Thread.sleep(500);
                    }
                    // closing side channel socket
                    sideChannel.closeSideChannelSocket();

                    for (String csp : appData.getTcpCSPs()) {

                        CTCPClient c = CSPairMapping.get(csp);
                        c.close();
                    }
                    Log.d("CleanUp", "Closed CSPairs 1");

                    for (String originalClientPort : appData.getUdpClientPorts()) {
                        CUDPClient c = udpPortMapping.get(originalClientPort);
                        c.close();
                    }

                    Log.d("CleanUp", "Closed CSPairs 2");
                    iteration++;
                } catch (InterruptedException ex) {
                    Log.w("Replay", "Replay interrupted!");
                }
            }
            // set progress bar to invisible
            publishProgress("finishProgress");
            getResults();
        }

        private void getResults() {
            try {
                if (isCancelled()) {
                    return;
                }
                String wait = context.getString(R.string.waiting);
                int ask4analysisRetry = 5;
                JSONObject result = null;
                for (; ask4analysisRetry > 0; ask4analysisRetry--) {
                    result = ask4analysis(randomID, app.historyCount);
                    if (result == null)
                        Log.d("Result Channel", "ask4analysis returned null!");
                    else
                        break;
                }

                if (result == null) {
                    app.status = getString(R.string.unavailable_replay_server);
                    updateUI();
                    return;
                }

                boolean success = result.getBoolean("success");

                if (!success) {
                    Log.d("Result Channel", "ask4analysis failed!");
                    app.status = getString(R.string.error_result);
                    updateUI();
                    return;
                }

                app.status = wait;
                updateUI();

                // sanity check
                if (app.historyCount < 0) {
                    Log.e("Result Channel",
                            "historyCount value not correct!");
                    return;
                }

                Log.d("Result Channel", "ask4analysis succeeded!");
                if (isCancelled()) {
                    return;
                }
                result = getSingleResult(randomID, app.historyCount);

                if (result == null) {
                    Log.d("Result Channel",
                            "getSingleResult returned null!");
                    app.status = getString(R.string.unavailable_replay_server);
                    updateUI();
                }

                success = result != null && result.getBoolean("success");

                if (success) {
                    Log.d("Result Channel", "retrieve result succeed");

                    // parse content of response
                    if (!result.has("response")) {
                        // if client cannot get result after 5 attempts,
                        // give up
                        // and display another message
                        Log.w("Result Channel",
                                "Server result not ready");
                        app.status = getString(R.string.unavailable_replay_server);
                        updateUI();
                    }

                    //counter -= 1;
                    JSONObject response = result.getJSONObject("response");

                    Log.d("Result Channel",
                            "response: " + response.toString());

                    String userID = response.getString("userID");
                    int historyCount = response.getInt("historyCount");
                    String replayName = response.getString("replayName");
                    String date = response.getString("date");
                    Double area_test = response.getDouble("area_test");
                    Double ks2pVal = response.getDouble("ks2pVal");
                    Double ks2RatioTest = response.getDouble("ks2_ratio_test");
                    Double xputOriginal = response.getDouble("xput_avg_original");
                    Double xputTest = response.getDouble("xput_avg_test");

                    Log.d("Result Channel",
                            "userID: " + userID
                                    + " historyCount: "
                                    + historyCount
                                    + " replayName: " + replayName
                                    + " date: " + date);

                    // sanity check
                    if ((!userID.trim().equalsIgnoreCase(randomID)) || (historyCount != app.historyCount)) {
                        Log.e("Result Channel",
                                "Result didn't pass sanity check! correct id: "
                                        + randomID
                                        + " correct historyCount: "
                                        + app.historyCount);
                        Log.e("Result Channel", "Result content: "
                                + response.toString());
                        app.status = getString(R.string.error_result);
                    } else {


                        double area_test_threshold = (double) a_threshold / 100;
                        double ks2pVal_threshhold = (double) ks2pvalue_threshold / 100;
                        double ks2RatioTest_threshold = (double) 95 / 100;

                        boolean aboveArea = area_test >= area_test_threshold;
                        boolean trustPValue = ks2RatioTest >= ks2RatioTest_threshold;
                        boolean belowP = ks2pVal < ks2pVal_threshhold;
                        boolean differentiation = false;
                        boolean inconclusive = false;

                        if (!trustPValue) {
                            differentiation = aboveArea;
                        } else {
                            if (aboveArea && belowP) {
                                differentiation = true;
                            }

                            if (aboveArea ^ belowP) {
                                inconclusive = true;
                            }
                        }

                        // TODO uncomment following code when you want differentiation to occur
                        //differentiation = true;

                        if (inconclusive) {
                            app.status = context.getResources()
                                    .getString(R.string.inconclusive);
                        } else if (differentiation) {
                            app.status = context.getResources()
                                    .getString(R.string.has_diff);
                        } else {
                            app.status = context.getResources()
                                    .getString(R.string.no_diff);
                        }

                        app.area_test = area_test;
                        app.ks2pVal = ks2pVal;
                        app.ks2pRatio = ks2RatioTest;
                        app.xputOriginal = xputOriginal;
                        app.xputTest = xputTest;

                        if ((inconclusive || differentiation)
                                && confirmationReplays) {
                            app.status = context.getResources().getString(R.string.confirmation_replay);
                            if (!rerun) {
                                rerun = true;
                                runTest();
                            } else {
                                app.status = context.getResources().getString(R.string.has_diff);
                            }
                        }

                        // put new result into array list
                        Log.d("Result Channel",
                                "put result to json array");

                        response.put("status", app.status.split(",")[0]);
                        response.put("appName", app.name);
                        response.put("areaThreshold", area_test_threshold);
                        response.put("ks2pThreshold", ks2pVal_threshhold);
                        response.put("server", server);
                        Log.d("response", response.toString());
                        results.put(response);
                    }
                    updateUI();
                } else {
                    Log.w("Result Channel", "Error: Some Error");
                    app.status = getString(R.string.unavailable_replay_server);
                    updateUI();
                }
            } catch (JSONException e) {
                Log.d("Result Channel", "parsing json error");
                e.printStackTrace();
            }
        }
    }
}