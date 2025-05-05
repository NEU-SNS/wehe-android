package mobi.meddle.wehe.ui.main;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
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
import android.text.method.LinkMovementMethod;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;

import dagger.hilt.android.AndroidEntryPoint;
import mobi.meddle.wehe.R;
import mobi.meddle.wehe.constant.Consts;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {
    private final int locationRequestCode = 1093;
    private DrawerLayout mDrawer;
    private NavigationView mNavigationView;
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
        Toolbar mToolbar = findViewById(R.id.main_app_bar);
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

        // Disable item highlighting
        Menu menu = mNavigationView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setCheckable(false); // This ensures it's never "selected"
        }

        // Handle navigation for Why Wehe selection
        mNavigationView.getMenu().findItem(R.id.nav_about).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            navController.navigate(R.id.aboutFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for differentiation test selection
        mNavigationView.getMenu().findItem(R.id.nav_run).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            Bundle args = new Bundle();
            args.putBoolean("runPortTest", false);
            args.putString("TAG", Consts.TAG_DIFFERENTIATION_TESTS);
            navController.navigate(R.id.selectionFragment, args);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for port test selection
        mNavigationView.getMenu().findItem(R.id.nav_run_port).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            Bundle args = new Bundle();
            args.putBoolean("runPortTest", true);
            args.putString("TAG", Consts.TAG_PORT_TESTS);
            navController.navigate(R.id.selectionFragment, args);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for previous results selection
        mNavigationView.getMenu().findItem(R.id.nav_results).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            navController.navigate(R.id.resultsFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for settings selection
        mNavigationView.getMenu().findItem(R.id.nav_settings).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            navController.navigate(R.id.settingsFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for how it works selection
        mNavigationView.getMenu().findItem(R.id.nav_functionality).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
            navController.navigate(R.id.functionalityFragment);
            mDrawer.closeDrawer(GravityCompat.START);
            return true;
        });

        // Handle navigation for online dashboard selection
        mNavigationView.getMenu().findItem(R.id.nav_dashboard).setOnMenuItemClickListener(item -> {
            item.setChecked(false);
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
        // Create the fixed header text
        TextView headerView = new TextView(this);
        headerView.setText(R.string.consent_form_title);
        headerView.setPadding(32, 32, 32, 16);
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        headerView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));

// Create the scrollable content
        TextView messageView = new TextView(this);
        messageView.setText(Html.fromHtml(getString(R.string.consent_form), Html.FROM_HTML_MODE_LEGACY));
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        messageView.setPadding(32, 16, 32, 16);

// Add the scrollable TextView to a ScrollView
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(messageView);

// Create a LinearLayout to hold both views
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(headerView);
        container.addView(scrollView);

        new AlertDialog.Builder(this)
                .setView(container)
                .setPositiveButton(R.string.accept, (dialog, which) -> {
                    SharedPreferences sharedPrefs =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = sharedPrefs.edit();
                    editor.putBoolean("userAgreedNov2018Consent", true);
                    editor.apply();

                    requestLocationPermission();
                })
                .setNegativeButton(R.string.decline, (dialog, which) -> {
                    SharedPreferences sharedPreferences =
                            PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putBoolean("userAgreedNov2018Consent", false);
                    editor.apply();
                    finish();
                })
                .show();
    }

    private void requestLocationPermission() {

        // Create the fixed header text
        TextView headerView = new TextView(this);
        headerView.setText(R.string.dialog_permission_title);
        headerView.setPadding(32, 32, 32, 16);
        headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        headerView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));

        // Create a TextView for the message with proper styling
        TextView messageView = new TextView(this);
        messageView.setText(getString(R.string.permission_explaination));
        messageView.setPadding(32, 16, 32, 16); // Add some padding

        // Set text size explicitly
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        // Ensure text color contrasts with background
        messageView.setTextColor(getResources().getColor(android.R.color.black, getTheme()));

        // Use a ScrollView to handle potentially long content
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(messageView);

        // Create a LinearLayout to hold both views
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(headerView);
        container.addView(scrollView);

        new AlertDialog.Builder(this)
                .setView(container) // Set the scrollable view instead of message
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        requestPermissions(
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                locationRequestCode
                        ))
                .show();
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
