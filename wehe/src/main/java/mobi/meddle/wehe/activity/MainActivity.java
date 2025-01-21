package mobi.meddle.wehe.activity;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.constant.Consts;

public class MainActivity extends AppCompatActivity {
    private final int locationRequestCode = 1093;
    private DrawerLayout mDrawer;
    private Toolbar mToolbar;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupViews();
        setupNavigation();
        checkConsent();
    }

    private void setupViews() {
        mDrawer = findViewById(R.id.drawer_layout);
        mNavigationView = findViewById(R.id.nav_view);
        mToolbar = findViewById(R.id.main_app_bar);
        setSupportActionBar(mToolbar);
    }

    private void setupNavigation() {
        // Get NavController
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        assert navHostFragment != null;
        navController = navHostFragment.getNavController();

        // Setup AppBarConfiguration
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.selectionFragment, R.id.resultsFragment, R.id.aboutFragment,
                R.id.functionalityFragment, R.id.dashboardFragment, R.id.settingsFragment)
                .setOpenableLayout(mDrawer)
                .build();

        // Setup Toolbar with NavController
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

        // Setup NavigationView with NavController
        NavigationUI.setupWithNavController(mNavigationView, navController);

        // Handle navigation for Why Wehe selection
        mNavigationView.getMenu().findItem(R.id.nav_about).setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.aboutFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for differentiation test selection
        mNavigationView.getMenu().findItem(R.id.nav_run).setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            args.putBoolean("runPortTest", false);
            args.putString("TAG", Consts.TAG_DIFFERENTIATION_TESTS);
            navController.navigate(R.id.selectionFragment, args);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for port test selection
        mNavigationView.getMenu().findItem(R.id.nav_run_port).setOnMenuItemClickListener(item -> {
            Bundle args = new Bundle();
            args.putBoolean("runPortTest", true);
            args.putString("TAG", Consts.TAG_PORT_TESTS);
            navController.navigate(R.id.selectionFragment, args);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for previous results selection
        mNavigationView.getMenu().findItem(R.id.nav_results).setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.resultsFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for settings selection
        mNavigationView.getMenu().findItem(R.id.nav_settings).setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.settingsFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for how it works selection
        mNavigationView.getMenu().findItem(R.id.nav_functionality).setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.functionalityFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for online dashboard selection
        mNavigationView.getMenu().findItem(R.id.nav_dashboard).setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.dashboardFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void checkConsent() {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean userAgreed = sharedPrefs.getBoolean("userAgreedNov2018Consent", false);

        if (!userAgreed) {
            consentDialog();
        }
    }

    private void consentDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.consent_form_title)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = sharedPrefs.edit();
                    editor.putBoolean("userAgreedNov2018Consent", true);
                    editor.apply();

                    requestLocationPermission();
                })
                .setNegativeButton(R.string.decline, (dialog, which) -> {
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = sharedPrefs.edit();
                    editor.putBoolean("userAgreedNov2018Consent", false);
                    editor.apply();
                    finish();
                })
                .setMessage(Html.fromHtml(getString(R.string.consent_form)))
                .show();
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.dialog_permission_title)
                    .setMessage(R.string.permission_explaination)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            requestPermissions(
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    locationRequestCode
                            ))
                    .show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
