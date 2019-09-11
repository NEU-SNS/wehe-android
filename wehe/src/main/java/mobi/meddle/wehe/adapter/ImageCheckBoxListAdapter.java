package mobi.meddle.wehe.adapter;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.bean.ApplicationBean;

public class ImageCheckBoxListAdapter extends RecyclerView.Adapter<ImageCheckBoxListAdapter.SelectionViewHolder> implements
        OnClickListener {

    public ArrayList<ApplicationBean> selectedApps;
    /**
     * A list containing some sample data to show.
     */
    private ArrayList<ApplicationBean> dataList;
    private Context context;

    public ImageCheckBoxListAdapter(HashMap<String, ApplicationBean> apps, Context context) {
        dataList = new ArrayList<>();
        selectedApps = new ArrayList<>();
        this.context = context;

        for (String s : apps.keySet()) {
            // make sure dataList and randomDataList have the same order
            ApplicationBean app = apps.get(s);
            dataList.add(app);
            selectedApps.add(app);
        }
    }

    @Override
    public SelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        view.setOnClickListener(this);
        return new SelectionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectionViewHolder holder, int position) {
        ApplicationBean app = dataList.get(position);
        holder.view.setTag(app);
        holder.sw.setChecked(true);
        // Set the click listener for the checkbox
        holder.sw.setChecked(app.isSelected());
        holder.img.setImageDrawable(context.getResources().getDrawable(
                context.getResources().getIdentifier(app.getImage(),
                        "drawable", context.getPackageName())));
        holder.appNameTextView.setText(app.getName());

        holder.appTimeTextView.setText(String.format(Locale.getDefault(), "%s %s seconds",
                context.getString(R.string.appTimeLabel), app.getTime() * 2));

        holder.appSizeTextView.setText(String.format(Locale.getDefault(), "%s %s MB",
                context.getString(R.string.appSizeLabel), app.getSize() * 2));
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.list_item_selection;
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }


    /**
     * Will be called when a switch has been clicked.
     */
    @Override
    public void onClick(View view) {
        ApplicationBean app = (ApplicationBean) view.getTag();
        Switch sw = view.findViewById(R.id.isSelectedSwitch);
        sw.setChecked(!sw.isChecked());
        app.setSelected(sw.isChecked());

        if (sw.isChecked()) {
            if (!selectedApps.contains(app)) {
                selectedApps.add(app);
            }
        } else {
            selectedApps.remove(app);
        }
    }

    static class SelectionViewHolder extends RecyclerView.ViewHolder {
        private final Switch sw;
        private final ImageView img;
        private final TextView appNameTextView;
        private final TextView appTimeTextView;
        private final TextView appSizeTextView;
        private final View view;

        SelectionViewHolder(View view) {
            super(view);
            this.view = view;
            this.sw = view.findViewById(R.id.isSelectedSwitch);
            this.img = view.findViewById(R.id.appImageView);
            this.appNameTextView = view.findViewById(R.id.app_name_textview);
            this.appTimeTextView = view.findViewById(R.id.app_time_textview);
            this.appSizeTextView = view.findViewById(R.id.app_size_textview);
        }
    }
}

