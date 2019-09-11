package mobi.meddle.wehe.fragment;

import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import mobi.meddle.wehe.R;

public class AboutFragment extends Fragment {

    public static final String TAG = "AboutFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        NavigationView navigationView = getActivity().findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_about);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        getActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_about, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

    }
}
