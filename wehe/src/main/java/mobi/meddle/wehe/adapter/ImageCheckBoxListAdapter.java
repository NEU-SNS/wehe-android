package mobi.meddle.wehe.adapter;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.bean.ApplicationBean;
import mobi.meddle.wehe.bean.ApplicationBean.Category;
import mobi.meddle.wehe.constant.Consts;

/**
 * Used to display apps/ports for the user to select.
 * XML layout: list_item_selection.xml; Goes in appsRecyclerView in fragment_selection.xml
 */
public class ImageCheckBoxListAdapter
        extends RecyclerView.Adapter<ImageCheckBoxListAdapter.SelectionViewHolder>
        implements OnClickListener {
    private final ArrayList<ApplicationBean> selectedApps;
    private final ArrayList<ApplicationBean> dataList;
    private final Context context;
    private final boolean runPortTest;

    /**
     * Constructor.
     *
     * @param apps        all the apps/ports that exist
     * @param context     the context
     * @param runPortTest true if the Run Port Tests page is showing for selection; false otherwise
     * @param cat         the category of apps that the current tab is displaying
     * @param prevSelApps when switching tabs, for example, between the video and music tab, the
     *                    apps the user previously chose stays the same. This list contains the
     *                    previously selected apps for the view to display. This variable is null if
     *                    user has not visited the current tab
     */
    public ImageCheckBoxListAdapter(@NonNull ArrayList<ApplicationBean> apps,
                                    @NonNull Context context, boolean runPortTest, Category cat,
                                    ArrayList<ApplicationBean> prevSelApps) {
        dataList = new ArrayList<>(); //all apps/ports that are displayed on screen
        //the 3 random app indexes to select when the app page loads
        int[] randomIndexes = new int[]{-1, -1, -1};
        this.runPortTest = runPortTest;
        this.context = context;
        Locale current = context.getResources().getConfiguration().locale;
        String country = current.getCountry();

        //determine which apps/ports to display on screen
        for (ApplicationBean app : apps) {
            assert app != null;
            if (cat != app.getCategory()) {
                continue;
            }

            if (country.equals("FR") && app.isEnglishOnly()) {
                continue;
            } else if (!country.equals("FR") && app.isFrenchOnly()) {
                continue;
            }

            dataList.add(app);
            app.setSelected(false);
        }

        //user has not visited the tab that is being switched to
        if (prevSelApps == null) {
            selectedApps = new ArrayList<>(); //apps/ports that have been selected to run
            if (dataList.size() < 4) { //all apps/ports selected there are less than 4 to choose from
                for (ApplicationBean app : dataList) {
                    selectedApps.add(app);
                    app.setSelected(true);
                }
            } else { //randomly select 3 apps/ports to check when tab loads
                ArrayList<Integer> allIndexes = new ArrayList<>();
                for (int i = 0; i < dataList.size(); i++) {
                    allIndexes.add(i);
                }
                Collections.shuffle(allIndexes);
                for (int i = 0; i < 3; i++) {
                    selectedApps.add(dataList.get(allIndexes.get(i)));
                    dataList.get(allIndexes.get(i)).setSelected(true);
                    randomIndexes[i] = allIndexes.get(i);
                }
            }
        } else { //user has already visited the tab, so select the previous apps
            selectedApps = prevSelApps;
            for (ApplicationBean app : selectedApps) {
                app.setSelected(true);
            }
        }
    }

    @NonNull
    @Override
    public SelectionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
        view.setOnClickListener(this);
        return new SelectionViewHolder(view);
    }

    //runs every time an app/port scrolls into view to load that app/port's view
    @Override
    public void onBindViewHolder(@NonNull SelectionViewHolder holder, int position) {
        ApplicationBean app = dataList.get(position);
        holder.view.setTag(app);
        holder.sw.setChecked(app.isSelected()); //set if checked
        //set image
        holder.img.setImageDrawable(context.getResources().getDrawable(context.getResources()
                .getIdentifier(app.getImage(), "drawable", context.getPackageName())));
        //set name
        holder.appNameTextView.setText(app.getName());
        //set size
        holder.appSizeTextView.setText(String.format(Locale.getDefault(),
                context.getString(R.string.replay_size), app.getSize()));
        //set time only if app since goal of ports is to run as fast as possible
        if (runPortTest) {
            holder.appTimeTextView.setVisibility(View.GONE);
        } else {
            int time = Consts.TIMEOUT_ENABLED ? Math.min(app.getTime(), Consts.REPLAY_APP_TIMEOUT * 2)
                    : app.getTime();
            holder.appTimeTextView.setText(String.format(Locale.getDefault(),
                    context.getString(R.string.replay_time), time));
            holder.appTimeTextView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return R.layout.list_item_selection;
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public ArrayList<ApplicationBean> getSelectedApps() {
        return selectedApps;
    }

    /**
     * Gets the total size of the selected apps/ports
     *
     * @return total size of selected apps/ports
     */
    public int getTotalSize() {
        int size = 0;
        for (ApplicationBean app : selectedApps) {
            size += app.getSize();
        }
        return size * 2;
    }

    /**
     * Will be called when a switch has been clicked.
     */
    @Override
    public void onClick(@NonNull View view) {
        ApplicationBean app = (ApplicationBean) view.getTag();
        Switch sw = view.findViewById(R.id.isSelectedSwitch);
        sw.setChecked(!sw.isChecked());

        if (sw.isChecked()) {
            if (!selectedApps.contains(app)) {
                selectedApps.add(app);
                app.setSelected(true);
            }
        } else {
            selectedApps.remove(app);
            app.setSelected(false);
        }

        //update the total size text field in SelectionFragment
        TextView totSize = ((Activity) context).findViewById(R.id.totSizeTextView);
        totSize.setText(String.format(Locale.getDefault(), context.getString(R.string.total_size),
                getTotalSize()));
    }

    /**
     * The view that the user sees for each app/port available for selection.
     */
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
