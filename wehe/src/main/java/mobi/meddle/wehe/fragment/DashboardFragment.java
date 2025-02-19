package mobi.meddle.wehe.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import java.util.Objects;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.activity.MainActivity;

/**
 * View Online Dashboard item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_dashboard.xml
 */
public class DashboardFragment extends Fragment {

    public static final String TAG = "DashboardFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_dashboard);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        requireActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_dashboard, parent, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        WebView dashboardView = view.findViewById(R.id.dashboardView);
        dashboardView.getSettings().setJavaScriptEnabled(true);
        dashboardView.getSettings().setDomStorageEnabled(true);
        dashboardView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        String url = getString(R.string.dashboard_url);
        dashboardView.loadUrl(url);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle("Online Dashboard");
            }
        }

        // Update navigation drawer selection
        if (getActivity() instanceof MainActivity) {
            MainActivity mainActivity = (MainActivity) getActivity();
            NavigationView navigationView = mainActivity.findViewById(R.id.nav_view);
            if (navigationView != null) {
                // Clear all selections
                for (int i = 0; i < navigationView.getMenu().size(); i++) {
                    MenuItem menuItem = navigationView.getMenu().getItem(i);
                    menuItem.setChecked(Objects.requireNonNull(menuItem.getTitle()).toString().equals("View Online Dashboard"));
                }
            }
        }
    }


}
