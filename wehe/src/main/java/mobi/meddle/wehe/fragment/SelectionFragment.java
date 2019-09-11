package mobi.meddle.wehe.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;
import mobi.meddle.wehe.adapter.ImageCheckBoxListAdapter;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.constant.ReplayConstants;

public class SelectionFragment extends Fragment {

    public static final String TAG = "SelectionFragment";
    public HashMap<String, ApplicationBean> apps = new HashMap<>();
    SharedPreferences history;
    RecyclerView appList;
    Button nextButton;
    Context context;
    View view;

    private ImageCheckBoxListAdapter adapter;

    OnClickListener nextButtonOnClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (adapter.selectedApps.size() == 0) {
                Toast.makeText(context,
                        getString(R.string.select_at_least_one),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = new Intent(context, ReplayActivity.class);
            intent.putParcelableArrayListExtra("selectedApps", adapter.selectedApps);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right,
                    R.anim.slide_out_left);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            context = getContext();

            // This method parses JSON file which contains details for different
            // Applications
            // and returns HashMap of ApplicationBean type
            apps = parseAppJSON();

            if (Locale.getDefault().getDisplayLanguage().equals("français")) {
                apps.remove("NBCSports");
                apps.remove("NBCSports_random");
            }

            // Main screen checkbox Adapter. This is populated from HashMap
            // retrieved from above method
            adapter = new ImageCheckBoxListAdapter(apps, context);

            history = context.getSharedPreferences(ReplayActivity.STATUS,
                    Context.MODE_PRIVATE);

            // Display a warning if the user is on wifi
            ConnectivityManager connectivityManager = (ConnectivityManager) this.context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            TelephonyManager telephonyManager = (TelephonyManager) this.context
                    .getSystemService(Context.TELEPHONY_SERVICE);

            if (connectivityManager == null) {
                return;
            }

            NetworkInfo networkInfo = connectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if (networkInfo != null
                    && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                String carrierName;
                if (telephonyManager != null) {
                    carrierName = telephonyManager.getNetworkOperatorName();
                } else {
                    carrierName = "your carrier";
                }
                showWifiToast(carrierName);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {

        NavigationView navigationView = Objects.requireNonNull(getActivity()).findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_run);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        getActivity().setTitle(menuItem.getTitle());
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_selection, parent, false);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        nextButton = view.findViewById(R.id.nextButton);
        nextButton.setOnClickListener(nextButtonOnClick);
        appList = view.findViewById(R.id.appsRecyclerView);
        appList.setLayoutManager(new LinearLayoutManager(context));
        appList.setAdapter(adapter);
    }

    public void showWifiToast(String carrier) {
        CharSequence text = String.format(getString(R.string.wifiWarning), carrier);
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    /**
     * This method parses applist json file located in assets folder. This file has all the basic details of apps for replay.
     *
     * @return HashMap
     */
    public HashMap<String, ApplicationBean> parseAppJSON() {
        HashMap<String, ApplicationBean> hashMap = new HashMap<>();
        BufferedReader in = null;
        try {
            StringBuilder buf = new StringBuilder();
            InputStream json = context.getAssets().open(ReplayConstants.APPS_FILENAME);
            in = new BufferedReader(new InputStreamReader(json));
            String str;

            while ((str = in.readLine()) != null) {
                buf.append(str);
            }

            in.close();

            JSONObject jObject = new JSONObject(buf.toString());
            JSONArray jArray = jObject.getJSONArray("apps");

            JSONObject appObj;
            ApplicationBean bean;
            for (int i = 0; i < jArray.length(); i++) {
                appObj = jArray.getJSONObject(i);
                bean = new ApplicationBean();

                bean.setName(appObj.getString("name"));
                bean.setDataFile(appObj.getString("datafile"));
                bean.setSize(appObj.getDouble("size"));
                bean.setTime(appObj.getDouble("time"));
                bean.setImage(appObj.getString("image"));
                bean.setRandomDataFile(appObj.getString("randomdatafile"));
                bean.setRandomSize(appObj.getDouble("randomsize"));
                bean.setRandomTime(appObj.getDouble("randomtime"));
                // all beans are selected by default
                bean.setSelected(true);
                hashMap.put(bean.getName(), bean);
            }
        } catch (IOException ex) {
            Log.d(ReplayConstants.LOG_APPNAME, "IOException while reading file " + ReplayConstants.APPS_FILENAME);
            ex.printStackTrace();
        } catch (JSONException ex) {
            Log.d(ReplayConstants.LOG_APPNAME, "JSONException while parsing JSON file " + ReplayConstants.APPS_FILENAME);
            ex.printStackTrace();
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
        }
        return hashMap;
    }
}
