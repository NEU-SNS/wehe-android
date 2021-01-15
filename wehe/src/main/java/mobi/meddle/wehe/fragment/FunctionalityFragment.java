package mobi.meddle.wehe.fragment;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import mobi.meddle.wehe.R;

/**
 * How it Works item in navigation bar (menu.drawer_view.xml)
 * XML layout: fragment_functionality.xml
 */
public class FunctionalityFragment extends Fragment {
    public static final String TAG = "FunctionalityFragment";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent,
                             Bundle savedInstanceState) {
        NavigationView navigationView = requireActivity().findViewById(R.id.nav_view);
        MenuItem menuItem = navigationView.getMenu().findItem(R.id.nav_functionality);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        requireActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_functionality, parent, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        TextView textView = view.findViewById(R.id.functionalityView);
        textView.setMovementMethod(new ScrollingMovementMethod());
    }
}
