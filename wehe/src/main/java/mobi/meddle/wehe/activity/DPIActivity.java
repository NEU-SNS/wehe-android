package mobi.meddle.wehe.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
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
import java.net.ConnectException;
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
import java.util.ArrayList;
import java.util.HashMap;
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

public class DPIActivity extends AppCompatActivity {

    public static final String STATUS = "DPIActPrefsFile";

    private ImageView appIcon;
    private TextView appName;
    private TextView appSize;
    private TextView appTime;
    private TextView xputOriginalTitle;
    private TextView xputOriginal;
    private TextView xputTestTitle;
    private TextView xputTest;
    private TextView diffStatus;
    private TextView dpiAnalysis;
    private Toolbar mToolbar;
    private ProgressBar prgBar;
    private TraceAnalyzer traceAnalyzer;
    private boolean replayOngoing;
    private Button startButton;
    private Context context;
    private String metadataServer;
    private String server;
    private UpdateUIBean updateUIBean;
    private String enableTiming;
    private boolean doTest;
    private String analyzerServerUrl;
    private String randomID = null;
    private int a_threshold;
    private int ks2pvalue_threshold;
    private SharedPreferences settings;
    private ApplicationBean app;
    private TextView resultTextView;
    private SSLSocketFactory sslSocketFactory;
    private HostnameVerifier hostnameVerifier;
    private SSLSocketFactory metadataSocketFactory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getApplicationContext();
        setContentView(R.layout.activity_dpi);
        mToolbar = findViewById(R.id.dpi_bar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.dpi_page_title));
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        startButton = findViewById(R.id.start_button);
        app = getIntent().getParcelableExtra("app");
        appIcon = findViewById(R.id.app_icon);
        appIcon.setImageDrawable(getResources().getDrawable(
                getResources().getIdentifier(app.getImage(),
                        "drawable", getPackageName())));

        appName = findViewById(R.id.app_name_textview);
        appName.setText(app.name);
        appSize = findViewById(R.id.app_size_textview);
        appSize.setText(String.format(Locale.getDefault(), "Size: %.1f", app.getSize()));
        appTime = findViewById(R.id.app_time_textview);
        appTime.setText(String.format(Locale.getDefault(), "Time: %.1f", app.getTime()));
        xputOriginalTitle = findViewById(R.id.xputOriginalTitle);
        xputOriginalTitle.setText(getString(R.string.xputOriginal, app.name));
        xputTestTitle = findViewById(R.id.xputTestTitle);
        xputTestTitle.setText(getString(R.string.xputTest, app.name));
        xputOriginal = findViewById(R.id.xputOriginalValue);
        xputOriginal.setText(String.format(Locale.getDefault(), "%.1f Mb/s", app.xputOriginal));
        xputTest = findViewById(R.id.xputTestValue);
        xputTest.setText(String.format(Locale.getDefault(), "%.1f Mb/s", app.xputTest));
        diffStatus = findViewById(R.id.diff_status_tv);
        String color = (app.xputOriginal > app.xputTest) ? getString(R.string.color_green) : getString(R.string.color_red);
        diffStatus.setTextColor(Color.parseColor(color));
        diffStatus.setText(getString(R.string.has_diff));
        dpiAnalysis = findViewById(R.id.resultTextView);
        prgBar = findViewById(R.id.progress);
        prgBar.setVisibility(View.GONE);
    }

    private void updateUI(final boolean diff) {
        this.runOnUiThread(new Runnable() {
            public void run() {
                if (diff) {
                    String color = (app.xputOriginal > app.xputTest) ? getString(R.string.color_green) : getString(R.string.color_red);
                    diffStatus.setTextColor(Color.parseColor(color));
                    diffStatus.setText(getString(R.string.has_diff));
                } else {
                    diffStatus.setTextColor(Color.parseColor(getString(R.string.color_yellow)));
                    diffStatus.setText(getString(R.string.no_diff));
                }
                xputOriginal.setText(String.format(Locale.getDefault(), "%.1f Mb/s", app.xputOriginal));
                xputTest.setText(String.format(Locale.getDefault(), "%.1f Mb/s", app.xputTest));
            }
        });
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

    public void runTest(View view) {

        traceAnalyzer = new TraceAnalyzer();
        traceAnalyzer.execute("");
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

    private void showFinishDialog(final String dpIrule) {
        DPIActivity.this.runOnUiThread(new Runnable() {
            public void run() {
                dpiAnalysis.setText(dpIrule);
                new AlertDialog.Builder(DPIActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(R.string.dpi_analysis_finished_title)
                        .setMessage(dpIrule)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(
                                            DialogInterface dialog,
                                            int which) {
                                        // do nothing
                                    }
                                }).show();
                resultTextView = findViewById(R.id.resultTextView);
                resultTextView.setText(dpIrule);
            }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (!replayOngoing) {
                DPIActivity.this.finish();
                DPIActivity.this.overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);
            } else {
                new AlertDialog.Builder(DPIActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                        .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                        .setPositiveButton(getString(android.R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        traceAnalyzer.cancel(true);
                                        DPIActivity.this.finish();
                                        DPIActivity.this
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
                    DPIActivity.this.finish();
                    DPIActivity.this.overridePendingTransition(
                            android.R.anim.slide_in_left,
                            android.R.anim.slide_out_right);
                } else {
                    new AlertDialog.Builder(DPIActivity.this,
                            AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setTitle(getString(R.string.interrupt_ongoing_replay_title))
                            .setMessage(getString(R.string.interrupt_ongoing_replay_text))
                            .setPositiveButton(getString(android.R.string.yes),
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog,
                                                            int which) {
                                            traceAnalyzer.cancel(true);
                                            DPIActivity.this.finish();
                                            DPIActivity.this
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

    // TODO read the API doc in README to understand this task first
    // This is a step by step implementation of the DPI API
    private class TraceAnalyzer extends AsyncTask<String, String, Void> {
        private combinedAppJSONInfoBean appData;
        private long timeStarted;
        private int testID = 1;
        private String carrierName = CombinedSideChannel.getCarrierName(context);
        private int testedLeft = -1;
        private int testedRight = -1;
        private int packetNumber;
        private boolean doServerInvert = false;

        // this method handles all UI updates, running on main thread
        protected void onProgressUpdate(String... values) {
            if (values[0].equalsIgnoreCase("updateStatus")) {
                app.status = values[1];
                //adapter.notifyDataSetChanged();
            } else if (values[0].equalsIgnoreCase("updateUI")) {
                if (prgBar.getVisibility() == View.GONE)
                    prgBar.setVisibility(View.VISIBLE);
                prgBar.setProgress(updateUIBean.getProgress());
            } else if (values[0].equalsIgnoreCase("finishProgress")) {
                prgBar.setProgress(0);
                prgBar.setVisibility(View.GONE);
            } else if (values[0].equalsIgnoreCase("makeToast")) {
                Toast.makeText(DPIActivity.this, values[1],
                        Toast.LENGTH_LONG).show();
            } else if (values[0].equalsIgnoreCase("makeDialog")) {
                new AlertDialog.Builder(DPIActivity.this,
                        AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                        .setTitle("Error")
                        .setMessage(values[1])
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        traceAnalyzer.cancel(true);
                                        DPIActivity.this.finish();
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
            if (!isNetworkAvailable()) {
                Toast.makeText(
                        context,
                        getString(R.string.toast_nonetwork),
                        Toast.LENGTH_LONG).show();
            } else {
                updateUIBean = new UpdateUIBean();
                Config.readConfigFile(ReplayConstants.CONFIG_FILE, context);
                SharedPreferences sharedPrefs =
                        PreferenceManager.getDefaultSharedPreferences(context);
                try {
                    server = sharedPrefs.getString("pref_server", "wehe2.meddle.mobi");
                    metadataServer = "wehe-metadata.meddle.mobi";
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

                    // Load CAs from an InputStream
                    // (could be from a resource or ByteArrayInputStream or ...)
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

                Log.d("Result Channel",
                        "path: " + server + " port: " + port);

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

                // to get historyCount
                settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);

                // generate or retrieve an historyCount for this phone
                boolean hasHistoryCount = settings.getBoolean("hasHistoryCount", false);
                if (!hasHistoryCount) {
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putBoolean("hasHistoryCount", true);
                    editor.putInt("historyCount", app.historyCount);
                    editor.apply();
                }

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

                // adrian: added cause arash's code
                Config.set("jitter", "true");
                Config.set("publicIP", "");
                new Thread(new Runnable() {
                    public void run() {
                        Config.set("publicIP", getPublicIP("80"));
                    }

                }).start();

                while (Config.get("publicIP").equals("")) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                Log.d("Replay", "public IP: " + Config.get("publicIP"));
            }
            runOnUiThread(new Runnable() {
                public void run() {
                    startButton.setVisibility(View.GONE);
                }
            });
            appData = unpickleJSON(app.getDataFile(), context);
            int ask4DPIanalysisRetry = 5;
            JSONObject resultDPI = null;
            for (; ask4DPIanalysisRetry > 0; ask4DPIanalysisRetry--) {
                resultDPI = ask4DPIanalysis(randomID, carrierName, true);
                if (resultDPI == null)
                    Log.d("Result Channel", "ask4analysis returned null!");
                else
                    break;
            }
            try {
                if (resultDPI == null) {
                    app.status = getString(R.string.unavailable_replay_server);
                    //updateUI();
                    return null;
                }

                boolean success = resultDPI.getBoolean("success");

                if (!success) {
                    Log.d("Result Channel", "ask4analysis failed!");
                    app.status = getString(R.string.error_result);
                    //updateUI();
                    return null;
                }

                app.status = getString(R.string.waiting);
                //updateUI();

                // sanity check
                if (app.historyCount < 0) {
                    Log.e("Result Channel",
                            "historyCount value not correct!");
                    return null;
                }

                Log.d("Result Channel", "ask4analysis succeeded!");
                if (isCancelled()) {
                    return null;
                }


                Log.d("Result Channel", "retrieve result succeed");

                // parse content of response
                if (!resultDPI.has("response")) {
                    // if client cannot get result after 5 attempts,
                    // give up
                    // and display another message
                    Log.w("Result Channel",
                            "Server result not ready");
                    app.status = getString(R.string.unavailable_replay_server);
                    //updateUI();
                }

                //counter -= 1;
                JSONObject response = resultDPI.getJSONObject("response");

                Log.d("Result Channel",
                        "response: " + response.toString());

                try {
                    Toast.makeText(context, "Found DPI rule=" + response.getString("DPIrule"), Toast.LENGTH_LONG).show();
                } catch (JSONException e) {
                    Log.d("WAITING", "For the result!");
                }

                testedLeft = response.getInt("testRegionLeft");
                testedRight = response.getInt("testRegionRight");
                String isClient = response.getString("testPacket").split("_")[0];
                packetNumber = Integer.parseInt(response.getString("testPacket").split("_")[1]);
                doServerInvert = false;
                if (isClient.equals("S")) {
                    doServerInvert = true;
                } else
                    bitInvert();
                testID++;
            } catch (JSONException e) {
                e.printStackTrace();
            }
            runTest();

            if (isCancelled()) {
                return null;
            }
            Log.d("Result Channel", "Exiting normally");
            return null;
        }

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
                    //tempRS.setPayload(DecodeHex.decodeHex(((String)dictionary.get("payload")).toCharArray()));
                    tempRS.setPayload(UtilsManager.hexStringToByteArray((String) dictionary.get("payload")));
                    tempRS.setTimestamp((Double) dictionary.get("timestamp"));
                    // Log.d("Time", (i+1) + " " +
                    // String.valueOf(tempRS.getTimestamp()));

                    // adrian: for tcp
                    if (dictionary.has("response_len"))
                        tempRS.setResponse_len((Integer) dictionary.get("response_len"));
				/*else
					tempRS.setResponse_len(-1);*/

                    if (dictionary.has("response_hash"))
                        tempRS.setResponse_hash(dictionary.get("response_hash").toString());

                    // adrian: for udp
                    if (dictionary.has("end"))
                        tempRS.setEnd((Boolean) dictionary.get("end"));

                    Q.add(tempRS);
                }
                appData.setQ(Q);

                // adrian: store udpClientPorts
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
            if (isCancelled()) {
                return;
            }

            HashMap<String, CTCPClient> CSPairMapping = new HashMap<>();
            HashMap<String, CUDPClient> udpPortMapping = new HashMap<>();

            try {

                publishProgress("updateStatus", getResources()
                        .getString(R.string.create_side_channel));

                int sideChannelPort = Integer.valueOf(Config
                        .get("combined_sidechannel_port"));

                // String randomID = new RandomString(10).nextString();
                if (randomID == null) {
                    Log.d("RecordReplay", "randomID does not exist!");
                    return;
                }

                SocketInstance socketInstance = new SocketInstance(server,
                        sideChannelPort, null);
                Log.d("Server", server);

                CombinedSideChannel sideChannel = new CombinedSideChannel(sslSocketFactory,
                        server, sideChannelPort, randomID);
                // adrian: new format of serverPortsMap
                HashMap<String, HashMap<String, HashMap<String, ServerInstance>>> serverPortsMap = null;
                UDPReplayInfoBean udpReplayInfoBean = new UDPReplayInfoBean();

                // adrian: for recording jitter and payload
                JitterBean jitterBean = new JitterBean();

                settings = getSharedPreferences(STATUS, Context.MODE_PRIVATE);

                if (doTest)
                    Log.w("Replay", "include -Test string");

                String replayPort = "80";
                String ipThroughProxy = "127.0.0.1";
                if (!appData.isUDP()) {
                    for (String csp : appData.getTcpCSPs()) {
                        replayPort = csp.substring(csp.lastIndexOf('.') + 1);
                    }
                    ipThroughProxy = getPublicIP(replayPort);
                } //else {
//					for (String originalClientPort : appData.getUdpClientPorts()) {
//						replayPort = originalClientPort;
//					}
//					getUDPIP(replayPort);
                //}

                // Youtube;False;0;DiffDetector;0;129.10.9.93;1.0
                sideChannel.declareID(appData.getReplayName(),
                        // for indicating end of test
                        "True",

                        "" + testID,
                        // add a tail for testing data
                        doTest ? Config.get("extraString") + "-Test" : Config
                                .get("extraString"), String
                                .valueOf(app.historyCount), ipThroughProxy, BuildConfig.VERSION_NAME);

                if (doServerInvert) {
                    sideChannel.sendChangeSpec(packetNumber, "\"replacel\"", "[" + (testedLeft) + "," + (testedRight) + "]");
                } else {
                    sideChannel.sendChangeSpec(-1, "null", "null");
                }

                // adrian: update progress
                app.status = getResources().getString(
                        R.string.ask4permission);
                publishProgress("updateStatus", getString(R.string.ask4permission));
                if (isCancelled()) {
                    return;
                }

                String[] permission = sideChannel.ask4Permission();
                String status = permission[0].trim();

                Log.d("Replay", "permission[0]: " + status
                        + " permission[1]: " + permission[1]);

                String permissionError = permission[1].trim();
                String customError;
                if (status.equals("0")) {
                    //resultChannelThread.gotError = true;
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
                    //updateUI();
                    throw new ConnectException();
                }

                int numOfTimeSlices = Integer.parseInt(permission[2].trim(), 10);


                // always send noIperf here
                sideChannel.sendIperf();
                if (isCancelled()) {
                    return;
                }
                // send device info and get carrierName
                sideChannel.sendMobileStats(metadataSocketFactory, metadataServer, sideChannelPort,
                        Config.get("sendMobileStats"), getApplicationContext(),
                        randomID, app.historyCount, testID);

                /*
                 * Ask for port mapping from server. For some reason, port map
                 * info parsing was throwing error. so, I put while loop to do
                 * this until port mapping is parsed successfully.
                 */
                // adrian: update progress
                app.status = getResources().getString(
                        R.string.receive_server_port_mapping);
                publishProgress(
                        "updateStatus", getString(
                                R.string.receive_server_port_mapping));

                // randomID = new RandomString(10).nextString();
                serverPortsMap = sideChannel.receivePortMappingNonBlock();
                udpReplayInfoBean.setSenderCount(sideChannel
                        .receiveSenderCount());
                Log.d("Replay",
                        "Successfully received serverPortsMap and senderCount!");

                /*
                 * Create clients from CSPairs
                 */

                // adrian: update progress
                app.status = getResources().getString(
                        R.string.create_tcp_client);
                publishProgress("updateStatus", getString(R.string.create_tcp_client));

                for (String csp : appData.getTcpCSPs()) {
                    String destIP = csp.substring(csp.lastIndexOf('-') + 1,
                            csp.lastIndexOf("."));
                    String destPort = csp.substring(csp.lastIndexOf('.') + 1);


                    ServerInstance instance = serverPortsMap.get("tcp")
                            .get(destIP).get(destPort);
                    if (instance.server.trim().equals(""))
                        instance.server = server; // serverPortsMap.get(destPort);
                    // adrian: pass two more parameters: randomID and replayName
                    // compared with python client
                    CTCPClient c = new CTCPClient(csp, instance.server,
                            Integer.valueOf(instance.port), randomID,
                            appData.getReplayName(), Config.get("publicIP"),
                            /*Boolean.valueOf(Config.get("addHeader"))*/false);
                    CSPairMapping.put(csp, c);
                }
                Log.d("Replay", "created clients from CSPairs");

                /*
                 * adrian: create clients from udpClientPorts
                 */

                // adrian: update progress
                app.status = getResources().getString(
                        R.string.create_udp_client);
                publishProgress("updateStatus", getString(R.string.create_udp_client));

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
                publishProgress("updateStatus", getString(R.string.run_notf));

                CombinedNotifierThread notifier = sideChannel
                        .notifierCreater(udpReplayInfoBean);
                Thread notfThread = new Thread(notifier);
                notfThread.start();

                app.status = getResources().getString(
                        R.string.run_receiver);
                CombinedAnalyzerTask analyzerTask;
                Log.d("Intervalcalc", "Time is " + app.getTime() + " and slices are " + numOfTimeSlices);
                analyzerTask = new CombinedAnalyzerTask(app.getTime(), numOfTimeSlices);
                Timer analyzerTimer = new Timer(true);
                analyzerTimer.scheduleAtFixedRate(analyzerTask, 0, analyzerTask.getInterval());
                //resultChannelThread.gotError = false;
                publishProgress("updateStatus", getString(R.string.run_receiver));

                CombinedReceiverThread receiver = new CombinedReceiverThread(
                        udpReplayInfoBean, jitterBean, analyzerTask);
                Thread rThread = new Thread(receiver);
                rThread.start();

                Thread UIUpdateThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        //prgBar.setProgress(0);
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
                // adrian: update progress
                app.status = getResources().getString(
                        R.string.run_sender);

                publishProgress("updateStatus", getString(R.string.run_sender));

                CombinedQueue queue = new CombinedQueue(appData.getQ(), jitterBean, analyzerTask);
                this.timeStarted = System.nanoTime();
                queue.run(updateUIBean, 1, 1, CSPairMapping, udpPortMapping,
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

                // adrian: update progress
                app.status = getResources().getString(
                        R.string.send_done);
                publishProgress("updateStatus", getString(R.string.send_done));

                sideChannel.sendDone(duration);

                Log.d("Replay", "replay finished using time " + duration + " s");

                String message;
                if (queue.ABORT) {
                    Log.w("Replay", "replay aborted!");
                    Log.w("Replay", queue.abort_reason);
                    //resultChannelThread.gotError = true;
                    message = getString(R.string.simple_error);
                    if (message.equals("error_proxy"))
                        message = getString(R.string.error_proxy);
                } else {
                    message = getString(R.string.finish_random);
                }

                app.status = message;
                publishProgress("updateStatus", message);

                sideChannel.sendTimeSlices(analyzerTask.getAverageThroughputsAndSlices());

                // Getting result
                while (!sideChannel.getResult(Config.get("result"))) {
                    Thread.sleep(500);
                }

                // closing side channel socket
                sideChannel.closeSideChannelSocket();

                for (String csp : appData.getTcpCSPs()) {
                    CTCPClient c = CSPairMapping.get(csp);
                    c.close();
                }
                Log.d("CleanUp", "Closed CSPairs");

                for (String originalClientPort : appData.getUdpClientPorts()) {
                    CUDPClient c = udpPortMapping.get(originalClientPort);
                    c.close();
                }

                Log.d("CleanUp", "Closed CSPairs");
            } catch (ConnectException ce) {
                Log.w("Replay", "Server unavailable!");
                ce.printStackTrace();

                publishProgress("makeToast", getString(R.string.server_unavailable));
            } catch (InterruptedException ex) {
                Log.w("Replay", "Replay interrupted!");
            }
            // set progress bar to invisible
            publishProgress("finishProgress");
            getResults();
        }

        private JSONObject get4dpi(String id, String carrierName, String replayName) {
            ArrayList<String> data = new ArrayList<>();
            data.add("command=" + "DPIrule");
            data.add("userID=" + id);
            data.add("carrierName=" + carrierName);
            data.add("replayName=" + replayName);

            return sendRequest("GET", data, null);
        }

        private JSONObject ask4analysis(String id) {
            HashMap<String, String> pairs = new HashMap<>();

            pairs.put("command", "analyze");
            pairs.put("userID", id);
            pairs.put("historyCount", String.valueOf(app.historyCount));
            pairs.put("testID", "" + testID);

            return sendRequest("POST", null, pairs);
        }

        private JSONObject ask4DPIReset(String id, String carrierName, boolean diff) {
            ArrayList<String> data = new ArrayList<>();
            data.add("command=DPIreset");
            data.add("userID=" + id);
            data.add("carrierName=" + carrierName);
            data.add("replayName=" + appData.getReplayName());
            return sendRequest("GET", data, null);
        }

        private JSONObject ask4DPIanalysis(String id, String carrierName, boolean diff) {
            ArrayList<String> data = new ArrayList<>();
            data.add("command=DPIanalysis");
            data.add("userID=" + id);
            data.add("carrierName=" + carrierName);
            data.add("replayName=" + appData.getReplayName());
            data.add("historyCount=" + app.historyCount);
            data.add("testID=" + testID);
            data.add("testedLeft=" + testedLeft);
            data.add("testedRight=" + testedRight);
            if (diff)
                data.add("diff=T");
            else
                data.add("diff=F");
            return sendRequest("GET", data, null);
        }

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
                    conn.setSSLSocketFactory(sslSocketFactory);
                    conn.setHostnameVerifier(hostnameVerifier);
                    conn.setConnectTimeout(80000);
                    conn.setReadTimeout(80000);

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
                    conn.setSSLSocketFactory(sslSocketFactory);
                    conn.setHostnameVerifier(hostnameVerifier);
                    conn.setConnectTimeout(50000);
                    conn.setReadTimeout(50000);
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

        private void getResults() {
            boolean differentiation = false;
            String wait = getString(R.string.waiting);
            try {
                int ask4analysisRetry = 5;
                JSONObject result = null;
                for (; ask4analysisRetry > 0; ask4analysisRetry--) {
                    result = ask4analysis(randomID);
                    if (result == null)
                        Log.d("Result Channel", "ask4analysis returned null!");
                    else
                        break;
                }

                if (result == null) {
                    app.status = getString(R.string.unavailable_replay_server);
                    return;
                }

                boolean success = result.getBoolean("success");

                if (!success) {
                    Log.d("Result Channel", "ask4analysis failed!");
                    app.status = getString(R.string.error_result);
                    return;
                }

                app.status = wait;

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

                for (int i = 0; i < 5; i++) {
                    result = getSingleResult(randomID, app.historyCount);
                    if (result == null)
                        Log.d("Result Channel", "getSingle returned null!");
                    else
                        break;
                }

                if (result == null) {
                    Log.d("Result Channel",
                            "getSingleResult returned null!");
                    app.status = getString(R.string.unavailable_replay_server);
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
                    }

                    JSONObject response = result.getJSONObject("response");

                    Log.d("Result Channel",
                            "response: " + response.toString());

                    String userID = response.getString("userID");
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
                                    + app.historyCount
                                    + " replayName: " + replayName
                                    + " date: " + date);

                    // sanity check
                    if ((!userID.trim().equalsIgnoreCase(randomID))) {
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
                        differentiation = false;
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

                    }
                }

                int ask4DPIanalysisRetry = 5;
                JSONObject resultDPI = null;
                for (; ask4DPIanalysisRetry > 0; ask4DPIanalysisRetry--) {
                    resultDPI = ask4DPIanalysis(randomID, carrierName, differentiation);
                    if (resultDPI == null)
                        Log.d("Result Channel", "ask4DPIanalysis returned null!");
                    else
                        break;
                }

                if (resultDPI == null) {
                    app.status = getString(R.string.unavailable_replay_server);
                    //updateUI();
                    return;
                }

                success = resultDPI.getBoolean("success");

                if (!success) {
                    Log.d("Result Channel", "ask4DPIanalysis failed!");
                    app.status = getString(R.string.error_result);
                    //updateUI();
                    return;
                }

                app.status = wait;
                //updateUI();

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


                Log.d("Result Channel", "retrieve result succeed");

                // parse content of response
                if (!resultDPI.has("response")) {
                    // if client cannot get result after 5 attempts,
                    // give up
                    // and display another message
                    Log.w("Result Channel",
                            "Server result not ready");
                    app.status = getString(R.string.unavailable_replay_server);
                    //updateUI();
                }

                //counter -= 1;
                JSONObject response = resultDPI.getJSONObject("response");

                Log.d("Result Channel",
                        "response: " + response.toString());

                try {
                    showFinishDialog(response.getString("DPIrule").replace("[", "").replace("]", "").replace("\"", ""));
                    return;
                } catch (JSONException e) {
                    Log.d("WAITING", "For the result!");
                }

                testedLeft = response.getInt("testRegionLeft");
                testedRight = response.getInt("testRegionRight");
                String isClient = response.getString("testPacket").split("_")[0];
                packetNumber = Integer.parseInt(response.getString("testPacket").split("_")[1]);
                doServerInvert = false;
                if (isClient.equals("S"))
                    doServerInvert = true;
                else
                    bitInvert();
                testID++;
                updateUI(differentiation);
                runTest();
            } catch (JSONException e) {
                Log.d("Result Channel", "parsing json error");
                e.printStackTrace();
            }
        }

        private JSONObject getSingleResult(String id, int historyCount) {
            ArrayList<String> data = new ArrayList<>();
            data.add("userID=" + id);
            data.add("command=" + "singleResult");
            data.add("historyCount=" + historyCount);
            data.add("testID=" + testID);

            return sendRequest("GET", data, null);
        }

        private void bitInvert() {
            if (testedLeft == -1 || testedRight == -1)
                return;
            appData = unpickleJSON(app.getDataFile(), context);
            RequestSet RS = appData.getQ().get(packetNumber - 1);
            byte[] payload = RS.getPayload();
            for (int l = testedLeft - 1; l < testedRight; l++) {
                payload[l] = (byte) (~payload[l] & 0xff);
            }

            RS.setPayload(payload);
            appData.getQ().set(packetNumber - 1, RS);
        }
    }
}