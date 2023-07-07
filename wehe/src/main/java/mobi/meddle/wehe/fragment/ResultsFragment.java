package mobi.meddle.wehe.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;

/**
 * Fragment to show the results page.
 * Previous Results item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_results.xml
 * results_item.xml goes in resultsListView in fragment_results.xml
 */
public class ResultsFragment extends Fragment {

    public static final String TAG = "ResultsFragment";
    private ArrayList<Result> results;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_results);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        requireActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_results, parent, false);
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        SharedPreferences history = this.requireActivity().getSharedPreferences(
                ReplayActivity.STATUS, Context.MODE_PRIVATE);
        try {
            //load results from disk into results ArrayList
            JSONObject resultsWithDate = new JSONObject(history.getString("lastResult", "{}"));
            results = new ArrayList<>();
            if (resultsWithDate.length() > 0) {
                Iterator<String> iter = resultsWithDate.keys();
                while (iter.hasNext()) {
                    String currentDate = iter.next();
                    JSONArray responses = resultsWithDate.getJSONArray(currentDate);
                    for (int i = 0; i < responses.length(); i++) {
                        JSONObject response = responses.getJSONObject(i);
                        boolean isPortTests = response.getBoolean("isPort");
                        Result current = new Result();

                        current.isPortTest = isPortTests;
                        current.resultNameText = response.getString("appName");
                        current.appImage = response.getString("appImage");
                        current.differentiationText = response.getString("status");
                        Date date = new Date(Long.parseLong(response.getString("date")));
                        if (DateUtils.isToday(date.getTime())) {
                            DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT,
                                    Locale.getDefault());
                            current.dateText = String.format(getString(R.string.today),
                                    df.format(date));
                        } else {
                            DateFormat df = DateFormat.getDateTimeInstance(
                                    DateFormat.LONG, DateFormat.SHORT, Locale.getDefault());
                            current.dateText = df.format(date);
                        }

                        current.appThroughput = response.getDouble("xput_avg_original");
                        current.nonAppThroughput = response.getDouble("xput_avg_test");
                        current.ipType = response.getBoolean("isIPv6") ? "IPv6" : "IPv4";
                        current.server = response.getString("server");
                        current.carrier = response.getString("carrier");
                        if (response.has("localizationNetwork")) {
                            current.isLocalization = true;
                            current.differentiationNetwork = response.getString("localizationNetwork");
                        } else {
                            current.isLocalization = false;
                        }

                        results.add(current);
                    }
                }

            }
            Collections.reverse(results);
        } catch (JSONException e) {
            Log.e("resultFragment", "Error loading results", e);
        }

        //load results onto screen
        ArrayAdapter<Result> resultsAdapter = new ArrayAdapter<Result>(this.requireActivity(),
                0, results) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                Result current = results.get(position);

                // Inflate only once
                if (convertView == null) {
                    ViewHolder viewHolder = new ViewHolder();
                    viewHolder.isPortTest = current.isPortTest;
                    convertView = requireActivity().getLayoutInflater()
                            .inflate(R.layout.results_item, parent, false);

                    viewHolder.resultNameText = convertView.findViewById(R.id.resultNameText);
                    viewHolder.dateText = convertView.findViewById(R.id.dateText);
                    viewHolder.differentiationText = convertView.findViewById(R.id.differentiationText);
                    viewHolder.appThroughput = convertView.findViewById(R.id.appThroughput);
                    viewHolder.nonAppThroughput = convertView.findViewById(R.id.nonappThroughput);
                    viewHolder.appImageView = convertView.findViewById(R.id.appImageView);
                    viewHolder.ipType = convertView.findViewById(R.id.ipTypeValue);
                    viewHolder.server = convertView.findViewById(R.id.serverValue);
                    viewHolder.carrier = convertView.findViewById(R.id.carrierValue);
                    convertView.setTag(viewHolder);
                }

                TextView resultNameText = ((ViewHolder) convertView.getTag()).resultNameText;
                TextView dateText = ((ViewHolder) convertView.getTag()).dateText;
                TextView ipTypeText = ((ViewHolder) convertView.getTag()).ipType;
                TextView carrierText = ((ViewHolder) convertView.getTag()).carrier;
                TextView serverText = ((ViewHolder) convertView.getTag()).server;

                dateText.setText(current.dateText);
                ipTypeText.setText(current.ipType);
                serverText.setText(current.server);
                carrierText.setText(current.carrier);

                TextView differentiationText = ((ViewHolder) convertView.getTag()).differentiationText;
                TextView appThroughput = ((ViewHolder) convertView.getTag()).appThroughput;
                TextView nonAppThroughput = ((ViewHolder) convertView.getTag()).nonAppThroughput;
                ImageView appImageView = ((ViewHolder) convertView.getTag()).appImageView;

                TextView appLabel = convertView.findViewById(R.id.appThroughputNameText);
                TextView nonAppLabel = convertView.findViewById(R.id.nonappThroughputNameText);
                nonAppLabel.setVisibility(View.VISIBLE);
                appThroughput.setVisibility(View.VISIBLE);
                nonAppThroughput.setVisibility(View.VISIBLE);
                if (current.isLocalization) {
                    if (current.differentiationNetwork.equals("")) {
                        appLabel.setText(R.string.localize_failed_desc);
                    } else {
                        appLabel.setText(String.format(getString(R.string.localize_succ_desc),
                                current.differentiationNetwork));
                    }
                    nonAppLabel.setVisibility(View.GONE);
                    appThroughput.setVisibility(View.GONE);
                    nonAppThroughput.setVisibility(View.GONE);
                } else if (current.isPortTest) {
                    appLabel.setText(String.format(getString(R.string.port_throughput),
                            current.resultNameText.split(" ")[1]));
                    nonAppLabel.setText(String.format(getString(R.string.port_throughput), "443"));
                } else {
                    appLabel.setText(getString(R.string.app_throughput));
                    nonAppLabel.setText(getString(R.string.nonapp_throughput));
                }

                int appImageDrawableId = getResources().getIdentifier(current.appImage, "drawable",
                        requireActivity().getPackageName());
                appImageView.setImageDrawable(ContextCompat.getDrawable(getContext(), appImageDrawableId));
                resultNameText.setText(current.resultNameText);
                //display result type
                switch (current.differentiationText) {
                    case "has diff":
                        differentiationText.setText(getString(R.string.has_diff));
                        differentiationText.setTextColor(getResources().getColor(R.color.red));
                        break;
                    case "no diff":
                        differentiationText.setText(getString(R.string.no_diff));
                        differentiationText.setTextColor(getResources().getColor(R.color.forestGreen));
                        break;
                    case "inconclusive":
                        differentiationText.setText(getString(R.string.inconclusive).split(",")[0]);
                        differentiationText.setTextColor(getResources().getColor(R.color.orange2));
                        break;
                    case "localize failed":
                        differentiationText.setText(R.string.localize_failed);
                        differentiationText.setTextColor(getResources().getColor(R.color.red));
                        break;
                    case "localize succ":
                        differentiationText.setText(R.string.localize_succ);
                        differentiationText.setTextColor(getResources().getColor(R.color.forestGreen));
                        break;
                    default:
                        differentiationText.setText(current.differentiationText);
                        differentiationText.setTextColor(getResources().getColor(R.color.orange2));
                        break;
                }
                appThroughput.setText(String.format(Locale.getDefault(),
                        getString(R.string.throughput), current.appThroughput));
                nonAppThroughput.setText(String.format(Locale.getDefault(),
                        getString(R.string.throughput), current.nonAppThroughput));
                return convertView;
            }
        };

        ListView resultsList = view.findViewById(R.id.resultsListView);
        resultsList.setAdapter(resultsAdapter);
    }

    /**
     * The view that the user sees for each app/port result.
     */
    private static class ViewHolder {
        boolean isPortTest;
        TextView resultNameText;
        ImageView appImageView;
        TextView dateText;
        TextView differentiationText;
        TextView appThroughput;
        TextView nonAppThroughput;
        TextView ipType;
        TextView server;
        TextView carrier;
    }

    /**
     * The information for each app/port result.
     */
    private static class Result {
        boolean isPortTest;
        String resultNameText;
        String appImage;
        String dateText;
        String differentiationText;
        Double appThroughput;
        Double nonAppThroughput;
        String ipType;
        String server;
        String carrier;
        boolean isLocalization;
        String differentiationNetwork;
    }
}
