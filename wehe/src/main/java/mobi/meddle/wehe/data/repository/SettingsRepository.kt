//// SettingsRepository.kt
//package mobi.meddle.wehe.data.repository
//
//import android.content.Context
//import android.content.SharedPreferences
//import androidx.preference.PreferenceManager
//import dagger.hilt.android.qualifiers.ApplicationContext
//import mobi.meddle.wehe.R
//import mobi.meddle.wehe.constant.Consts
//import javax.inject.Inject
//import javax.inject.Singleton
//
///**
// * Repository to manage settings data and persistence
// */
//@Singleton
//class SettingsRepository @Inject constructor(
//    @ApplicationContext private val context: Context
//) {
//    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
//
//    /**
//     * Get the current server address
//     */
//    fun getServerAddress(): String {
//        return sharedPreferences.getString(
//            context.getString(R.string.pref_server_key),
//            Consts.DEFAULT_SERVER
//        ) ?: Consts.DEFAULT_SERVER
//    }
//
//    /**
//     * Save the server address
//     */
//    fun saveServerAddress(address: String) {
//        sharedPreferences.edit()
//            .putString(context.getString(R.string.pref_server_key), address)
//            .apply()
//    }
//
//    /**
//     * Get the current area threshold
//     */
//    fun getAreaThreshold(): Int {
//        return sharedPreferences.getString(
//            context.getString(R.string.pref_area_key),
//            Consts.A_THRESHOLD.toString()
//        )?.toIntOrNull() ?: Consts.A_THRESHOLD
//    }
//
//    /**
//     * Save the area threshold
//     */
//    fun saveAreaThreshold(threshold: Int) {
//        sharedPreferences.edit()
//            .putString(context.getString(R.string.pref_area_key), threshold.toString())
//            .apply()
//    }
//
//    /**
//     * Get the current KS2P threshold
//     */
//    fun getKs2pThreshold(): Int {
//        return sharedPreferences.getString(
//            context.getString(R.string.pref_ks2p_key),
//            Consts.KS2PVAL_THRESHOLD.toString()
//        )?.toIntOrNull() ?: Consts.KS2PVAL_THRESHOLD
//    }
//
//    /**
//     * Save the KS2P threshold
//     */
//    fun saveKs2pThreshold(threshold: Int) {
//        sharedPreferences.edit()
//            .putString(context.getString(R.string.pref_ks2p_key), threshold.toString())
//            .apply()
//    }
//
//    /**
//     * Check if using default settings
//     */
//    fun isUsingDefaultSettings(): Boolean {
//        return sharedPreferences.getBoolean(
//            context.getString(R.string.pref_switch_key),
//            false
//        )
//    }
//
//    /**
//     * Set whether using default settings
//     */
//    fun setUsingDefaultSettings(usingDefaults: Boolean) {
//        sharedPreferences.edit()
//            .putBoolean(context.getString(R.string.pref_switch_key), usingDefaults)
//            .apply()
//    }
//
//    /**
//     * Reset all settings to default values
//     */
//    fun resetToDefaults() {
//        saveServerAddress(Consts.DEFAULT_SERVER)
//        saveAreaThreshold(Consts.A_THRESHOLD)
//        saveKs2pThreshold(Consts.KS2PVAL_THRESHOLD)
//        setUsingDefaultSettings(true)
//    }
//}