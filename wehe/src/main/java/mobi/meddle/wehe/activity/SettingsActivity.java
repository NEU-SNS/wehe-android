package mobi.meddle.wehe.activity;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import mobi.meddle.wehe.R;

/**
 * @authors: Alankrit Joshi,
 * Settings Activity controls the app settings and uses most of the elements from Default Shared
 * Preferences
 */
public class SettingsActivity extends AppCompatActivity {

    // Toolbar data type to set mToolbar to show the title of the activity
    Toolbar toolbar;

    /**
     * @param savedInstanceState
     * @authors: Alankrit Joshi,
     * On creation of the instance, following things happen:
     * 1. Binds content view with 'layout/preferences_layout.xml'
     * 2. Initializes mToolbar
     * 3. Injects Preference Fragment class defined below
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Bind content view with 'layout/preferences_layout.xml'
        setContentView(R.layout.preferences_layout);
        // Initialize mToolbar using the element in the layout
        toolbar = findViewById(R.id.settings_bar);
        // Enable support action bar using the mToolbar
        setSupportActionBar(toolbar);
        // If support action bar was enabled properly, set title and enable navigation
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_page_title));
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        // Inject fragment class into the element 'fragment_container' of the layout
        getFragmentManager().beginTransaction().replace(R.id.fragment_container, new GeneralPreferenceFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                SettingsActivity.this.finish();
                SettingsActivity.this.overridePendingTransition(
                        android.R.anim.slide_in_left,
                        android.R.anim.slide_out_right);
                break;
        }
        return true;
    }

    /**
     * @authors: Alankrit Joshi,
     * General preference fragment to add general preferences stored in 'xml/preferences.xml
     */
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        /**
         * @param savedInstanceState On creation of the fragment, add preferences from 'xml/preferences.xml
         * @authors: Alankrit Joshi,
         */
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            addPreferencesFromResource(R.xml.preferences);
            attachListeners();
        }

        private void attachListeners() {
            Preference.OnPreferenceChangeListener numberListener = new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    try {
                        int newNumber = Integer.parseInt(newValue.toString(), 10);
                        return !(newNumber > 100 || newNumber < 0);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            };

            Preference.OnPreferenceChangeListener switchListener = new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean defaultSettings = Boolean.parseBoolean(newValue.toString());
                    if (!defaultSettings) {
                        return true;
                    }

                    EditTextPreference serverPref = (EditTextPreference)
                            getPreferenceScreen().findPreference("pref_server");
                    EditTextPreference areaPref = (EditTextPreference)
                            getPreferenceScreen().findPreference("pref_threshold_area");
                    EditTextPreference ks2pPref = (EditTextPreference)
                            getPreferenceScreen().findPreference("pref_threshold_ks2p");

                    serverPref.setText("wehe2.meddle.mobi");
                    areaPref.setText("10");
                    ks2pPref.setText("5");

                    return true;
                }
            };

            EditTextPreference areaPref = (EditTextPreference)
                    getPreferenceScreen().findPreference("pref_threshold_area");
            areaPref.setOnPreferenceChangeListener(numberListener);

            EditTextPreference ks2pPref = (EditTextPreference)
                    getPreferenceScreen().findPreference("pref_threshold_ks2p");
            ks2pPref.setOnPreferenceChangeListener(numberListener);

            SwitchPreference defaultSwitch = (SwitchPreference)
                    getPreferenceScreen().findPreference("pref_switch");
            defaultSwitch.setOnPreferenceChangeListener(switchListener);


        }
    }
}
