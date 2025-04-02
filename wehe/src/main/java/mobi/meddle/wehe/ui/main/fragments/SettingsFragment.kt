package mobi.meddle.wehe.ui.main.fragments

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.google.android.material.navigation.NavigationView
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.ui.main.MainActivity
import java.util.Locale
import java.util.Objects

/**
 * @author Alankrit Joshi, Derek Ng
 * Settings Fragment controls the app settings and uses most of the elements from Default Shared
 * Preferences
 * Settings item in navigation bar (menu.drawer_view.xml)
 * XML layout: xml.preferences.xml
 */
@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        //attach fragment to xml.preferences
        setPreferencesFromResource(R.xml.preferences, rootKey)
        setCurrentPreferences()
        attachListeners()
    }

    /**
     * Set the summary labels to display the current values of the server, area, and ks2p when
     * loading page or when using defaults.
     */
    private fun setCurrentPreferences() {
        val serverPref =
            checkNotNull(preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_server_key)))
        serverPref.summary =
            String.format(getString(R.string.pref_cur_server), serverPref.value)

        val areaPref =
            checkNotNull(preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_area_key)))
        areaPref.summary = String.format(
            getString(R.string.pref_cur_percent),
            areaPref.text!!.toInt()
        )

        val ks2pPref =
            checkNotNull(preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_ks2p_key)))
        ks2pPref.summary = String.format(
            getString(R.string.pref_cur_percent),
            ks2pPref.text!!.toInt()
        )
    }

    private fun attachListeners() {
        //determines if input is valid number between 0 and 100, used for area and ks2p
        val numberListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                try {
                    val newNumber = newValue.toString().toInt(10)
                    if (newNumber >= 0 && newNumber <= 100) {
                        preference.summary = String.format(
                            getString(R.string.pref_cur_percent),
                            newNumber
                        )
                        return@OnPreferenceChangeListener true
                    }
                } catch (ignored: NumberFormatException) {
                }
                Toast.makeText(context, getString(R.string.inval_percent), Toast.LENGTH_LONG).show()
                false
            }

        //set custom server
        val servPref = checkNotNull(
            preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_server_key))
        )
        val changeCurrentText =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                val index = servPref.findIndexOfValue(newValue.toString())
                if (index != servPref.entries.size - 1) { //not custom server
                    preference.summary = String.format(
                        getString(R.string.pref_cur_server),
                        newValue.toString()
                    )
                    return@OnPreferenceChangeListener true
                }

                /*custom server*/
                //custom server text box
                val customServer = EditText(context)
                customServer.setText(R.string.cust_server)
                customServer.hint = ""

                //get previous server if user cancels typing in new server
                val oldServer = preference.sharedPreferences!!.getString(
                    getString(R.string.pref_server_key), Consts.DEFAULT_SERVER
                )

                //dialogue to popup to let user type in new server
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.cust_server))
                    .setView(customServer)
                    .setPositiveButton(
                        getString(android.R.string.ok)
                    ) { dialog, which -> //set new server
                        val customServerText =
                            customServer.text.toString().lowercase(Locale.getDefault())
                        if (customServerText.matches("[a-z0-9.-]+".toRegex())) {
                            servPref.value = customServerText
                            preference.summary = String.format(
                                getString(R.string.pref_cur_server),
                                customServerText
                            )
                        } else {
                            Toast.makeText(
                                context, getString(R.string.inval_server),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .setNegativeButton(
                        getString(android.R.string.cancel)
                    ) { dialog, which -> //user cancels; set server to the previous server
                        servPref.value = oldServer
                        dialog.cancel()
                    }
                    .create().show()
                true
            }

        //use default settings switch
        val switchListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                val defaultSettings = newValue.toString().toBoolean()
                if (!defaultSettings) {
                    return@OnPreferenceChangeListener true
                }

                val serverPref =
                    preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_server_key))
                val areaPref =
                    preferenceScreen.findPreference<EditTextPreference>(
                        getString(R.string.pref_area_key)
                    )
                val ks2pPref =
                    preferenceScreen.findPreference<EditTextPreference>(
                        getString(R.string.pref_ks2p_key)
                    )

                checkNotNull(serverPref)
                serverPref.value = Consts.DEFAULT_SERVER
                checkNotNull(areaPref)
                areaPref.text = Consts.A_THRESHOLD.toString()
                checkNotNull(ks2pPref)
                ks2pPref.text = Consts.KS2PVAL_THRESHOLD.toString()

                setCurrentPreferences()
                true
            }

        //attach the listeners
        val areaPref = checkNotNull(
            preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_area_key))
        )
        areaPref.onPreferenceChangeListener = numberListener

        val ks2pPref = checkNotNull(
            preferenceScreen.findPreference<EditTextPreference>(getString(R.string.pref_ks2p_key))
        )
        ks2pPref.onPreferenceChangeListener = numberListener

        val serverPref = checkNotNull(
            preferenceScreen.findPreference<ListPreference>(getString(R.string.pref_server_key))
        )
        serverPref.onPreferenceChangeListener = changeCurrentText

        val defaultSwitch = checkNotNull(
            preferenceScreen.findPreference<SwitchPreference>(getString(R.string.pref_switch_key))
        )
        defaultSwitch.onPreferenceChangeListener = switchListener
    }

    override fun onResume() {
        super.onResume()
        if (activity is AppCompatActivity) {
            val actionBar = (activity as AppCompatActivity).supportActionBar
            if (actionBar != null) {
                actionBar.title = "Settings"
            }
        }

        // Update navigation drawer selection
        if (activity is MainActivity) {
            val mainActivity = activity as MainActivity?
            val navigationView = mainActivity!!.findViewById<NavigationView>(R.id.nav_view)
            if (navigationView != null) {
                // Clear all selections
                for (i in 0 until navigationView.menu.size()) {
                    val menuItem = navigationView.menu.getItem(i)
                    menuItem.setChecked(
                        Objects.requireNonNull(menuItem.title).toString() == "Settings"
                    )
                }
            }
        }
    }

    companion object {
        const val TAG: String = "SettingsFragment"
    }
}
