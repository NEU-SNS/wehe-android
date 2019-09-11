package mobi.meddle.wehe.fragment;

import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import mobi.meddle.wehe.R;

public class FunctionalityFragment extends Fragment {

    public static final String TAG = "FunctionalityFragment";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        NavigationView navigationView = getActivity().findViewById(R.id.nav_view);
        Menu menu = navigationView.getMenu();
        MenuItem menuItem = menu.findItem(R.id.nav_functionality);
        if (!menuItem.isChecked()) {
            menuItem.setChecked(true);
        }
        getActivity().setTitle(menuItem.getTitle());
        return inflater.inflate(R.layout.fragment_functionality, parent, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        TextView textView = view.findViewById(R.id.functionalityView);
        textView.setMovementMethod(new ScrollingMovementMethod());
    }
}
