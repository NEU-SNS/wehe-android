package mobi.meddle.wehe.ui.main.fragments

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import dagger.hilt.android.AndroidEntryPoint
import mobi.meddle.wehe.R
import mobi.meddle.wehe.constant.Consts
import java.util.Locale

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
        // Set the preference file name
        preferenceManager.sharedPreferencesName = getString(R.string.preference_file_key)

        // Load the preferences from XML
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // Initialize the current preferences display
        setCurrentPreferences()
        attachListeners()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        // Ensure the list view background uses theme colors
        listView?.setBackgroundColor(requireContext().getColorFromAttr(android.R.attr.colorBackground))

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ensure fragment background uses theme colors
        view.setBackgroundColor(requireContext().getColorFromAttr(android.R.attr.colorBackground))
    }

    // Extension function to get color from theme attribute
    private fun Context.getColorFromAttr(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    // Extension function to check if dark mode is enabled
    private fun Context.isDarkModeEnabled(): Boolean {
        return resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
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

        // Set dialog theme for EditTextPreference dialogs
        areaPref.setDialogLayoutResource(R.layout.preference_dialog_edittext)
        ks2pPref.setDialogLayoutResource(R.layout.preference_dialog_edittext)
    }

    private fun attachListeners() {
        //determines if input is valid number between 0 and 100, used for area and ks2p
        val numberListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                try {
                    val newNumber = newValue.toString().toInt(10)
                    if (newNumber in 0..100) {
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

//                /*custom server*/
//
//                // Create themed context wrapper for dialog
//                val dialogContext = ContextThemeWrapper(requireContext(), R.style.AlertDialogTheme)
//
//                // Create header TextView with theme-aware styling
//                val headerView = TextView(dialogContext)
//                headerView.text = getString(R.string.custom_server_text)
//                headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
//                headerView.setPadding(30, 30, 30, 10)
//
//                // Get theme-aware text color
//                headerView.setTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorPrimary))
//
//                // Custom server text box with theme-aware styling
//                val customServer = EditText(dialogContext)
//                customServer.setText("")
//                customServer.setTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorPrimary))
//                customServer.setHintTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorHint))
//                customServer.setPadding(30, 30, 30, 20)
//                customServer.hint = ""
//
                // Get previous server if user cancels typing in new server
                val oldServer = preference.sharedPreferences!!.getString(
                    getString(R.string.pref_server_key), Consts.DEFAULT_SERVER
                )
//
//                // Create a LinearLayout to hold both views with theme-aware background
//                val container = LinearLayout(dialogContext)
//                container.orientation = LinearLayout.VERTICAL
//                container.setBackgroundColor(dialogContext.getColorFromAttr(android.R.attr.colorBackground))
//                container.addView(headerView)
//                container.addView(customServer)

                // Create themed context wrapper for dialog
                val dialogContext = requireContext()

// Create header TextView with theme-aware styling
                val headerView = TextView(dialogContext)
                headerView.text = getString(R.string.custom_server_text)
                headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                headerView.setPadding(30, 30, 30, 10)
                headerView.setTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorPrimary))

// Custom server text box with theme-aware styling
                val customServer = EditText(dialogContext)
                customServer.setText("")
                customServer.setTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorPrimary))
                customServer.setHintTextColor(dialogContext.getColorFromAttr(android.R.attr.textColorHint))
// Also set background tint to ensure it's visible in all themes
                customServer.setPadding(30, 30, 30, 20)
                customServer.hint = getString(R.string.custom_server_hint) // Add a hint string resource if you don't have one

                val dialogView = layoutInflater.inflate(R.layout.dialog_custom_server, null)
                // Create dialog with appropriate styling
                AlertDialog.Builder(dialogContext)
                    .setView(dialogView)
                    .setPositiveButton(
                        getString(android.R.string.ok)
                    ) { _, _ -> //set new server
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
                    ) { dialog, _ -> //user cancels; set server to the previous server
                        servPref.value = oldServer
                        dialog.cancel()
                    }
                    .create().show()
                true
            }

        //use default settings switch
        val switchListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
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
}