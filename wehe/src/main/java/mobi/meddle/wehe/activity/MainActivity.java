package mobi.meddle.wehe.activity;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.fragment.AboutFragment;
import mobi.meddle.wehe.fragment.DashboardFragment;
import mobi.meddle.wehe.fragment.FunctionalityFragment;
import mobi.meddle.wehe.fragment.ResultsFragment;
import mobi.meddle.wehe.fragment.SelectionFragment;

public class MainActivity extends AppCompatActivity {

    private final int locationRequestCode = 1093;
    private DrawerLayout mDrawer;
    private Toolbar mToolbar;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private FragmentManager mFragmentManager;

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
        } else {
            if (savedInstanceState == null) {
                Fragment fragment = new SelectionFragment();
                // We're adding fragments to the backstack as we navigate otherwise back button will
                // Land you out of the application
                // TODO find a better way to navigate using back button currently it keeps
                //  adding the fragments even though another instance of the same frament might
                //  exist in the backstack
                mFragmentManager.beginTransaction().add(fragment, SelectionFragment.TAG).commit();
                mFragmentManager.beginTransaction().replace(R.id.content_frame, fragment)
                        .addToBackStack(null)
                        .commit();
                setTitle(R.string.nav_run);
            }
        }
    }

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
                                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            MainActivity.this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                                    locationRequestCode
                                            );
                                        }
                                    })
                                    .setMessage(
                                            R.string.permission_explaination)
                                    .show();
                        }

                        Fragment fragment = new SelectionFragment();
                        mFragmentManager.beginTransaction().add(fragment, SelectionFragment.TAG).commit();
                        mFragmentManager.beginTransaction().replace(R.id.content_frame, fragment)
                                .addToBackStack(null)
                                .commit();
                        setTitle(R.string.nav_run);
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
                .setMessage(Html.fromHtml(getString(
                        R.string.consent_form)))
                // Display the dialog
                .show();
    }

    protected void onCreateDrawer() {
        mNavigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
                        int id = menuItem.getItemId();
                        Fragment fragment = null;

                        switch (id) {
                            case R.id.nav_run:
                                fragment = mFragmentManager.findFragmentByTag(SelectionFragment.TAG);
                                if (fragment == null) {
                                    fragment = new SelectionFragment();
                                    mFragmentManager.beginTransaction().add(fragment, SelectionFragment.TAG).commit();
                                }
                                break;
                            case R.id.nav_results:
                                fragment = mFragmentManager.findFragmentByTag(ResultsFragment.TAG);
                                if (fragment == null) {
                                    fragment = new ResultsFragment();
                                    mFragmentManager.beginTransaction().add(fragment, ResultsFragment.TAG).commit();
                                }
                                break;
                            case R.id.nav_about:
                                fragment = mFragmentManager.findFragmentByTag(AboutFragment.TAG);
                                if (fragment == null) {
                                    fragment = new AboutFragment();
                                    mFragmentManager.beginTransaction().add(fragment, AboutFragment.TAG).commit();
                                }
                                break;
                            case R.id.nav_functionality:
                                fragment = mFragmentManager.findFragmentByTag(FunctionalityFragment.TAG);
                                if (fragment == null) {
                                    fragment = new FunctionalityFragment();
                                    mFragmentManager.beginTransaction().add(fragment, FunctionalityFragment.TAG).commit();
                                }
                                break;
                            case R.id.nav_dashboard:
                                fragment = mFragmentManager.findFragmentByTag(DashboardFragment.TAG);
                                if (fragment == null) {
                                    fragment = new DashboardFragment();
                                    mFragmentManager.beginTransaction().add(fragment, DashboardFragment.TAG).commit();
                                }
                                break;
                        }

                        // Insert the fragment by replacing any existing fragment
                        // TODO tested this as mentioned above needs improvement
                        mFragmentManager.beginTransaction().replace(R.id.content_frame, fragment)
                                .addToBackStack(null)
                                .commit();

                        // Highlight the selected item has been done by NavigationView
                        menuItem.setChecked(true);
                        // Set action bar title
                        setTitle(menuItem.getTitle());
                        // Close the navigation drawer
                        // close drawer when item is tapped
                        mDrawer.closeDrawers();
                        return false;
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                MainActivity.this.startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private ActionBarDrawerToggle setupDrawerToggle() {
        return new ActionBarDrawerToggle(this, mDrawer, mToolbar, R.string.drawer_open, R.string.drawer_close);
    }
}