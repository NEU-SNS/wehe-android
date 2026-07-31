package mobi.meddle.wehe.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mobi.meddle.wehe.R
import mobi.meddle.wehe.combined.CTCPClient
import mobi.meddle.wehe.combined.CUDPClient
import mobi.meddle.wehe.combined.CombinedQueue
import mobi.meddle.wehe.combined.CombinedSideChannel
import mobi.meddle.wehe.combined.WebSocketConnection
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.model.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.model.ServerInstance
import mobi.meddle.wehe.data.model.UpdateUIBean
import mobi.meddle.wehe.util.Config
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config as RobolectricConfig
import java.io.IOException

// Kotlin infers the generic type parameter of Mockito's `any()`/`eq()`/`isNull()` matchers as
// non-null whenever the target parameter's declared type is non-null (which several
// ServerRepository.sendRequest params are, e.g. `url: String`, `main: Boolean`), even though the
// matcher itself legitimately returns a Java `null` placeholder at runtime. That mismatch trips
// Kotlin's own `Intrinsics.checkNotNullParameter`, producing a confusing
// "any(...)/eq(...) must not be null" NullPointerException instead of a real assertion failure.
// This project has no mockito-kotlin dependency (which normally papers over this), so these three
// small helpers do the same thing by hand: register the real matcher with Mockito for its
// side effect, then hand back an unchecked-cast null so Kotlin's null-check never fires.
@Suppress("UNCHECKED_CAST")
private fun <T> uninitialized(): T = null as T

private fun <T> anyKt(): T {
    Mockito.any<T>()
    return uninitialized()
}

private fun <T> eqKt(value: T): T {
    Mockito.eq(value)
    return uninitialized()
}

private fun <T> isNullKt(): T {
    Mockito.isNull<T>()
    return uninitialized()
}

/**
 * Tests for [ReplayRepository].
 *
 * Uses Robolectric rather than a fully-mocked [Context] for two reasons: (1) `org.json.JSONObject`
 * / `JSONArray` resolve, in a plain (non-Robolectric) JVM unit test, to the Android SDK's stub jar
 * -- since this module has no real `org.json` implementation on the test classpath and isn't using
 * Robolectric elsewhere, EVERY method on those classes silently becomes a no-op returning default
 * values (`unitTests.returnDefaultValues = true` in build.gradle promotes what would otherwise be a
 * hard "Stub!" crash into a silent, wrong answer instead). Since this repository is essentially all
 * about parsing JSON server responses, that would make the tests either pass vacuously or fail in
 * confusing ways unrelated to the code under test. See the discovery in ServerRepositoryTest for
 * more detail; the same issue affects this file identically. (2) A real Robolectric [Context] gives
 * real `getString(R.string.*)` resolution and real `SharedPreferences`, which lets several tests
 * assert against the actual production error copy instead of a stubbed placeholder.
 *
 * `ReplayRepository.serverRepository` is a public `var` (not constructor-injected), which makes it
 * easy to swap in a Mockito mock of the (already-tested-separately) [ServerRepository] and
 * control exactly what the "network" returns.
 */
@RunWith(RobolectricTestRunner::class)
@RobolectricConfig(sdk = [34])
class ReplayRepositoryTest {

    private lateinit var context: Context
    private lateinit var serverRepository: ServerRepository
    private lateinit var replayRepository: ReplayRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        replayRepository = ReplayRepository(context)
        serverRepository = mock(ServerRepository::class.java)
        replayRepository.serverRepository = serverRepository

        // Config is a process-wide static singleton (see mobi.meddle.wehe.util.Config) with no
        // reset hook, so every test that touches it must (re-)establish the keys it needs.
        Config.set("result_port", "443")
        Config.set("publicIP", "9.9.9.9")
        Config.set("combined_sidechannel_port", "5555")
        Config.set("timing", "false")
        Config.set("extraString", "0")
        Config.set("sendMobileStats", "false")
        Config.set("result", "result")
    }

    /** Reflection helper: `analyzerServerUrls` is a `private val ArrayList<String>` with no setter. */
    @Suppress("UNCHECKED_CAST")
    private fun seedAnalyzerServerUrls(vararg urls: String) {
        val field = ReplayRepository::class.java.getDeclaredField("analyzerServerUrls")
        field.isAccessible = true
        val list = field.get(replayRepository) as ArrayList<String>
        list.clear()
        list.addAll(urls)
    }

    // =====================================================================
    // getPortMappingFromServer (pure function over injected CombinedSideChannel mocks)
    // =====================================================================

    @Test
    fun `getPortMappingFromServer returns empty results for an empty side channel list`() {
        val (maps, beans) = replayRepository.getPortMappingFromServer(ArrayList())
        assertThat(maps).isEmpty()
        assertThat(beans).isEmpty()
    }

    @Test
    fun `getPortMappingFromServer collects port maps and sender counts per channel, in order`() {
        val sc0 = mock(CombinedSideChannel::class.java)
        val sc1 = mock(CombinedSideChannel::class.java)
        val map0 = HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>()
        map0["tcp"] = HashMap()
        val map1 = HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>()
        map1["udp"] = HashMap()
        Mockito.`when`(sc0.receivePortMappingNonBlock()).thenReturn(map0)
        Mockito.`when`(sc0.receiveSenderCount()).thenReturn(5)
        Mockito.`when`(sc1.receivePortMappingNonBlock()).thenReturn(map1)
        Mockito.`when`(sc1.receiveSenderCount()).thenReturn(7)

        val (maps, beans) = replayRepository.getPortMappingFromServer(arrayListOf(sc0, sc1))

        assertThat(maps).containsExactly(map0, map1).inOrder()
        assertThat(beans.map { it.senderCount }).containsExactly(5, 7).inOrder()
    }

    @Test
    fun `getPortMappingFromServer does not catch IOException from a side channel (propagates uncaught)`() {
        // Note: this documents existing behavior, not necessarily desired behavior -- there's no
        // try/catch around sc.receivePortMappingNonBlock()/receiveSenderCount() in
        // ReplayRepository.kt (~line 453-454), even though both are declared to throw IOException.
        val sc = mock(CombinedSideChannel::class.java)
        Mockito.`when`(sc.receivePortMappingNonBlock()).thenThrow(IOException("boom"))

        assertThrows(IOException::class.java) {
            replayRepository.getPortMappingFromServer(arrayListOf(sc))
        }
    }

    // =====================================================================
    // ask4analysis
    // =====================================================================

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `ask4analysis sends the expected POST pairs and returns the raw response`() {
        val expected = JSONObject().put("success", true)
        val captor = ArgumentCaptor.forClass(HashMap::class.java) as ArgumentCaptor<HashMap<String, String?>>
        Mockito.`when`(
            serverRepository.sendRequest(
                eqKt("https://server/Results"), eqKt("POST"), Mockito.eq(true), isNullKt(), captor.capture()
            )
        ).thenReturn(expected)

        val result = replayRepository.ask4analysis("https://server/Results", "user-1", 3)

        assertThat(result).isSameInstanceAs(expected)
        val pairs = captor.value
        assertThat(pairs["command"]).isEqualTo("analyze")
        assertThat(pairs["userID"]).isEqualTo("user-1")
        assertThat(pairs["historyCount"]).isEqualTo("3")
        assertThat(pairs["testID"]).isEqualTo("1")
    }

    @Test
    fun `ask4analysis passes a null id straight through to the pairs map`() {
        Mockito.`when`(
            serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt())
        ).thenReturn(null)

        val result = replayRepository.ask4analysis("https://server/Results", null, 1)

        assertThat(result).isNull()
    }

    // =====================================================================
    // requestAnalysis
    // =====================================================================

    @Test
    fun `requestAnalysis succeeds when every server returns success on the first try`() {
        seedAnalyzerServerUrls("https://s1/Results", "https://s2/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt()))
            .thenReturn(JSONObject().put("success", true))

        val result = replayRepository.requestAnalysis("user-1", 1)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).hasSize(2)
    }

    @Test
    fun `requestAnalysis retries up to 3 times per server and succeeds once a non-null response arrives`() {
        seedAnalyzerServerUrls("https://s1/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt()))
            .thenReturn(null, null, JSONObject().put("success", true))

        val result = replayRepository.requestAnalysis("user-1", 1)

        assertThat(result.isSuccess).isTrue()
        verify(serverRepository, times(3)).sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt())
    }

    @Test
    fun `requestAnalysis fails when a server returns null on all 3 attempts`() {
        seedAnalyzerServerUrls("https://s1/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt()))
            .thenReturn(null)

        val result = replayRepository.requestAnalysis("user-1", 1)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message)
            .isEqualTo(context.getString(R.string.error_analysis_fail))
    }

    @Test
    fun `requestAnalysis fails when any server response has success=false`() {
        seedAnalyzerServerUrls("https://s1/Results", "https://s2/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt()))
            .thenReturn(JSONObject().put("success", true))
            .thenReturn(JSONObject().put("success", false))

        val result = replayRepository.requestAnalysis("user-1", 1)

        assertThat(result.isFailure).isTrue()
    }

    // ---------------------------------------------------------------------
    // BUG: requestAnalysis calls `result.getBoolean("success")` with no surrounding try/catch for
    // org.json.JSONException (ReplayRepository.kt:773). org.json's JSONException is an unchecked
    // RuntimeException, so if a server ever returns a JSON body that's missing the "success" field
    // (or has it as a non-boolean), this throws all the way up through requestAnalysis instead of
    // producing a Result.failure(...) like every other error path in this same function does. This
    // is notably inconsistent with `analyzeResults` a bit further down the file, which wraps its
    // entire body in `try { ... } catch (e: JSONException) { return Result.failure(e) }`.
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - requestAnalysis throws uncaught JSONException for a malformed (missing success field) response instead of Result-failure`() {
        seedAnalyzerServerUrls("https://s1/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), anyKt(), anyBoolean(), isNullKt(), anyKt()))
            .thenReturn(JSONObject().put("unexpected_field", "oops"))

        assertThrows(JSONException::class.java) {
            replayRepository.requestAnalysis("user-1", 1)
        }
    }

    // =====================================================================
    // retrieveResults
    // =====================================================================

    @Test
    fun `retrieveResults succeeds immediately when server has the result ready`() = runTest {
        seedAnalyzerServerUrls("https://s1/Results")
        val resp = JSONObject().put("success", true).put("response", JSONObject())
        Mockito.`when`(serverRepository.sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt()))
            .thenReturn(resp)

        val result = replayRepository.retrieveResults("user-1", 1, runPortTests = false)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly(resp)
    }

    @Test
    fun `retrieveResults retries while success=true but response is not ready yet, then succeeds`() = runTest {
        seedAnalyzerServerUrls("https://s1/Results")
        val notReady = JSONObject().put("success", true)
        val ready = JSONObject().put("success", true).put("response", JSONObject())
        Mockito.`when`(serverRepository.sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt()))
            .thenReturn(notReady, ready)

        val result = replayRepository.retrieveResults("user-1", 1, runPortTests = false)

        assertThat(result.isSuccess).isTrue()
        verify(serverRepository, times(2))
            .sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt())
    }

    @Test
    fun `retrieveResults returns empty success list when port tests time out (port likely blocked)`() = runTest {
        seedAnalyzerServerUrls("https://s1/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt()))
            .thenReturn(null)

        val result = replayRepository.retrieveResults("user-1", 1, runPortTests = true)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEmpty()
    }

    @Test
    fun `retrieveResults fails with not_all_tcp_sent_text when not a port test and server never has a result`() = runTest {
        seedAnalyzerServerUrls("https://s1/Results")
        Mockito.`when`(serverRepository.sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt()))
            .thenReturn(null)

        val result = replayRepository.retrieveResults("user-1", 1, runPortTests = false)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message)
            .isEqualTo(context.getString(R.string.not_all_tcp_sent_text))
    }

    @Test
    fun `retrieveResults treats an explicit server error field the same as any other not-ready response (keeps retrying)`() = runTest {
        seedAnalyzerServerUrls("https://s1/Results")
        val errorResp = JSONObject().put("success", false).put("error", "server exploded")
        val ready = JSONObject().put("success", true).put("response", JSONObject())
        Mockito.`when`(serverRepository.sendRequest(anyKt(), eqKt("GET"), Mockito.eq(true), anyKt(), isNullKt()))
            .thenReturn(errorResp, ready)

        val result = replayRepository.retrieveResults("user-1", 1, runPortTests = false)

        assertThat(result.isSuccess).isTrue()
    }

    // =====================================================================
    // analyzeResults (pure function -- only needs `context.getString`, uses the real Robolectric
    // string resources here)
    // =====================================================================

    private fun validResponse(
        userID: String = "user-1",
        historyCount: Int = 5,
        areaTest: Double = 0.5,
        ks2pVal: Double = 0.5,
        ks2Ratio: Double = 1.0,
        xputOriginal: Double = 5.0,
        xputTest: Double = 5.0
    ): JSONObject = JSONObject()
        .put("userID", userID)
        .put("historyCount", historyCount)
        .put("area_test", areaTest)
        .put("ks2pVal", ks2pVal)
        .put("ks2_ratio_test", ks2Ratio)
        .put("xput_avg_original", xputOriginal)
        .put("xput_avg_test", xputTest)

    @Test
    fun `analyzeResults - above area threshold and below p-value threshold is a differentiation`() {
        val response = validResponse(areaTest = 0.5, ks2pVal = 0.01, xputOriginal = 8.0, xputTest = 2.0)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.isSuccess).isTrue()
        val analysis = result.getOrNull()!!
        assertThat(analysis.differentiation).isTrue()
        assertThat(analysis.inconclusive).isFalse()
        // xputOriginal (8.0) > xputTest (2.0) -> "throttled", not a port test -> app text
        assertThat(analysis.errorMessage).isEqualTo(context.getString(R.string.test_throttled_app_text))
    }

    @Test
    fun `analyzeResults - prioritized message when original throughput is lower than test throughput`() {
        val response = validResponse(areaTest = 0.5, ks2pVal = 0.01, xputOriginal = 2.0, xputTest = 8.0)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = true,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        val analysis = result.getOrNull()!!
        assertThat(analysis.differentiation).isTrue()
        assertThat(analysis.errorMessage).isEqualTo(context.getString(R.string.test_prioritized_port_text))
    }

    @Test
    fun `analyzeResults - above area but at-or-above p-value threshold is inconclusive, not differentiation`() {
        val response = validResponse(areaTest = 0.5, ks2pVal = 0.50)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        val analysis = result.getOrNull()!!
        assertThat(analysis.differentiation).isFalse()
        assertThat(analysis.inconclusive).isTrue()
        assertThat(analysis.errorMessage).isNull()
    }

    @Test
    fun `analyzeResults - below area threshold is neither differentiation nor inconclusive`() {
        val response = validResponse(areaTest = 0.01, ks2pVal = 0.01)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        val analysis = result.getOrNull()!!
        assertThat(analysis.differentiation).isFalse()
        assertThat(analysis.inconclusive).isFalse()
        assertThat(analysis.errorMessage).isNull()
    }

    @Test
    fun `analyzeResults - area threshold escalates to 30 pct whenever either throughput exceeds 10`() {
        // a_threshold=5 would normally require area_test >= 0.05, but xputOriginal>10 bumps the
        // effective threshold to 30% (0.30), so area_test=0.10 should NOT count as "above area".
        val response = validResponse(areaTest = 0.10, ks2pVal = 0.01, xputOriginal = 15.0, xputTest = 5.0)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 5, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        val analysis = result.getOrNull()!!
        assertThat(analysis.differentiation).isFalse()
        assertThat(analysis.inconclusive).isFalse()
    }

    @Test
    fun `analyzeResults - empty response means port blocked, differentiation always true`() {
        val result = replayRepository.analyzeResults(
            JSONObject(), "user-1", 5, "dataFile", runPortTests = true,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 12.5
        )

        val analysis = result.getOrNull()!!
        assertThat(analysis.portBlocked).isTrue()
        assertThat(analysis.differentiation).isTrue()
        assertThat(analysis.errorMessage).isEqualTo(context.getString(R.string.test_blocked_port_text))
        assertThat(analysis.xputTest).isEqualTo(12.5) // randomThroughput fills in xput_avg_test
        assertThat(analysis.area_test).isEqualTo(-1.0)
    }

    @Test
    fun `analyzeResults - empty response non-port-test uses the app-blocked message`() {
        val result = replayRepository.analyzeResults(
            JSONObject(), "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.getOrNull()!!.errorMessage)
            .isEqualTo(context.getString(R.string.test_blocked_app_text))
    }

    @Test
    fun `analyzeResults - userID mismatch fails the sanity check`() {
        val response = validResponse(userID = "someone-else")

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).isEqualTo(context.getString(R.string.error_result))
    }

    @Test
    fun `analyzeResults - historyCount mismatch fails the sanity check`() {
        val response = validResponse(historyCount = 999)

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `analyzeResults - userID comparison is case-insensitive (per source's equals ignoreCase)`() {
        val response = validResponse(userID = "USER-1")

        val result = replayRepository.analyzeResults(
            response, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `analyzeResults - missing required field is caught as JSONException and returned as Result-failure`() {
        // Unlike requestAnalysis/retrieveResults's getBoolean("success") calls, this function DOES
        // wrap its field access in try/catch(JSONException), so a malformed response degrades
        // gracefully here.
        val incomplete = JSONObject().put("userID", "user-1").put("historyCount", 5)
        // no area_test/ks2pVal/etc.

        val result = replayRepository.analyzeResults(
            incomplete, "user-1", 5, "dataFile", runPortTests = false,
            a_threshold = 30, ks2pvalue_threshold = 5, randomThroughput = 0.0
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(JSONException::class.java)
    }

    // =====================================================================
    // saveResults
    // =====================================================================

    private fun realPrefs(name: String = "saveResultsTest"): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)

    @Test
    fun `saveResults stores results keyed by a formatted date string`() {
        val prefs = realPrefs("save1")
        val results = JSONArray().put(JSONObject().put("a", 1))

        replayRepository.saveResults(results, prefs)

        val stored = JSONObject(prefs.getString("lastResult", "{}")!!)
        assertThat(stored.length()).isEqualTo(1)
        val key = stored.keys().next()
        // yyyy/MM/dd HH:mm:ss
        assertThat(key).matches("""\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}""")
    }

    @Test
    fun `saveResults trims the oldest entry once 10 results are already stored`() {
        val prefs = realPrefs("save2")
        val existing = JSONObject()
        for (i in 1..10) {
            existing.put("2020/01/01 00:00:0$i", JSONArray())
        }
        prefs.edit().putString("lastResult", existing.toString()).apply()

        replayRepository.saveResults(JSONArray(), prefs)

        val stored = JSONObject(prefs.getString("lastResult", "{}")!!)
        // one of the original 10 was evicted, and the new one was added -> still 10
        assertThat(stored.length()).isEqualTo(10)
    }

    @Test
    fun `saveResults recovers from a corrupted stored value instead of crashing`() {
        val prefs = realPrefs("save3")
        prefs.edit().putString("lastResult", "not valid json").apply()

        replayRepository.saveResults(JSONArray(), prefs)

        // Should have recovered by treating the corrupted value as an empty JSONObject and
        // stored a single fresh entry rather than propagating the parse failure.
        val stored = JSONObject(prefs.getString("lastResult", "{}")!!)
        assertThat(stored.length()).isEqualTo(1)
    }

    // ---------------------------------------------------------------------
    // BUG: saveResults takes `settings: SharedPreferences?` (nullable) and safely uses `settings?.`
    // for the *read* at the top of the function, but then unconditionally force-unwraps at the end
    // with `settings!!.edit()` (ReplayRepository.kt:379) -- so passing null still crashes with an
    // NPE, just later and less predictably than if the parameter were simply non-null to begin
    // with. This is an inconsistent null-handling contract within a single function.
    // ---------------------------------------------------------------------
    @Test
    fun `BUG - saveResults still crashes with NPE when settings is null, despite null-safe reads earlier in the function`() {
        assertThrows(NullPointerException::class.java) {
            replayRepository.saveResults(JSONArray(), null)
        }
    }

    // =====================================================================
    // isNetworkUnavailable
    // =====================================================================

    @Test
    fun `isNetworkUnavailable is true when there is no active network`() {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        Shadows.shadowOf(connectivityManager).setDefaultNetworkActive(false)

        assertThat(replayRepository.isNetworkUnavailable(context)).isTrue()
    }

    // =====================================================================
    // updateHistoryCount
    // =====================================================================

    @Test
    fun `updateHistoryCount increments and persists the new count`() {
        val result = replayRepository.updateHistoryCount(4)

        assertThat(result).isEqualTo(5)
        val settings = context.getSharedPreferences("STATUS", Context.MODE_PRIVATE)
        assertThat(settings.getInt("historyCount", -1)).isEqualTo(5)
    }

    // =====================================================================
    // cleanupResources
    // =====================================================================

    @Test
    fun `cleanupResources closes every side channel, TCP client, and UDP client`() {
        val sc0 = mock(CombinedSideChannel::class.java)
        val sc1 = mock(CombinedSideChannel::class.java)
        val tcpClient = mock(CTCPClient::class.java)
        val udpClient = mock(CUDPClient::class.java)
        val appData = CombinedAppJSONInfoBean()
        appData.tcpCSPs = arrayListOf("csp1")
        appData.udpClientPorts = arrayListOf("1000")

        val tcpMapping = arrayListOf(hashMapOf("csp1" to tcpClient))
        val udpMapping = arrayListOf(hashMapOf("1000" to udpClient))

        replayRepository.cleanupResources(arrayListOf(sc0, sc1), tcpMapping, udpMapping, appData)

        verify(sc0).closeSideChannelSocket()
        verify(sc1).closeSideChannelSocket()
        verify(tcpClient).close()
        verify(udpClient).close()
    }

    @Test
    fun `cleanupResources tolerates a csp or port with no corresponding client (missing map entry)`() {
        val appData = CombinedAppJSONInfoBean()
        appData.tcpCSPs = arrayListOf("csp-not-in-map")
        appData.udpClientPorts = arrayListOf("port-not-in-map")

        // Should not throw even though the mappings don't contain these keys.
        replayRepository.cleanupResources(
            ArrayList(), arrayListOf(HashMap()), arrayListOf(HashMap()), appData
        )
    }

    // =====================================================================
    // closeWebSocketConnections / isMlabServerUsed / isIPv6 / clearTimers / checkPortAccess
    // =====================================================================

    @Test
    fun `closeWebSocketConnections closes only the open connections and leaves the list intact by default`() {
        val open = mock(WebSocketConnection::class.java)
        val closed = mock(WebSocketConnection::class.java)
        Mockito.`when`(open.isOpen).thenReturn(true)
        Mockito.`when`(closed.isOpen).thenReturn(false)
        replayRepository.wsConns.add(open)
        replayRepository.wsConns.add(closed)

        replayRepository.closeWebSocketConnections()

        verify(open).close()
        verify(closed, never()).close()
        assertThat(replayRepository.wsConns).hasSize(2)
    }

    @Test
    fun `closeWebSocketConnections with clear argument empties the connection list`() {
        val ws = mock(WebSocketConnection::class.java)
        Mockito.`when`(ws.isOpen).thenReturn(false)
        replayRepository.wsConns.add(ws)

        replayRepository.closeWebSocketConnections("clear")

        assertThat(replayRepository.wsConns).isEmpty()
    }

    @Test
    fun `isMlabServerUsed defaults to false`() {
        assertThat(replayRepository.isMlabServerUsed()).isFalse()
    }

    @Test
    fun `isIPv6 delegates to the underlying ServerRepository`() {
        Mockito.`when`(serverRepository.isIPv6).thenReturn(true)
        assertThat(replayRepository.isIPv6()).isTrue()
    }

    @Test
    fun `clearTimers delegates to ServerRepository cleanup`() {
        replayRepository.clearTimers()
        verify(serverRepository).cleanup()
    }

    @Test
    fun `checkPortAccess reports accessible when getPublicIP does not return -1`() = runTest {
        Mockito.`when`(serverRepository.getPublicIP("1234")).thenReturn("5.6.7.8")

        val (ip, accessible) = replayRepository.checkPortAccess("1234")

        assertThat(ip).isEqualTo("5.6.7.8")
        assertThat(accessible).isTrue()
    }

    @Test
    fun `checkPortAccess reports blocked when getPublicIP returns -1`() = runTest {
        Mockito.`when`(serverRepository.getPublicIP("1234")).thenReturn("-1")

        val (ip, accessible) = replayRepository.checkPortAccess("1234")

        assertThat(ip).isEqualTo("-1")
        assertThat(accessible).isFalse()
    }

    // =====================================================================
    // createTCPClients / createUDPClients
    // =====================================================================

    @Test
    fun `createTCPClients parses the c_s_pair, zero-pads the port, and fills in a blank server`() {
        replayRepository.servers.add("203.0.113.9")
        val appData = CombinedAppJSONInfoBean()
        appData.tcpCSPs = arrayListOf("1.2.3.4-5.6.7.8.443")
        appData.replayName = "myReplay"

        val instance = ServerInstance(server = "", port = "443") // blank server -> falls back
        val serverPortsMaps = arrayListOf(
            hashMapOf("tcp" to hashMapOf("5.6.7.8" to hashMapOf("00443" to instance)))
        )

        val mappings = replayRepository.createTCPClients(appData, serverPortsMaps)

        assertThat(mappings).hasSize(1)
        val client = mappings[0]["1.2.3.4-5.6.7.8.443"]
        assertThat(client).isNotNull()
        // The blank ServerInstance.server got filled in with servers[0].
        assertThat(instance.server).isEqualTo("203.0.113.9")
    }

    @Test
    fun `createTCPClients propagates and logs when the server port mapping is missing the expected keys`() {
        replayRepository.servers.add("203.0.113.9")
        val appData = CombinedAppJSONInfoBean()
        appData.tcpCSPs = arrayListOf("1.2.3.4-5.6.7.8.443")
        appData.replayName = "myReplay"

        // Empty maps -- no "tcp" key at all.
        val serverPortsMaps = arrayListOf(
            HashMap<String, HashMap<String, HashMap<String, ServerInstance>>>()
        )

        assertThrows(Exception::class.java) {
            replayRepository.createTCPClients(appData, serverPortsMaps)
        }
    }

    @Test
    fun `createUDPClients creates one client per original client port, per server`() {
        replayRepository.servers.add("203.0.113.9")
        replayRepository.servers.add("203.0.113.10")
        val appData = CombinedAppJSONInfoBean()
        appData.udpClientPorts = arrayListOf("1000", "2000")

        val mappings = replayRepository.createUDPClients(appData)

        assertThat(mappings).hasSize(2) // one per server
        assertThat(mappings[0].keys).containsExactly("1000", "2000")
    }

    // =====================================================================
    // initiateTestWithServer (side channels are passed in, so plain mocks suffice -- no need to
    // touch the real, network-backed CombinedSideChannel constructor)
    // =====================================================================

    @Test
    fun `initiateTestWithServer succeeds and collects the number of time slices per channel`() {
        val sc = mock(CombinedSideChannel::class.java)
        Mockito.`when`(sc.ask4Permission()).thenReturn(arrayOf("1", "", "42"))

        val result = replayRepository.initiateTestWithServer(
            arrayListOf(sc), CombinedAppJSONInfoBean(), "user-1", 1, 0,
            endOfTest = true, doTest = false, ipThroughProxy = "1.2.3.4"
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).containsExactly(42)
        verify(sc).declareID(anyKt(), eqKt("True"), eqKt("user-1"), eqKt("1"), eqKt("0"), anyKt(), eqKt("1.2.3.4"), anyKt())
        verify(sc).sendIperf()
        verify(sc).sendMobileStats(anyKt(), anyKt())
    }

    @Test
    fun `initiateTestWithServer maps permission error code 2 to the IP-already-connected message`() {
        val sc = mock(CombinedSideChannel::class.java)
        Mockito.`when`(sc.ask4Permission()).thenReturn(arrayOf("0", "2", ""))

        val result = replayRepository.initiateTestWithServer(
            arrayListOf(sc), CombinedAppJSONInfoBean(), "user-1", 1, 0,
            endOfTest = false, doTest = false, ipThroughProxy = "1.2.3.4"
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message)
            .isEqualTo(context.getString(R.string.error_IP_connected))
    }

    @Test
    fun `initiateTestWithServer maps an unrecognized permission error code to the generic unknown error`() {
        val sc = mock(CombinedSideChannel::class.java)
        Mockito.`when`(sc.ask4Permission()).thenReturn(arrayOf("0", "99", ""))

        val result = replayRepository.initiateTestWithServer(
            arrayListOf(sc), CombinedAppJSONInfoBean(), "user-1", 1, 0,
            endOfTest = false, doTest = false, ipThroughProxy = "1.2.3.4"
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).isEqualTo(context.getString(R.string.error_unknown))
    }

    // =====================================================================
    // runPacketQueue (CombinedQueue is passed in, so a plain mock avoids the heavy real replay
    // machinery entirely)
    // =====================================================================

    @Test
    fun `runPacketQueue invokes queue-run with the configured timing flag and returns a positive duration`() {
        val queue = mock(CombinedQueue::class.java)
        Config.set("timing", "true")

        val duration = replayRepository.runPacketQueue(
            queue, 1, ArrayList(), ArrayList(), ArrayList(), ArrayList(),
            UpdateUIBean(), kotlin.coroutines.EmptyCoroutineContext
        )

        assertThat(duration).isAtLeast(0.0)
        verify(queue).run(
            anyKt(), Mockito.eq(1), anyKt(), anyKt(), anyKt(), anyKt(), Mockito.eq(true), eqKt(replayRepository.servers), anyKt()
        )
    }

    // =====================================================================
    // setupServersAndCertificates (the simple, non-MLab path -- getServerIP is mocked, so no real
    // DNS/network/cert I/O happens)
    // =====================================================================

    @Test
    fun `setupServersAndCertificates succeeds for a plain IPv4 server with no metadata server`() = runTest {
        Mockito.`when`(serverRepository.getServerIP("plainserver.example")).thenReturn("203.0.113.9")

        val result = replayRepository.setupServersAndCertificates(
            "plainserver.example", null, numTests = 1, isTomography = false
        )

        assertThat(result.isSuccess).isTrue()
        assertThat(replayRepository.servers).containsExactly("203.0.113.9")
        verify(serverRepository).setServers(replayRepository.servers)
    }

    @Test
    fun `setupServersAndCertificates fails with error_unknown_host when DNS resolution fails`() = runTest {
        Mockito.`when`(serverRepository.getServerIP("badserver.example")).thenReturn("")

        val result = replayRepository.setupServersAndCertificates(
            "badserver.example", null, numTests = 1, isTomography = false
        )

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message).isEqualTo(context.getString(R.string.error_unknown_host))
    }

    @Test
    fun `setupServersAndCertificates rewrites wehe3-meddle-mobi to wehe4-meddle-mobi and takes the MLab hack path`() = runTest {
        // "wehe4.meddle.mobi" is special-cased to seed servers[0]="10.0.0.0" and unconditionally
        // route through connectToMLabServers -- exercise that here with a minimal successful MLab
        // response for numTests=1 so the whole call can succeed without needing tomography.
        val mlabResp = JSONObject().put(
            "results", JSONArray().put(
                JSONObject().put("machine", "abc").put(
                    "urls", JSONObject().put(Consts.MLAB_WEB_SOCKET_SERVER_KEY, "wss://example/ws")
                )
            )
        )
        Mockito.`when`(serverRepository.sendRequest(eqKt(Consts.MLAB_SERVERS), eqKt("GET"), Mockito.eq(false), isNullKt(), isNullKt()))
            .thenReturn(mlabResp)
        Mockito.`when`(serverRepository.getServerIP(anyKt())).thenReturn("198.51.100.5")

        mockConstruction(WebSocketConnection::class.java) { mockWs, _ ->
            Mockito.`when`(mockWs.isOpen).thenReturn(true)
        }.use {
            val result = replayRepository.setupServersAndCertificates(
                "wehe3.meddle.mobi", null, numTests = 1, isTomography = false
            )

            assertThat(result.isSuccess).isTrue()
            assertThat(replayRepository.isMlabServerUsed()).isTrue()
            // The original "10.0.0.0" placeholder was removed by connectToMLabServers and replaced
            // with the (mocked) resolved MLab server IP.
            assertThat(replayRepository.servers).containsExactly("198.51.100.5")
        }
    }

    @Test
    fun `connectToMLabServers falls back to wehe2 when it cannot reach enough MLab servers, and fails outright for tomography`() = runTest {
        // No results at all from the MLab locate service -> mLabResp!!["results"] would NPE if not
        // handled -- give it a well-formed but empty list instead so the loop just never runs.
        val mlabResp = JSONObject().put("results", JSONArray())
        Mockito.`when`(serverRepository.sendRequest(eqKt(Consts.MLAB_SERVERS), eqKt("GET"), Mockito.eq(false), isNullKt(), isNullKt()))
            .thenReturn(mlabResp)

        val result = replayRepository.setupServersAndCertificates(
            "wehe3.meddle.mobi", null, numTests = 1, isTomography = true
        )

        // Tomography explicitly isn't supported without MLab servers.
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()!!.message)
            .isEqualTo(context.getString(R.string.tomography_not_supported))
    }

    // ---------------------------------------------------------------------
    // BUG: mLabResp!!["results"] (ReplayRepository.kt:158) will throw an uncaught
    // KotlinNullPointerException if sendRequest ever returns null (e.g. the MLab locate service is
    // down or times out) -- this is exactly the failure mode ServerRepository.sendRequest is
    // documented to signal via a null return, yet connectToMLabServers's surrounding
    // `catch (e: Exception)` DOES actually catch this (Kotlin's `!!` throws
    // `NullPointerException`/`KotlinNullPointerException`, a subtype of `Exception`), so this
    // specific case is NOT a crash -- but it's worth calling out that the only thing standing
    // between "a normal, documented null return from sendRequest" and "silent failure via generic
    // Exception catch" is an unchecked `!!`, with no distinguishing log message for this particular
    // cause versus a genuine WebSocket/parsing exception a few lines below.
    // ---------------------------------------------------------------------
    @Test
    fun `connectToMLabServers surfaces a failure (via the generic catch-all) when the MLab locate request itself fails`() = runTest {
        Mockito.`when`(serverRepository.sendRequest(eqKt(Consts.MLAB_SERVERS), eqKt("GET"), Mockito.eq(false), isNullKt(), isNullKt()))
            .thenReturn(null)

        val result = replayRepository.setupServersAndCertificates(
            "wehe3.meddle.mobi", null, numTests = 1, isTomography = false
        )

        assertThat(result.isFailure).isTrue()
    }
}
