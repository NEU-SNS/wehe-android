package mobi.meddle.wehe.adapter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.constant.Consts;

/**
 * Used to display the apps/ports while the replays are running.
 * XML layout: list_item_replay.xml; Goes in appsRecyclerView in activity_replay.xml
 */
public class ImageReplayRecyclerViewAdapter extends
        RecyclerView.Adapter<ImageReplayRecyclerViewAdapter.ViewHolder> {

    private final List<ApplicationBean> dataList; //list of apps/ports
    private final ReplayActivity replayAct; //replay activity, to get resources
    private final boolean runPortTests; //self explanatory
    private boolean isTomography = false;

    /**
     * Constructor.
     *
     * @param list         list of apps/ports to run replays
     * @param replayAct    a ReplayActivity
     * @param runPortTests true if port tests are being run; false otherwise
     */
    public ImageReplayRecyclerViewAdapter(List<ApplicationBean> list, ReplayActivity replayAct,
                                          boolean runPortTests) {
        this.replayAct = replayAct;
        this.dataList = list;
        this.runPortTests = runPortTests;
    }

    public void setTomography(boolean isTomography) {
        this.isTomography = isTomography;
    }

    @Override
    public int getItemCount() {
        return dataList.size() + 1;
    }

    //runs every time an app/port scrolls onto screen to load that app/port's view
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.xputOriginalTextView.setVisibility(View.GONE);
        holder.xputOriginalValueTextView.setVisibility(View.GONE);
        holder.xputTestTextView.setVisibility(View.GONE);
        holder.xputTestValueTextView.setVisibility(View.GONE);
        holder.arcepLogo.setVisibility(View.GONE);
        holder.alertArcep.setVisibility(View.GONE);
        holder.imageButton.setVisibility(View.GONE);

        if (position == 0) {
            holder.tvAppStatus.setVisibility(View.GONE);
            holder.tvAppTime.setVisibility(View.GONE);
            holder.tvAppSize.setVisibility(View.GONE);
            holder.img.setVisibility(View.GONE);
            String description;
            if (isTomography) {
                description = replayAct.getString(R.string.tomo_test_desc);
            } else if (runPortTests) {
                description = replayAct.getString(R.string.normal_port_desc);
            } else {
                description = replayAct.getString(R.string.normal_app_desc);
            }
            description = ""; //TODO: comment out when time to add test descriptions
            holder.tvAppName.setText(description);
            holder.tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_PX, replayAct.getResources().getDimension(R.dimen.text_medium));
            return;
        } else if (holder.tvAppStatus.getVisibility() == View.GONE) {
            holder.tvAppName.setVisibility(View.VISIBLE);
            holder.tvAppStatus.setVisibility(View.VISIBLE);
            holder.tvAppTime.setVisibility(View.VISIBLE);
            holder.img.setVisibility(View.VISIBLE);
            holder.tvAppName.setTextSize(TypedValue.COMPLEX_UNIT_PX, replayAct.getResources().getDimension(R.dimen.text_small));
        }
        final ApplicationBean app = dataList.get(position - 1);
        Resources res = replayAct.getResources();
        //load name
        holder.tvAppName.setText(app.getName());
        //load time for only apps
        if (runPortTests) {
            holder.tvAppTime.setVisibility(View.GONE);
        } else {
            int time = Consts.TIMEOUT_ENABLED ? Math.min(app.getTime(), Consts.REPLAY_APP_TIMEOUT * 2)
                    : app.getTime();
            holder.tvAppTime.setText(String.format(Locale.getDefault(),
                    res.getString(R.string.replay_time), time));
            holder.tvAppTime.setVisibility(View.VISIBLE);
        }
        //load size
        int numTests = 2 * (app.isTomography() ? Consts.NUM_TOMOGRAPHY_TESTS : 1);
        holder.tvAppSize.setText(String.format(Locale.getDefault(),
                res.getString(R.string.replay_size), numTests, app.getSize()));
        holder.tvAppSize.setVisibility(View.VISIBLE);

        // here we set different color for different results
        int red = res.getColor(R.color.red);
        int green = res.getColor(R.color.forestGreen);
        int yellow = res.getColor(R.color.orange2);
        int blue = res.getColor(R.color.blue0);

        //load arcep alert button and logo
        if (app.getArcepNeedsAlerting()) {
            holder.arcepLogo.setVisibility(View.VISIBLE);
            holder.alertArcep.setVisibility(View.VISIBLE);
            holder.alertArcep.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    app.setArcepNeedsAlerting(false);
                    holder.arcepLogo.setVisibility(View.GONE);
                    holder.alertArcep.setVisibility(View.GONE);

                    //open arcep site in browser; tests will continue running in background
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(Consts.ARCEP_URL));
                    replayAct.startActivity(i);
                }
            });
        }
        //load status
        holder.tvAppStatus.setText(app.getStatus());
        String status = app.getStatus().trim();
        if (status.equals(res.getString(R.string.no_diff))) {
            holder.tvAppStatus.setTextColor(green);
            holder.xputOriginalTextView.setVisibility(View.VISIBLE);
            holder.xputOriginalValueTextView.setVisibility(View.VISIBLE);
            holder.xputTestTextView.setVisibility(View.VISIBLE);
            holder.xputTestValueTextView.setVisibility(View.VISIBLE);
        } else if (status.equals(res.getString(R.string.has_diff))) {
            holder.tvAppStatus.setTextColor(red);
            if (!app.getError().equals(res.getString(R.string.not_all_tcp_sent_text))) {
                holder.xputOriginalTextView.setVisibility(View.VISIBLE);
                holder.xputOriginalValueTextView.setVisibility(View.VISIBLE);
                holder.xputTestTextView.setVisibility(View.VISIBLE);
                holder.xputTestValueTextView.setVisibility(View.VISIBLE);
            }
            holder.imageButton.setVisibility(View.VISIBLE);
            holder.imageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    makeInfoBox(res.getString(R.string.has_diff), app.getError());
                }
            });
        } else if (status.equals(res.getString(R.string.inconclusive))) {
            holder.tvAppStatus.setTextColor(yellow);
            if (app.getError().equals("")) {
                holder.xputOriginalTextView.setVisibility(View.VISIBLE);
                holder.xputOriginalValueTextView.setVisibility(View.VISIBLE);
                holder.xputTestTextView.setVisibility(View.VISIBLE);
                holder.xputTestValueTextView.setVisibility(View.VISIBLE);
            } else {
                holder.imageButton.setVisibility(View.VISIBLE);
                holder.imageButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        makeInfoBox(res.getString(R.string.inconclusive).split(",")[0], app.getError());
                    }
                });
            }
        } else if (status.equals(res.getString(R.string.tomo_failed))) {
            holder.tvAppStatus.setTextColor(red);
            holder.xputOriginalTextView.setVisibility(View.VISIBLE);
        } else if (status.equals(res.getString(R.string.tomo_succ))) {
            holder.tvAppStatus.setTextColor(green);
            holder.xputOriginalTextView.setVisibility(View.VISIBLE);
        } else {
            holder.tvAppStatus.setTextColor(blue);
        }

        //load throughputs
        String appName = runPortTests ?
                String.format(res.getString(R.string.port_name), app.getName().split("\\s+")[1]) : app.getName();
        String port443 = res.getString(R.string.port_name, "443");
        String xputOriginalLabel = res.getString(R.string.xputOriginal, appName);
        String xputTestLabel = runPortTests ? res.getString(R.string.xputOriginal, port443) :
                res.getString(R.string.xputTest, appName);
        String throughputFormat = replayAct.getResources().getString(R.string.throughput);
        String xputOriginal = String.format(Locale.getDefault(), throughputFormat, app.originalThroughput);
        String xputTest = String.format(Locale.getDefault(), throughputFormat, app.randomThroughput);

        if (app.isTomography()) {
            if (app.getDifferentiationNetwork().equals("")) {
                holder.xputOriginalTextView.setText(R.string.tomo_failed_desc);
            } else {
                holder.xputOriginalTextView.setText(String.format(res.getString(R.string.tomo_succ_desc),
                        app.getDifferentiationNetwork()));
            }
        } else {
            holder.xputOriginalTextView.setText(xputOriginalLabel);
            holder.xputTestTextView.setText(xputTestLabel);
            holder.xputOriginalValueTextView.setText(xputOriginal);
            holder.xputTestValueTextView.setText(xputTest);
        }

        holder.img.setImageDrawable(ResourcesCompat.getDrawable(replayAct.getResources(),
                res.getIdentifier(app.getImage(),
                        "drawable", replayAct.getPackageName()), null));
    }

    @NonNull
    @Override
    public ImageReplayRecyclerViewAdapter.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.list_item_replay, parent, false);

        return new ViewHolder(view);
    }

    /**
     * Display an information box if user clicks the "i" icon.
     *
     * @param title title of the info box
     * @param msg   the message to display to the user
     */
    private void makeInfoBox(String title, String msg) {
        new AlertDialog.Builder(replayAct, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle(title)
                .setMessage(msg)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                }).show();
    }

    /**
     * The view that the user sees for each app/port being run in the tests.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        final TextView tvAppName;
        final TextView tvAppSize;
        final TextView tvAppTime;
        final TextView tvAppStatus;
        final TextView xputOriginalTextView;
        final TextView xputOriginalValueTextView;
        final TextView xputTestTextView;
        final TextView xputTestValueTextView;
        final ImageView img;
        final ImageButton imageButton;
        final ImageView arcepLogo;
        final Button alertArcep;

        ViewHolder(View view) {
            super(view);
            xputOriginalTextView = view.findViewById(R.id.xputOriginal);
            xputOriginalValueTextView = view.findViewById(R.id.xputOriginalValue);
            xputTestTextView = view.findViewById(R.id.xputTest);
            xputTestValueTextView = view.findViewById(R.id.xputTestValue);

            tvAppName = view.findViewById(R.id.resultNameText);
            tvAppSize = view.findViewById(R.id.appSize);
            tvAppTime = view.findViewById(R.id.appTime);
            tvAppStatus = view.findViewById(R.id.appStatusTextView);
            img = view.findViewById(R.id.appImageView);
            imageButton = view.findViewById(R.id.ib_reverse_engineer_info);

            arcepLogo = view.findViewById(R.id.arcepLogoImageView);
            alertArcep = view.findViewById(R.id.reportToArcep);
        }
    }
}
