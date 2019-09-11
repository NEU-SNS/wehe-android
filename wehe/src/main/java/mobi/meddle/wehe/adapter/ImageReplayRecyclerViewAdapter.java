package mobi.meddle.wehe.adapter;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.ReplayActivity;
import mobi.meddle.wehe.bean.ApplicationBean;

public class ImageReplayRecyclerViewAdapter extends
        RecyclerView.Adapter<ImageReplayRecyclerViewAdapter.ViewHolder> {

    /* The inflater used to inflate the XML layout */
    // private LayoutInflater inflator;

    /**
     * A list containing some sample data to show.
     */
    private List<ApplicationBean> dataList;
    private ReplayActivity replayAct;
    private View.OnClickListener dpiListener;

    // Provide a suitable constructor (depends on the kind of dataset)
    public ImageReplayRecyclerViewAdapter(List<ApplicationBean> list,
                                          ReplayActivity replayAct) {
        this.replayAct = replayAct;
        this.dataList = list;
        // for each iteration there are two or three replays
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final ApplicationBean app = dataList.get(position);

        holder.tvAppName.setText(app.getName());
        holder.tvAppSize.setText(String.format(Locale.getDefault(), "%s %s MB",
                replayAct.getApplicationContext().getString(R.string.appSizeLabel), app.getSize() * 2));
        holder.tvAppTime.setText(String.format(Locale.getDefault(), "%s %s seconds",
                replayAct.getApplicationContext().getString(R.string.appTimeLabel), app.getTime() * 2));
        holder.tvAppStatus.setText(app.status);

        // here we set different color for different results

        String red = replayAct.getApplicationContext().getString(R.string.color_red);
        String green = replayAct.getApplicationContext().getString(R.string.color_green);
        String yellow = replayAct.getApplicationContext().getString(R.string.color_yellow);
        String blue = replayAct.getApplicationContext().getString(R.string.color_blue);

        holder.xputOriginalTextView.setVisibility(View.GONE);
        holder.xputOriginalValueTextView.setVisibility(View.GONE);
        holder.xputTestTextView.setVisibility(View.GONE);
        holder.xputTestValueTextView.setVisibility(View.GONE);
        holder.imageButton.setVisibility(View.GONE);

        if (app.status.trim().equals(replayAct.getString(R.string.no_diff))) {
            holder.tvAppStatus.setTextColor(Color.parseColor(green));
        } else if (app.status.trim().equals(replayAct.getString(R.string.has_diff))) {
            String color = (app.xputOriginal > app.xputTest) ? green : red;
            holder.tvAppStatus.setTextColor(Color.parseColor(color));
            holder.xputOriginalTextView.setVisibility(View.VISIBLE);
            holder.xputOriginalValueTextView.setVisibility(View.VISIBLE);
            holder.xputTestTextView.setVisibility(View.VISIBLE);
            holder.xputTestValueTextView.setVisibility(View.VISIBLE);
            holder.imageButton.setVisibility(View.VISIBLE);
            holder.imageButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!replayAct.replayOngoing) {
                        // TODO switch this to launch DPI activity to enable DPI analysis
                        new AlertDialog.Builder(replayAct,
                                AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                                .setTitle(replayAct.getString(R.string.bitrate_info_title))
                                .setMessage(
                                        replayAct.getString(R.string.bitrate_info_text))
                                .setNeutralButton(android.R.string.ok,
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface dialog,
                                                                int which) {
                                                // do nothing
                                            }
                                        }).show();
                    } else {
                        Toast.makeText(replayAct.getApplicationContext(), replayAct.getString(R.string.wait_replay_ongoing), Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else if (app.error) {
            holder.tvAppStatus.setTextColor(Color.parseColor(red));
        } else if (app.status.trim().equals(replayAct.getString(R.string.inconclusive)) ||
                app.status.trim().equals(replayAct.getString(R.string.confirmation_replay))) {
            holder.tvAppStatus.setTextColor(Color.parseColor(yellow));
        } else {
            holder.tvAppStatus.setTextColor(Color.parseColor(blue));
        }

        String appName = app.name;

        String xputOriginalLabel = replayAct.getString(R.string.xputOriginal, appName);
        String xputTestLabel = replayAct.getString(R.string.xputTest, appName);

        String xputOriginal = String.format(Locale.getDefault(), "%.1f Mb/s", app.xputOriginal);
        String xputTest = String.format(Locale.getDefault(), "%.1f Mb/s", app.xputTest);

        holder.xputOriginalTextView.setText(xputOriginalLabel);
        holder.xputTestTextView.setText(xputTestLabel);
        holder.xputOriginalValueTextView.setText(xputOriginal);
        holder.xputTestValueTextView.setText(xputTest);

        holder.img.setImageDrawable(replayAct.getResources().getDrawable(
                replayAct.getResources().getIdentifier(app.getImage(),
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        TextView tvAppName;
        TextView tvAppSize;
        TextView tvAppTime;
        TextView tvAppStatus;
        TextView xputOriginalTextView;
        TextView xputOriginalValueTextView;
        TextView xputTestTextView;
        TextView xputTestValueTextView;
        ImageView img;
        ImageButton imageButton;

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
        }
    }
}
