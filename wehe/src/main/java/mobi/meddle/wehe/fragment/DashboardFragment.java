package mobi.meddle.wehe.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import mobi.meddle.wehe.R;

public class DashboardFragment extends Fragment {

    public static final String TAG = "DashboardFragment";
    WebView dashboardView;
    View view;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        NavigationView navigationView = getActivity().findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_dashboard);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        getActivity().setTitle(menuItem.getTitle());
        if (view == null) {
            view = inflater.inflate(R.layout.fragment_dashboard, parent, false);
        }
        return view;
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        dashboardView = view.findViewById(R.id.dashboardView);
        dashboardView.getSettings().setJavaScriptEnabled(true);
        dashboardView.getSettings().setDomStorageEnabled(true);
        dashboardView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        String url = getString(R.string.dashboard_url);
        dashboardView.loadUrl(url);
    }
}

