package mobi.meddle.wehe.fragment;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;

public class ResultsFragment extends Fragment {

    public static final String TAG = "ResultsFragment";
    SharedPreferences history;
    String finalResult;
    JSONObject resultsWithDate, response;
    Iterator<String> iter;
    String currentDate, replayName, status, replayDate, appName;
    JSONArray responses;
    Double xputOriginal, xputTest;

    ArrayList<Result> results;
    Result current;

    ListView resultsList;

    String has_diff, no_diff, red, green, yellow;

    SimpleDateFormat stringToDateFormat, dateToStringFormat, dateToTimeStringFormat;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        NavigationView navigationView = Objects.requireNonNull(getActivity()).findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_results);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        getActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_results, parent, false);
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        history = Objects.requireNonNull(this.getActivity()).getSharedPreferences(ReplayActivity.STATUS,
                Context.MODE_PRIVATE);
        stringToDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss", Locale.getDefault());
        dateToStringFormat = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        dateToTimeStringFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        has_diff = getString(R.string.has_diff);
        no_diff = getString(R.string.no_diff);
        red = getString(R.string.color_red);
        green = getString(R.string.color_green);
        yellow = getString(R.string.color_yellow);
        try {
            resultsWithDate = new JSONObject(history.getString(
                    "lastResult", "{}"));
            finalResult = "";
            results = new ArrayList<>();
            if (resultsWithDate.length() > 0) {
                iter = resultsWithDate.keys();
                while (iter.hasNext()) {
                    currentDate = iter.next();
                    responses = resultsWithDate.getJSONArray(currentDate);
                    for (int i = 0; i < responses.length(); i++) {
                        response = responses.getJSONObject(i);
                        current = new Result();
                        replayName = response.getString("replayName").split("-")[0];
                        replayDate = response.getString("date");
                        xputOriginal = response.getDouble("xput_avg_original");
                        xputTest = response.getDouble("xput_avg_test");
                        status = response.getString("status");
                        appName = response.getString("appName");

                        current.appName = appName;
                        current.resultNameText = appName;
                        current.differentiationText = status;
                        try {
                            Date formattedDate = stringToDateFormat.parse(replayDate);
                            if (DateUtils.isToday(formattedDate.getTime())) {
                                current.dateText = String.format(Locale.getDefault(), getString(R.string.today), dateToTimeStringFormat.format(formattedDate));
                            } else {
                                current.dateText = dateToStringFormat.format(formattedDate);
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        current.appThroughput = xputTest;
                        current.nonappThroughput = xputOriginal;
                        results.add(current);
                    }
                }

            }
            Collections.reverse(results);
        } catch (JSONException e1) {
            e1.printStackTrace();
        }

        ArrayAdapter<Result> resultsAdapter = new ArrayAdapter<Result>(this.getActivity(), 0, results) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {

                Result current = results.get(position);

                // Inflate only once
                if (convertView == null) {
                    convertView = Objects.requireNonNull(getActivity()).getLayoutInflater()
                            .inflate(R.layout.results_item, parent, false);
                    ViewHolder viewHolder = new ViewHolder();
                    viewHolder.resultNameText =
                            convertView.findViewById(R.id.resultNameText);
                    viewHolder.differentiationText =
                            convertView.findViewById(R.id.differentiationText);
                    viewHolder.dateText =
                            convertView.findViewById(R.id.dateText);
                    viewHolder.appThroughput =
                            convertView.findViewById(R.id.appThroughput);
                    viewHolder.nonappThroughput =
                            convertView.findViewById(R.id.nonappThroughput);
                    viewHolder.appImageView = convertView.findViewById(R.id.appImageView);

                    convertView.setTag(viewHolder);
                }

                TextView resultNameText = ((ViewHolder) convertView.getTag()).resultNameText;
                TextView differentiationText = ((ViewHolder) convertView.getTag()).differentiationText;
                TextView dateText = ((ViewHolder) convertView.getTag()).dateText;
                TextView appThroughput = ((ViewHolder) convertView.getTag()).appThroughput;
                TextView nonappThroughput = ((ViewHolder) convertView.getTag()).nonappThroughput;
                ImageView appImageView = ((ViewHolder) convertView.getTag()).appImageView;

                int appImageDrawableId = getResources().getIdentifier(current.appName.toLowerCase(), "drawable",
                        Objects.requireNonNull(getActivity()).getPackageName());
                appImageView.setImageDrawable(getResources().getDrawable(appImageDrawableId));
                resultNameText.setText(current.resultNameText);
                differentiationText.setText(current.differentiationText);
                if (current.differentiationText.equals(has_diff)) {
                    differentiationText.setTextColor(Color.parseColor(red));
                } else if (current.differentiationText.equals(no_diff)) {
                    differentiationText.setTextColor(Color.parseColor(green));
                } else {
                    differentiationText.setTextColor(Color.parseColor(yellow));
                }
                dateText.setText(current.dateText);
                appThroughput.setText(String.format(Locale.getDefault(), getString(R.string.throughput), current.appThroughput));
                nonappThroughput.setText(String.format(Locale.getDefault(), getString(R.string.throughput), current.nonappThroughput));

                return convertView;
            }
        };

        resultsList = view.findViewById(R.id.resultsListView);
        resultsList.setAdapter(resultsAdapter);
    }

    static class ViewHolder {

        ImageView appImageView;
        TextView resultNameText;
        TextView differentiationText;
        TextView dateText;
        TextView appThroughput;
        TextView nonappThroughput;
    }

    static class Result {
        String appName;
        String resultNameText;
        String differentiationText;
        String dateText;
        Double appThroughput;
        Double nonappThroughput;
    }
}
