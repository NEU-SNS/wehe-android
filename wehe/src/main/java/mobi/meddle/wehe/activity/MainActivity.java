package mobi.meddle.wehe.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.constant.Consts;
import mobi.meddle.wehe.fragment.AboutFragment;
import mobi.meddle.wehe.fragment.DashboardFragment;
import mobi.meddle.wehe.fragment.FunctionalityFragment;
import mobi.meddle.wehe.fragment.ResultsFragment;
import mobi.meddle.wehe.fragment.SelectionFragment;
import mobi.meddle.wehe.fragment.SettingsFragment;

/**
 * Starting point for Wehe.
 * XML layout: activity_main.xml
 */
public class MainActivity extends AppCompatActivity {
    private final int locationRequestCode = 1093;
    private DrawerLayout mDrawer;
    private Toolbar mToolbar;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private FragmentManager mFragmentManager;

    //user opens the app
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mDrawer = findViewById(R.id.drawer_layout);
        mNavigationView = findViewById(R.id.nav_view);
        mToolbar = findViewById(R.id.main_app_bar);
        setSupportActionBar(mToolbar);
        onCreateDrawer();
        mDrawerToggle = setupDrawerToggle();
        mFragmentManager = getSupportFragmentManager();

        // Tie DrawerLayout events to the ActionBarToggle
        mDrawer.addDrawerListener(mDrawerToggle);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Get current value of pref: userAgreedNov2018Consent
        // Boolean data type to store the state of consent form agreement
        boolean userAgreed = sharedPrefs.getBoolean("userAgreedNov2018Consent", false);

        // If userAgree is false, then Consent dialog should be shown
        if (!userAgreed) {
            consentDialog();
        } else if (savedInstanceState == null) {
            goToAppSelection();
        }
    }

    /**
     * Go to the screen to select apps/ports to test
     */
    private void goToAppSelection() {
        Fragment fragment = new SelectionFragment();
        // We're adding fragments to the backstack as we navigate otherwise back button will
        // Land you out of the application
        // TODO find a better way to navigate using back button currently it keeps
        //  adding the fragments even though another instance of the same fragment might
        //  exist in the backstack
        Bundle bundle = new Bundle();
        bundle.putBoolean("runPortTest", false);
        bundle.putString("TAG", Consts.TAG_DIFFERENTIATION_TESTS );
        fragment.setArguments(bundle);
        mFragmentManager.beginTransaction().add(fragment, Consts.TAG_DIFFERENTIATION_TESTS).commit();
        mFragmentManager.beginTransaction().replace(R.id.content_frame, fragment, Consts.TAG_DIFFERENTIATION_TESTS)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
        setTitle(R.string.nav_run);
    }

    /**
     * Get user to agree to data collection. If they do not agree, the app exits.
     */
    private void consentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.consent_form_title)
                // OnClick listener for 'Agree' choice
                .setPositiveButton(R.string.accept, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Set useAgree to true
                        final SharedPreferences sharedPrefs =
                                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean("userAgreedNov2018Consent", true);
                        editor.apply();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            new android.app.AlertDialog.Builder(MainActivity.this)
                                    .setTitle(R.string.dialog_permission_title)
                                    .setPositiveButton(android.R.string.ok,
                                            new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog, int which) {
                                                    MainActivity.this.requestPermissions(
                                                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                                            locationRequestCode
                                                    );
                                                }
                                            })
                                    .setMessage(R.string.permission_explaination)
                                    .show();
                        }

                        goToAppSelection();
                    }
                })
                // OnClick listener for 'Disagree' choice
                .setNegativeButton(R.string.decline, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final SharedPreferences sharedPrefs =
                                PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        // Exit the application as the user chose not to give consent
                        SharedPreferences.Editor editor = sharedPrefs.edit();
                        editor.putBoolean("userAgreedNov2018Consent", false);
                        editor.apply();
                        finish();
                    }
                })
                /*
                Set the dialog content using the HTML resource available in
                'values/strings.xml'
                 */
                .setMessage(Html.fromHtml(getString(R.string.consent_form)))
                // Display the dialog
                .show();
    }

    /**
     * Main menu.
     */
    private void onCreateDrawer() {
        mNavigationView.setNavigationItemSelectedListener(new OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                Fragment fragment = null;

                if (id == R.id.nav_run) {
//                    fragment = new SelectionFragment();
                    fragment = mFragmentManager.findFragmentByTag(Consts.TAG_DIFFERENTIATION_TESTS);
                    assert fragment != null;
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("runPortTest", false);
                    bundle.putString("TAG", Consts.TAG_DIFFERENTIATION_TESTS);
                    fragment.setArguments(bundle);
                }
                else if (id == R.id.nav_run_port) {
//                    fragment = new SelectionFragment();
                    Bundle bundle = new Bundle();
                    bundle.putBoolean("runPortTest", true);
                    bundle.putString("TAG", Consts.TAG_PORT_TESTS);
                    fragment = mFragmentManager.findFragmentByTag(Consts.TAG_PORT_TESTS);
                    if (fragment == null) {
                        fragment = new SelectionFragment();
                        fragment.setArguments(bundle);
                        mFragmentManager.beginTransaction()
                                .add(fragment, Consts.TAG_PORT_TESTS).commit();
                    }
                    fragment.setArguments(bundle);
                } else if (id == R.id.nav_results) {
                    fragment = mFragmentManager.findFragmentByTag(ResultsFragment.TAG);
                    if (fragment == null) {
                        fragment = new ResultsFragment();
                        mFragmentManager.beginTransaction()
                                .add(fragment, ResultsFragment.TAG).commit();
                    }
                } else if (id == R.id.nav_about) {
                    fragment = mFragmentManager.findFragmentByTag(AboutFragment.TAG);
                    if (fragment == null) {
                        fragment = new AboutFragment();
                        mFragmentManager.beginTransaction()
                                .add(fragment, AboutFragment.TAG).commit();
                    }
                } else if (id == R.id.nav_functionality) {
                    fragment = mFragmentManager.findFragmentByTag(FunctionalityFragment.TAG);
                    if (fragment == null) {
                        fragment = new FunctionalityFragment();
                        mFragmentManager.beginTransaction()
                                .add(fragment, FunctionalityFragment.TAG).commit();
                    }
                } else if (id == R.id.nav_dashboard) {
                    fragment = mFragmentManager.findFragmentByTag(DashboardFragment.TAG);
                    if (fragment == null) {
                        fragment = new DashboardFragment();
                        mFragmentManager.beginTransaction()
                                .add(fragment, DashboardFragment.TAG).commit();
                    }
                } else if (id == R.id.nav_settings) {
                    fragment = mFragmentManager.findFragmentByTag(SettingsFragment.TAG);
                    if (fragment == null) {
                        fragment = new SettingsFragment();
                        mFragmentManager.beginTransaction()
                                .add(fragment, SettingsFragment.TAG).commit();
                    }
                }

                // Insert the fragment by replacing any existing fragment
                // TODO tested this as mentioned above needs improvement
                Fragment currentFragment = mFragmentManager.findFragmentById(R.id.content_frame);
                assert fragment != null;
                assert fragment.getTag() != null;
                assert currentFragment != null;
                assert currentFragment.getTag() != null;

                // Making sure that the same fragments are not added to the stack
                if (!currentFragment.getTag().equals(fragment.getTag())) {
                    mFragmentManager.beginTransaction().replace(R.id.content_frame, fragment, fragment.getTag())
                            .setReorderingAllowed(true)
                            .addToBackStack(null) // Darsh changed
                            .commit();
                }

//                //unhighlight all items so that previous item isn't highlighted
//                for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
//                    mNavigationView.getMenu().getItem(i).setChecked(false);
//                }
//                // Highlight the selected item has been done by NavigationView
//                menuItem.setChecked(true);
                // Set action bar title
//                setTitle(menuItem.getTitle());
                // Close the navigation drawer
                // close drawer when item is tapped
                mDrawer.closeDrawers();
                return false;
            }
        });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

//    @Override  // Darsh changed: added back navigation functionality
//    public void onBackPressed() {
//        int backStackCount = mFragmentManager.getBackStackEntryCount();
//
//        if (backStackCount > 0) {
//            // Get the fragment at the top of the back stack
//            FragmentManager.BackStackEntry entry = mFragmentManager.getBackStackEntryAt(backStackCount - 1);
//            String fragmentTag = entry.getName(); // Get the fragment tag
//
//            // find the fragment using its tag
//            Fragment currentFragment = mFragmentManager.findFragmentByTag(fragmentTag);
//
//            if (currentFragment != null) {
//                // You can perform additional actions depending on the fragment
//                // For example, check if it's a specific fragment
//                if (currentFragment instanceof AboutFragment) {
//                    // Handle back press for this fragment
//                    ((SomeFragment) currentFragment).onBackPressed();
//                }
//            }
//
//            super.onBackPressed();
//        }
//        else {
//            super.onBackPressedDispatcher();
//        }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, mDrawer, mToolbar,
                R.string.drawer_open, R.string.drawer_close);
    }
}
