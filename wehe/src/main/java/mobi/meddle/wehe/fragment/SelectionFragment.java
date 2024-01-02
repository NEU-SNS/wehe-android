package mobi.meddle.wehe.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;
import mobi.meddle.wehe.adapter.ImageCheckBoxListAdapter;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.ApplicationBean.Category;
import mobi.meddle.wehe.constant.Consts;

/**
 * Fragment that allows user to choose the apps/ports to run.
 * Run Tests and Run Port Tests items in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_selection.xml
 * adapter.ImageCheckBoxListAdapter.java for layout of each app/port
 */
public class SelectionFragment extends Fragment {
    public static final String TAG = "SelectionFragment";
    private ArrayList<ApplicationBean> apps; //all the apps/ports to display on the page
    private Context context;
    private View view;
    private ImageCheckBoxListAdapter adapter; //the window to place the apps/ports in for user selection
    private boolean runPortTests;
    private String carrierDisplay; //cell carrier or "Wi-Fi"
    //If user switches between tabs, like the video and music tab, the selected apps are saved. 3
    //random apps will not be chosen when the user goes back to a tab they visited; instead, the
    //apps selected before the user left will be selected. HashMap contains map of Category to
    //list of apps selected from that Category
    private final HashMap<String, ArrayList<ApplicationBean>> prevSelectedApps = new HashMap<>();
    private Category currentCat;

    //button to run tests after user selects apps/ports
    private final OnClickListener nextButtonOnClick = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (adapter.getSelectedApps().size() == 0) {
                Toast.makeText(context, getString(R.string.select_at_least_one), Toast.LENGTH_LONG)
                        .show();
                return;
            }
            Intent intent = new Intent(context, ReplayActivity.class);
            intent.putParcelableArrayListExtra("selectedApps", adapter.getSelectedApps());
            intent.putExtra("runPortTests", runPortTests);
            intent.putExtra("carrier", carrierDisplay);
            startActivity(intent);
            requireActivity().overridePendingTransition(R.anim.slide_in_right,
                    R.anim.slide_out_left);
        }
    };

    //listener for tab buttons to go to different apps/ports
    private final OnClickListener changeTab = new OnClickListener() {
        @Override
        public void onClick(@NonNull View v) {


            Category cat = runPortTests ? Category.SMALL_PORT : Category.VIDEO;
            int buttonId = runPortTests ? R.id.smallPortButton : R.id.videoButton;
            if (v.getId() == R.id.musicButton) {
                cat = Category.MUSIC;
                buttonId = R.id.musicButton;
            } else if (v.getId() == R.id.conferencingButton) {
                cat = Category.CONFERENCING;
                buttonId = R.id.conferencingButton;
            } else if (v.getId() == R.id.largePortButton) {
                    cat = Category.LARGE_PORT;
                    buttonId = R.id.largePortButton;
            }

            CharSequence text = cat.name();
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(getContext(), text, duration);
            toast.show();

            //set all buttons back to default background color
            if (runPortTests) {
                view.findViewById(R.id.smallPortButton).setBackgroundColor(Color.GRAY);
                view.findViewById(R.id.largePortButton).setBackgroundColor(Color.GRAY);
            } else {
                view.findViewById(R.id.videoButton).setBackgroundColor(Color.GRAY);
                view.findViewById(R.id.musicButton).setBackgroundColor(Color.GRAY);
                view.findViewById(R.id.conferencingButton).setBackgroundColor(Color.GRAY);
            }

            //set selected button to white background
            view.findViewById(buttonId).setBackgroundColor(Color.WHITE);

            //get previously selected apps, if exists
            prevSelectedApps.put(currentCat.toString(), adapter.getSelectedApps());
            ArrayList<ApplicationBean> selectedApps = prevSelectedApps.containsKey(cat.toString())
                    ? prevSelectedApps.get(cat.toString()) : null;
            currentCat = cat;
            //open the new tab
            adapter = new ImageCheckBoxListAdapter(apps, context, runPortTests, cat, selectedApps);
            getParentFragmentManager().beginTransaction().detach(SelectionFragment.this)
                    .attach(SelectionFragment.this).commit();
            TextView totSize = view.findViewById(R.id.totSizeTextView);
            totSize.setText(String.format(Locale.getDefault(),
                    context.getString(R.string.total_size), adapter.getTotalSize()));
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            context = getContext();

            //caller passes whether this fragment should show apps or ports
            Bundle bundle = getArguments();
            assert bundle != null;
            runPortTests = bundle.getBoolean("runPortTest");

            // This method parses JSON file which contains details for different
            // Applications and returns HashMap of ApplicationBean type
            apps = parseAppJSON();

            // Main screen checkbox Adapter. This is populated from HashMap
            // retrieved from above method
            currentCat = runPortTests ? Category.SMALL_PORT : Category.VIDEO;
            adapter = new ImageCheckBoxListAdapter(apps, context, runPortTests, currentCat, null);

            // Display a warning if the user is on wifi
            ConnectivityManager connectivityManager = (ConnectivityManager) this.context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            TelephonyManager telephonyManager = (TelephonyManager) this.context
                    .getSystemService(Context.TELEPHONY_SERVICE);

            if (connectivityManager == null) {
                return;
            }

            //TODO: Switch to non-deprecated library without increasing minSDK?
            NetworkInfo networkInfo = connectivityManager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            String carrierName;
            if (telephonyManager != null) {
                carrierName = telephonyManager.getNetworkOperatorName();
            } else {
                carrierName = getString(R.string.your_carrier);
            }

            if (networkInfo != null && networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                carrierDisplay = "WiFi";
                showWifiToast(carrierName);
            } else {
                carrierDisplay = carrierName;
            }
        } catch (Exception e) {
            Log.e("selectionFragment", "Something went wrong creating selectionFragment", e);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        int menuItemId = runPortTests ? R.id.nav_run_port : R.id.nav_run;
        MenuItem menuItem = navigationView.getMenu().findItem(menuItemId);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        requireActivity().setTitle(menuItem.getTitle());
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_selection, parent, false);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        //attach tab buttons to the listener, so that clicking the button will change tabs
        if (runPortTests) {
            view.findViewById(R.id.portTabs).setVisibility(View.VISIBLE);
            view.findViewById(R.id.appTabs).setVisibility(View.INVISIBLE);
            //app buttons
            view.findViewById(R.id.appTabs).setVisibility(View.INVISIBLE);
            view.findViewById(R.id.smallPortButton).setOnClickListener(changeTab);
            view.findViewById(R.id.largePortButton).setOnClickListener(changeTab);
        } else {
            view.findViewById(R.id.appTabs).setVisibility(View.VISIBLE);
            //app buttons
            view.findViewById(R.id.appTabs).setVisibility(View.VISIBLE);
            view.findViewById(R.id.portTabs).setVisibility(View.INVISIBLE);
            view.findViewById(R.id.videoButton).setOnClickListener(changeTab);
            view.findViewById(R.id.musicButton).setOnClickListener(changeTab);
            view.findViewById(R.id.conferencingButton).setOnClickListener(changeTab);
        }

        //set total size text field
        TextView totSize = view.findViewById(R.id.totSizeTextView);
        totSize.setText(String.format(Locale.getDefault(), context.getString(R.string.total_size),
                adapter.getTotalSize()));

        //run button
        Button nextButton = view.findViewById(R.id.nextButton);
        if (runPortTests) {
            nextButton.setText(R.string.nav_run_port);
        } else {
            nextButton.setText(R.string.nav_run);
        }
        nextButton.setOnClickListener(nextButtonOnClick);

        //the apps
        RecyclerView appList = view.findViewById(R.id.appsRecyclerView);
        appList.setLayoutManager(new LinearLayoutManager(context));
        appList.setAdapter(adapter);
    }

    /**
     * Display a warning if the user is on Wi-Fi.
     * @param carrier the user's phone carrier
     */
    private void showWifiToast(String carrier) {
        if (carrier.equals("")) {
            carrier = getString(R.string.your_carrier).toLowerCase();
        }
        CharSequence text = String.format(getString(R.string.wifiWarning), carrier);
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        toast.show();
    }

    /**
     * This method parses apps_list.json file located in assets folder.
     * This file has all the basic details of apps for replay.
     *
     * @return ArrayList of all apps/ports in the JSON file
     */
    @NonNull
    private ArrayList<ApplicationBean> parseAppJSON() {
        ArrayList<ApplicationBean> apps = new ArrayList<>();
        BufferedReader in = null;
        try {
            StringBuilder buf = new StringBuilder();
            InputStream json = context.getAssets().open(Consts.APPS_FILENAME);
            in = new BufferedReader(new InputStreamReader(json));
            String str;

            while ((str = in.readLine()) != null) {
                buf.append(str);
            }

            in.close();

            JSONObject jObject = new JSONObject(buf.toString());
            JSONArray jArray = jObject.getJSONArray("apps");
            String port443SmallFile = jObject.getString("port443small");
            String port443LargeFile = jObject.getString("port443large");

            JSONObject appObj;
            ApplicationBean bean;
            for (int i = 0; i < jArray.length(); i++) {
                appObj = jArray.getJSONObject(i);
                bean = new ApplicationBean();

                bean.setDataFile(appObj.getString("datafile"));
                bean.setSize(appObj.getInt("size")); //JSON size only for 1 replay
                bean.setTime(appObj.getInt("time") * 2); //JSON time only for 1 replay
                bean.setImage(appObj.getString("image"));
                if (appObj.has("englishOnly")) {
                    bean.setEnglishOnly(appObj.getBoolean("englishOnly"));
                } else if (appObj.has("frenchOnly")) {
                    bean.setFrenchOnly(appObj.getBoolean("frenchOnly"));
                }

                Category cat = Category.valueOf(appObj.getString("category"));
                bean.setCategory(cat);
                //"random" test for ports is port 443
                if (cat == Category.SMALL_PORT) {
                    bean.setRandomDataFile(port443SmallFile);
                } else if (cat == Category.LARGE_PORT) {
                    bean.setRandomDataFile(port443LargeFile);
                } else {
                    bean.setRandomDataFile(appObj.getString("randomdatafile"));
                }

                if (cat == Category.SMALL_PORT || cat == Category.LARGE_PORT) {
                    bean.setName(String.format(getString(R.string.port_name), appObj.getString(("name"))));
                } else {
                    bean.setName(appObj.getString("name")); //app names stored in JSON file
                }

                apps.add(bean);
            }
        } catch (IOException ex) {
            Log.e("selectionFragment", "IOException reading file " + Consts.APPS_FILENAME, ex);
        } catch (JSONException ex) {
            Log.e("selectionFragment", "JSONException parsing JSON file " + Consts.APPS_FILENAME, ex);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    Log.w("selectionFragment", "Issue closing file", e);
                }
            }
        }
        return apps;
    }
}
