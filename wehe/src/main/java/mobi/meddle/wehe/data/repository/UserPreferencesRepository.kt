package mobi.meddle.wehe.data.repository

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import mobi.meddle.wehe.data.model.TestConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Repository for user preferences
 */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val context: Context
) {
    private val dataStore = context.dataStore

    // Preference keys
    private object PreferencesKeys {
        val USER_ID = stringPreferencesKey("user_id")
        val HISTORY_COUNT = intPreferencesKey("history_count")
        val HAS_ID = booleanPreferencesKey("has_id")
        val HAS_HISTORY_COUNT = booleanPreferencesKey("has_history_count")
        val LAST_RESULT = stringPreferencesKey("last_result")
        val CONFIRMATION_REPLAYS = booleanPreferencesKey("confirmation_replays")
        val USE_DEFAULT_THRESHOLDS = booleanPreferencesKey("use_default_thresholds")
        val A_THRESHOLD = intPreferencesKey("a_threshold")
        val KS2P_THRESHOLD = intPreferencesKey("ks2p_threshold")
    }

    val userId: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.USER_ID] ?: generateRandomId()
        }

    val historyCount: Flow<Int> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.HISTORY_COUNT] ?: 0
        }

    val testConfig: Flow<TestConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            TestConfig(
                confirmationReplays = preferences[PreferencesKeys.CONFIRMATION_REPLAYS] ?: true,
                useDefaultThresholds = preferences[PreferencesKeys.USE_DEFAULT_THRESHOLDS] ?: true,
                aThreshold = preferences[PreferencesKeys.A_THRESHOLD] ?: 10,
                ks2pValueThreshold = preferences[PreferencesKeys.KS2P_THRESHOLD] ?: 5
            )
        }

    suspend fun incrementHistoryCount() {
        dataStore.edit { preferences ->
            val currentCount = preferences[PreferencesKeys.HISTORY_COUNT] ?: 0
            preferences[PreferencesKeys.HISTORY_COUNT] = currentCount + 1
            preferences[PreferencesKeys.HAS_HISTORY_COUNT] = true
        }
    }

    suspend fun ensureUserId() {
        dataStore.edit { preferences ->
            if (preferences[PreferencesKeys.HAS_ID] != true) {
                preferences[PreferencesKeys.USER_ID] = generateRandomId()
                preferences[PreferencesKeys.HAS_ID] = true
            }
        }
    }

    suspend fun saveLastResult(results: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_RESULT] = results
        }
    }

    private fun generateRandomId(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..10)
            .map { chars.random() }
            .joinToString("")
    }


}
