//package mobi.meddle.wehe.ui.main.fragments
//
//import android.os.Bundle
//import android.view.View
//import android.widget.EditText
//import android.widget.LinearLayout
//import android.widget.Toast
//import androidx.appcompat.app.AlertDialog
//import androidx.appcompat.app.AppCompatActivity
//import androidx.fragment.app.viewModels
//import androidx.lifecycle.Observer
//import androidx.preference.EditTextPreference
//import androidx.preference.ListPreference
//import androidx.preference.Preference
//import androidx.preference.PreferenceFragmentCompat
//import androidx.preference.SwitchPreference
//import androidx.recyclerview.widget.RecyclerView
//import com.google.android.material.navigation.NavigationView
//import dagger.hilt.android.AndroidEntryPoint
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.ui.main.MainActivity
//import mobi.meddle.wehe.ui.main.viewmodels.SettingsViewModel
//import java.util.Objects
//
///**
// * Settings Fragment controls the app settings and uses most of the elements from Default Shared
// * Preferences
// * Settings item in navigation bar (menu.drawer_view.xml)
// * XML layout: xml.preferences.xml
// */
//@AndroidEntryPoint
//class SettingsFragment : PreferenceFragmentCompat() {
//    companion object {
//        const val TAG = "SettingsFragment"
//    }
//
//    // Use Hilt to inject the ViewModel
//    private val viewModel: SettingsViewModel by viewModels()
//
//    // UI reference objects
//    private lateinit var serverPref: ListPreference
//    private lateinit var areaPref: EditTextPreference
//    private lateinit var ks2pPref: EditTextPreference
//    private lateinit var defaultSwitch: SwitchPreference
//
//    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
//        // Attach fragment to xml.preferences
//        setPreferencesFromResource(R.xml.preferences, rootKey)
//
//        // Initialize UI references
//        initPreferenceReferences()
//
//        // Set up observers for the ViewModel data
//        setupObservers()
//
//        // Attach input listeners
//        attachListeners()
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        // Remove padding to use full width
//        view.findViewById<RecyclerView>(androidx.preference.R.id.recycler_view)?.apply {
//            setPadding(0, paddingTop, 0, paddingBottom)
//        }
//    }
//
//    private fun initPreferenceReferences() {
//        serverPref = findPreference(getString(R.string.pref_server_key))!!
//        areaPref = findPreference(getString(R.string.pref_area_key))!!
//        ks2pPref = findPreference(getString(R.string.pref_ks2p_key))!!
//        defaultSwitch = findPreference(getString(R.string.pref_switch_key))!!
//    }
//
//    private fun setupObservers() {
//        /// Observe changes to settings from the ViewModel
//        viewModel.serverAddress.observe(this, Observer { server ->
//            // Only update if the current value is different to prevent loops
//            if (serverPref.value != server) {
//                serverPref.value = server
//                serverPref.summary = String.format(getString(R.string.pref_cur_server), server)
//            }
//        })
//
//        viewModel.areaThreshold.observe(this, Observer { area ->
//            areaPref.text = area.toString()
//            areaPref.summary = String.format(getString(R.string.pref_cur_percent), area)
//        })
//
//        viewModel.ks2pThreshold.observe(this, Observer { ks2p ->
//            ks2pPref.text = ks2p.toString()
//            ks2pPref.summary = String.format(getString(R.string.pref_cur_percent), ks2p)
//        })
//
//        viewModel.usingDefaultSettings.observe(this, Observer { isDefault ->
//            defaultSwitch.isChecked = isDefault
//        })
//    }
//
//    private fun attachListeners() {
//        // Area and KS2P validation listener
//        val numberListener = Preference.OnPreferenceChangeListener { preference, newValue ->
//            viewModel.validatePercentageInput(newValue.toString()).also { isValid ->
//                if (!isValid) {
//                    Toast.makeText(context, getString(R.string.inval_percent), Toast.LENGTH_LONG).show()
//                }
//                return@OnPreferenceChangeListener isValid
//            }
//        }
//
//        // Server change listener
//        serverPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { preference, newValue ->
//            val index = serverPref.findIndexOfValue(newValue.toString())
//
//            if (index != serverPref.entries.size - 1) {
//                // Regular server selected (not custom)
//                viewModel.updateServerAddress(newValue.toString())
//                return@OnPreferenceChangeListener true
//            } else {
//                // Custom server selected - show dialog
//                showCustomServerDialog()
//                return@OnPreferenceChangeListener false
//            }
//        }
//
//        // Attach number validation listeners
//        areaPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
//            if (numberListener.onPreferenceChange(areaPref, newValue)) {
//                viewModel.updateAreaThreshold(newValue.toString().toInt())
//                return@OnPreferenceChangeListener true
//            }
//            return@OnPreferenceChangeListener false
//        }
//
//        ks2pPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
//            if (numberListener.onPreferenceChange(ks2pPref, newValue)) {
//                viewModel.updateKs2pThreshold(newValue.toString().toInt())
//                return@OnPreferenceChangeListener true
//            }
//            return@OnPreferenceChangeListener false
//        }
//
//        // Default settings switch listener
//        defaultSwitch.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
//            val useDefaultSettings = newValue.toString().toBoolean()
//            if (useDefaultSettings) {
//                viewModel.resetToDefaultSettings()
//            }
//            return@OnPreferenceChangeListener true
//        }
//    }
//
//    private fun showCustomServerDialog() {
//        // Create custom server input field
//        val customServer = EditText(context)
//        customServer.setText(R.string.cust_server)
//        customServer.hint = ""
//
//        // Create a container with padding
//        val container = LinearLayout(requireContext()).apply {
//            orientation = LinearLayout.VERTICAL
//            setPadding(32, 16, 32, 16)
//            addView(customServer)
//        }
//
//        // Store previous server value
//        val oldServer = viewModel.serverAddress.value
//
//        // Show dialog for custom server input
//        AlertDialog.Builder(requireContext())
//            .setTitle(getString(R.string.cust_server))
//            .setView(container)
//            .setPositiveButton(getString(android.R.string.ok)) { _, _ ->
//                val customServerText = customServer.text.toString().toLowerCase()
//                if (viewModel.validateServerAddress(customServerText)) {
//                    viewModel.updateServerAddress(customServerText)
//                } else {
//                    Toast.makeText(context, getString(R.string.inval_server), Toast.LENGTH_LONG).show()
//                    // Revert to previous server if validation fails
//                    oldServer?.let { viewModel.updateServerAddress(it) }
//                }
//            }
//            .setNegativeButton(getString(android.R.string.cancel)) { dialog, _ ->
//                // User cancels; revert to previous server
//                oldServer?.let { viewModel.updateServerAddress(it) }
//                dialog.cancel()
//            }
//            .create()
//            .show()
//    }
//
//    override fun onResume() {
//        super.onResume()
//
//        // Update action bar title
//        updateActionBarTitle()
//
//        // Update navigation drawer selection
//        updateNavigationDrawerSelection()
//
//        // Load current settings from preferences
//        viewModel.loadCurrentSettings()
//    }
//
//    private fun updateActionBarTitle() {
//        if (activity is AppCompatActivity) {
//            val actionBar = (activity as AppCompatActivity).supportActionBar
//            actionBar?.title = "Settings"
//        }
//    }
//
//    private fun updateNavigationDrawerSelection() {
//        if (activity is MainActivity) {
//            val mainActivity = activity as MainActivity
//            val navigationView = mainActivity.findViewById<NavigationView>(R.id.nav_view)
//            navigationView?.let { navView ->
//                // Clear all selections
//                for (i in 0 until navView.menu.size()) {
//                    val menuItem = navView.menu.getItem(i)
//                    menuItem.isChecked = Objects.requireNonNull(menuItem.title).toString() == "Settings"
//                }
//            }
//        }
//    }
//}