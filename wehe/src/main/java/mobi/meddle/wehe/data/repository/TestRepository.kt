package mobi.meddle.wehe.data.repository

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import mobi.meddle.wehe.data.model.AppData
import mobi.meddle.wehe.data.model.RequestSet
import mobi.meddle.wehe.data.model.TestResult
import mobi.meddle.wehe.data.remote.NetworkService
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for DataStore
val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * Repository for test operations
 */
@Singleton
class TestRepository @Inject constructor(
    private val networkService: NetworkService,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val fileService: FileService
) {
    /**
     * Request analysis for a test
     */
    suspend fun requestAnalysis(serverUrl: String, userId: String, historyCount: Int): Boolean {
        val params = mapOf(
            "command" to "analyze",
            "userID" to userId,
            "historyCount" to historyCount.toString(),
            "testID" to "1"
        )

        val response = networkService.performPostRequest(serverUrl, params, true)
        return response.optBoolean("success", false)
    }

    /**
     * Get test result
     */
    suspend fun getTestResult(serverUrl: String, userId: String, historyCount: Int): TestResult? {
        val params = mapOf(
            "command" to "singleResult",
            "userID" to userId,
            "historyCount" to historyCount.toString(),
            "testID" to "1"
        )

        val response = networkService.performGetRequest(serverUrl, params, true)

        if (!response.optBoolean("success", false)) {
            return null
        }

        val resultData = response.optJSONObject("response") ?: return null

        return TestResult(
            appName = resultData.optString("replayName", ""),
            replayName = resultData.optString("replayName", ""),
            date = resultData.optString("date", ""),
            userId = resultData.optString("userID", ""),
            historyCount = resultData.optInt("historyCount", -1),
            testId = resultData.optInt("testID", -1),
            areaTest = resultData.optBoolean("area_test", false),
            ks2RatioTest = resultData.optBoolean("ks2_ratio_test", false),
            throughputAvgOriginal = resultData.optDouble("xput_avg_original", 0.0),
            throughputAvgTest = resultData.optDouble("xput_avg_test", 0.0),
            ks2dVal = resultData.optDouble("ks2dVal", 0.0),
            ks2pVal = resultData.optDouble("ks2pVal", 0.0),
            hasDifferentiation = resultData.optBoolean("area_test", false) ||
                    resultData.optBoolean("ks2_ratio_test", false)
        )
    }

    /**
     * Save test results
     */
    suspend fun saveTestResults(results: JSONArray) {
        userPreferencesRepository.saveLastResult(results.toString())
    }

    /**
     * Load app data for testing
     */
    suspend fun loadAppData(filename: String): AppData {
        val jsonContent = fileService.readAssetFile(filename)
        val jsonArray = JSONArray(jsonContent)

        val requestSets = mutableListOf<RequestSet>()
        val qArray = jsonArray.getJSONArray(0)

        for (i in 0 until qArray.length()) {
            val jsonObject = qArray.getJSONObject(i)
            val requestSet = RequestSet(
                clientServerPair = jsonObject.getString("c_s_pair"),
                payload = hexStringToByteArray(jsonObject.getString("payload")),
                timestamp = jsonObject.getDouble("timestamp"),
                responseLength = if (jsonObject.has("response_len")) jsonObject.getInt("response_len") else null,
                responseHash = if (jsonObject.has("response_hash")) jsonObject.getString("response_hash") else null,
                isEnd = if (jsonObject.has("end")) jsonObject.getBoolean("end") else false
            )
            requestSets.add(requestSet)
        }

        val udpClientPorts = mutableListOf<String>()
        val portArray = jsonArray.getJSONArray(1)
        for (i in 0 until portArray.length()) {
            udpClientPorts.add(portArray.getString(i))
        }

        val tcpCSPs = mutableListOf<String>()
        val csArray = jsonArray.getJSONArray(2)
        for (i in 0 until csArray.length()) {
            tcpCSPs.add(csArray.getString(i))
        }

        return AppData(
            name = jsonArray.getString(3),
            replayName = jsonArray.getString(3),
            tcpCSPs = tcpCSPs,
            udpClientPorts = udpClientPorts,
            requestSets = requestSets
        )
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                    Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
