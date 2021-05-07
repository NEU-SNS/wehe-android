package mobi.meddle.wehe.fragment;

import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import mobi.meddle.wehe.R;
import mobi.meddle.wehe.constant.Consts;

/**
 * @author Alankrit Joshi, Derek Ng
 * Settings Fragment controls the app settings and uses most of the elements from Default Shared
 * Preferences
 * Settings item in navigation bar (menu.drawer_view.xml)
 * XML layout: xml.preferences.xml
 */
public class SettingsFragment extends PreferenceFragmentCompat {
    public static final String TAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        //attach fragment to xml.preferences
        setPreferencesFromResource(R.xml.preferences, rootKey);
        setCurrentPreferences();
        attachListeners();
    }

    /**
     * Set the summary labels to display the current values of the server, area, and ks2p when
     * loading page or when using defaults.
     */
    private void setCurrentPreferences() {
        ListPreference serverPref
                = getPreferenceScreen().findPreference(getString(R.string.pref_server_key));
        assert serverPref != null;
        serverPref.setSummary(String.format(getString(R.string.pref_cur_server), serverPref.getValue()));

        EditTextPreference areaPref
                = getPreferenceScreen().findPreference(getString(R.string.pref_area_key));
        assert areaPref != null;
        areaPref.setSummary(String.format(getString(R.string.pref_cur_percent),
                Integer.parseInt(areaPref.getText())));

        EditTextPreference ks2pPref
                = getPreferenceScreen().findPreference(getString(R.string.pref_ks2p_key));
        assert ks2pPref != null;
        ks2pPref.setSummary(String.format(getString(R.string.pref_cur_percent),
                Integer.parseInt(ks2pPref.getText())));
    }

    private void attachListeners() {
        //determines if input is valid number between 0 and 100, used for area and ks2p
        OnPreferenceChangeListener numberListener = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, @NonNull Object newValue) {
                try {
                    int newNumber = Integer.parseInt(newValue.toString(), 10);
                    if (newNumber >= 0 && newNumber <= 100) {
                        preference.setSummary(String.format(getString(R.string.pref_cur_percent),
                                newNumber));
                        return true;
                    }
                } catch (NumberFormatException ignored) {

                }
                Toast.makeText(getContext(), getString(R.string.inval_percent), Toast.LENGTH_LONG).show();
                return false;
            }
        };

        //set custom server
        ListPreference servPref =
                getPreferenceScreen().findPreference(getString(R.string.pref_server_key));
        assert servPref != null;
        OnPreferenceChangeListener changeCurrentText = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, @NonNull Object newValue) {
                int index = servPref.findIndexOfValue(newValue.toString());
                if (index != servPref.getEntries().length - 1) { //not custom server
                    preference.setSummary(String.format(getString(R.string.pref_cur_server),
                            newValue.toString()));
                    return true;
                }

                /*custom server*/
                //custom server text box
                EditText customServer = new EditText(getContext());
                customServer.setText(R.string.cust_server);
                customServer.setHint("");

                //get previous server if user cancels typing in new server
                String oldServer = preference.getSharedPreferences().getString(
                        getString(R.string.pref_server_key), Consts.DEFAULT_SERVER);

                //dialogue to popup to let user type in new server
                new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.cust_server))
                        .setView(customServer)
                        .setPositiveButton(getString(android.R.string.ok),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //set new server
                                        String customServerText = customServer.getText().toString().toLowerCase();
                                        if (customServerText.matches("[a-z0-9.-]+")) {
                                            servPref.setValue(customServerText);
                                            preference.setSummary(String.format(getString(R.string.pref_cur_server),
                                                    customServerText));
                                        } else {
                                            Toast.makeText(getContext(), getString(R.string.inval_server),
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    }
                                })
                        .setNegativeButton(getString(android.R.string.cancel),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        //user cancels; set server to the previous server
                                        servPref.setValue(oldServer);
                                        dialog.cancel();
                                    }
                                })
                        .create().show();

                return true;
            }
        };

        //use default settings switch
        OnPreferenceChangeListener switchListener = new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, @NonNull Object newValue) {
                boolean defaultSettings = Boolean.parseBoolean(newValue.toString());
                if (!defaultSettings) {
                    return true;
                }

                ListPreference serverPref =
                        getPreferenceScreen().findPreference(getString(R.string.pref_server_key));
                EditTextPreference areaPref =
                        getPreferenceScreen().findPreference(getString(R.string.pref_area_key));
                EditTextPreference ks2pPref =
                        getPreferenceScreen().findPreference(getString(R.string.pref_ks2p_key));

                assert serverPref != null;
                serverPref.setValue(Consts.DEFAULT_SERVER);
                assert areaPref != null;
                areaPref.setText(String.valueOf(Consts.A_THRESHOLD));
                assert ks2pPref != null;
                ks2pPref.setText(String.valueOf(Consts.KS2PVAL_THRESHOLD));

                setCurrentPreferences();

                return true;
            }
        };

        //attach the listeners
        EditTextPreference areaPref =
                getPreferenceScreen().findPreference(getString(R.string.pref_area_key));
        assert areaPref != null;
        areaPref.setOnPreferenceChangeListener(numberListener);

        EditTextPreference ks2pPref =
                getPreferenceScreen().findPreference(getString(R.string.pref_ks2p_key));
        assert ks2pPref != null;
        ks2pPref.setOnPreferenceChangeListener(numberListener);

        ListPreference serverPref =
                getPreferenceScreen().findPreference(getString(R.string.pref_server_key));
        assert serverPref != null;
        serverPref.setOnPreferenceChangeListener(changeCurrentText);

        SwitchPreference defaultSwitch =
                getPreferenceScreen().findPreference(getString(R.string.pref_switch_key));
        assert defaultSwitch != null;
        defaultSwitch.setOnPreferenceChangeListener(switchListener);
    }
}
