package mobi.meddle.wehe.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import mobi.meddle.wehe.BuildConfig;
import mobi.meddle.wehe.R;

/**
 * Why Wehe item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_about.xml
 */
public class AboutFragment extends Fragment {
    public static final String TAG = "AboutFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_about);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        requireActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_about, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        //set version number
        TextView about = requireActivity().findViewById(R.id.aboutView);
        about.setText(String.format(getString(R.string.about_text), BuildConfig.VERSION_NAME));
    }
}
