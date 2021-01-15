package mobi.meddle.wehe.adapter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    private final Boolean runPortTests; //self explanatory

    /**
     * Constructor.
     *
     * @param list         list of apps/ports to run replays
     * @param replayAct    a ReplayActivity
     * @param runPortTests true if port tests are being run; false otherwise
     */
    public ImageReplayRecyclerViewAdapter(List<ApplicationBean> list, ReplayActivity replayAct,
                                          Boolean runPortTests) {
        this.replayAct = replayAct;
        this.dataList = list;
        this.runPortTests = runPortTests;
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    //runs every time an app/port scrolls onto screen to load that app/port's view
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ApplicationBean app = dataList.get(position);
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
        holder.tvAppSize.setText(String.format(Locale.getDefault(),
                res.getString(R.string.replay_size), app.getSize()));
        holder.tvAppSize.setVisibility(View.VISIBLE);

        // here we set different color for different results
        int red = res.getColor(R.color.red);
        int green = res.getColor(R.color.forestGreen);
        int yellow = res.getColor(R.color.orange2);
        int blue = res.getColor(R.color.blue0);

        holder.xputOriginalTextView.setVisibility(View.GONE);
        holder.xputOriginalValueTextView.setVisibility(View.GONE);
        holder.xputTestTextView.setVisibility(View.GONE);
        holder.xputTestValueTextView.setVisibility(View.GONE);
        holder.arcepLogo.setVisibility(View.GONE);
        holder.alertArcep.setVisibility(View.GONE);
        holder.imageButton.setVisibility(View.GONE);

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
        if (app.getStatus().trim().equals(res.getString(R.string.no_diff))) {
            holder.tvAppStatus.setTextColor(green);
            holder.xputOriginalTextView.setVisibility(View.VISIBLE);
            holder.xputOriginalValueTextView.setVisibility(View.VISIBLE);
            holder.xputTestTextView.setVisibility(View.VISIBLE);
            holder.xputTestValueTextView.setVisibility(View.VISIBLE);
        } else if (app.getStatus().trim().equals(res.getString(R.string.has_diff))) {
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
        } else if (app.getStatus().trim().equals(res.getString(R.string.inconclusive))) {
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

        holder.xputOriginalTextView.setText(xputOriginalLabel);
        holder.xputTestTextView.setText(xputTestLabel);
        holder.xputOriginalValueTextView.setText(xputOriginal);
        holder.xputTestValueTextView.setText(xputTest);

        holder.img.setImageDrawable(res.getDrawable(res.getIdentifier(app.getImage(),
                "drawable", replayAct.getPackageName())));
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
                .setNeutralButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
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
