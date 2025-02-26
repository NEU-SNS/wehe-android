//// SettingsViewModel.kt
//package mobi.meddle.wehe.ui.main.viewmodels
//
//import android.app.Application
//import androidx.lifecycle.AndroidViewModel
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import androidx.preference.PreferenceManager
//import dagger.hilt.android.lifecycle.HiltViewModel
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.constant.Consts
//import javax.inject.Inject
//
///**
// * ViewModel for managing settings data and logic
// */
//@HiltViewModel
//class SettingsViewModel @Inject constructor(
//    private val application: Application
//) : AndroidViewModel(application) {
//
//    // LiveData for settings values
//    private val _serverAddress = MutableLiveData<String>()
//    val serverAddress: LiveData<String> = _serverAddress
//
//    private val _areaThreshold = MutableLiveData<Int>()
//    val areaThreshold: LiveData<Int> = _areaThreshold
//
//    private val _ks2pThreshold = MutableLiveData<Int>()
//    val ks2pThreshold: LiveData<Int> = _ks2pThreshold
//
//    private val _usingDefaultSettings = MutableLiveData<Boolean>()
//    val usingDefaultSettings: LiveData<Boolean> = _usingDefaultSettings
//
//    // Shared Preferences reference
//    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
//
//    /**
//     * Load current settings from SharedPreferences
//     */
//    fun loadCurrentSettings() {
//        val serverKey = application.getString(R.string.pref_server_key)
//        val areaKey = application.getString(R.string.pref_area_key)
//        val ks2pKey = application.getString(R.string.pref_ks2p_key)
//        val defaultSwitchKey = application.getString(R.string.pref_switch_key)
//
//        _serverAddress.value = sharedPreferences.getString(serverKey, Consts.DEFAULT_SERVER)
//        _areaThreshold.value = sharedPreferences.getString(areaKey, Consts.A_THRESHOLD.toString())?.toInt()
//        _ks2pThreshold.value = sharedPreferences.getString(ks2pKey, Consts.KS2PVAL_THRESHOLD.toString())?.toInt()
//        _usingDefaultSettings.value = sharedPreferences.getBoolean(defaultSwitchKey, false)
//    }
//
//    /**
//     * Validate percentage input (0-100)
//     */
//    fun validatePercentageInput(input: String): Boolean {
//        return try {
//            val value = input.toInt()
//            value in 0..100
//        } catch (e: NumberFormatException) {
//            false
//        }
//    }
//
//    /**
//     * Validate server address format
//     */
//    fun validateServerAddress(address: String): Boolean {
//        return address.matches("[a-z0-9.-]+".toRegex())
//    }
//
//    /**
//     * Update server address in preferences and LiveData
//     */
//    fun updateServerAddress(address: String) {
//        _serverAddress.value = address
//        sharedPreferences.edit()
//            .putString(application.getString(R.string.pref_server_key), address)
//            .apply()
//    }
//
//    /**
//     * Update area threshold in preferences and LiveData
//     */
//    fun updateAreaThreshold(threshold: Int) {
//        _areaThreshold.value = threshold
//        sharedPreferences.edit()
//            .putString(application.getString(R.string.pref_area_key), threshold.toString())
//            .apply()
//    }
//
//    /**
//     * Update KS2P threshold in preferences and LiveData
//     */
//    fun updateKs2pThreshold(threshold: Int) {
//        _ks2pThreshold.value = threshold
//        sharedPreferences.edit()
//            .putString(application.getString(R.string.pref_ks2p_key), threshold.toString())
//            .apply()
//    }
//
//    /**
//     * Reset all settings to default values
//     */
//    fun resetToDefaultSettings() {
//        updateServerAddress(Consts.DEFAULT_SERVER)
//        updateAreaThreshold(Consts.A_THRESHOLD)
//        updateKs2pThreshold(Consts.KS2PVAL_THRESHOLD)
//
//        _usingDefaultSettings.value = true
//        sharedPreferences.edit()
//            .putBoolean(application.getString(R.string.pref_switch_key), true)
//            .apply()
//    }
//}