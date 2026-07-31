package mobi.meddle.wehe.ui.main.viewmodels

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ResultsViewModel].
 *
 * Uses Robolectric because [ResultsViewModel.loadResults] calls several real Android APIs
 * directly (PreferenceManager.getDefaultSharedPreferences, android.text.format.DateUtils,
 * java.text.DateFormat with a Locale-dependent Android resource string) that are not trivial to
 * stub with plain Mockito.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var viewModel: ResultsViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = ResultsViewModel()
    }

    private fun putLastResult(json: String) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("lastResult", json)
            .apply()
    }

    private fun response(
        isPort: Boolean = false,
        appName: String = "TestApp",
        appImage: String = "test_image",
        dateMillis: String = System.currentTimeMillis().toString(),
        status: String = "no diff",
        xputOriginal: Double = 5.0,
        xputTest: Double = 4.5,
        isIPv6: Boolean = false,
        server: String = "wehe4.meddle.mobi",
        carrier: String = "Verizon",
        tomographyNetwork: String? = null
    ): JSONObject {
        val obj = JSONObject()
        obj.put("isPort", isPort)
        obj.put("appName", appName)
        obj.put("appImage", appImage)
        obj.put("date", dateMillis)
        obj.put("status", status)
        obj.put("xput_avg_original", xputOriginal)
        obj.put("xput_avg_test", xputTest)
        obj.put("isIPv6", isIPv6)
        obj.put("server", server)
        obj.put("carrier", carrier)
        if (tomographyNetwork != null) {
            obj.put("tomographyNetwork", tomographyNetwork)
        }
        return obj
    }

    @Test
    fun `results LiveData has no value before loadResults is called`() {
        assertThat(viewModel.results.value).isNull()
    }

    @Test
    fun `loadResults with no stored history produces empty list`() = runTest {
        // "lastResult" pref key was never set - loadResults should use the "{}" default and
        // produce an empty list rather than crashing or leaving the LiveData unset.
        viewModel.loadResults(context)
        advanceUntilIdle()

        assertThat(viewModel.results.value).isNotNull()
        assertThat(viewModel.results.value).isEmpty()
    }

    @Test
    fun `loadResults parses a single stored result correctly`() = runTest {
        val dateKey = "2024/01/01 10:00:00"
        val responses = JSONArray().put(
            response(
                isPort = true,
                appName = "port 443",
                status = "has diff",
                isIPv6 = true,
                carrier = "T-Mobile"
            )
        )
        val history = JSONObject().put(dateKey, responses)
        putLastResult(history.toString())

        viewModel.loadResults(context)
        advanceUntilIdle()

        val results = viewModel.results.value
        assertThat(results).isNotNull()
        assertThat(results).hasSize(1)
        val result = results!![0]
        assertThat(result.isPortTest).isTrue()
        assertThat(result.resultNameText).isEqualTo("port 443")
        assertThat(result.differentiationText).isEqualTo("has diff")
        assertThat(result.ipType).isEqualTo("IPv6")
        assertThat(result.carrier).isEqualTo("T-Mobile")
        assertThat(result.isTomography).isFalse()
        assertThat(result.differentiationNetwork).isEmpty()
    }

    @Test
    fun `loadResults reverses order so most recently added result comes first`() = runTest {
        // All three responses live under a single date key so the JSONArray ordering is
        // deterministic; loadResults should reverse the overall accumulated list so the item
        // that was appended last ends up first.
        val responses = JSONArray()
            .put(response(appName = "First"))
            .put(response(appName = "Second"))
            .put(response(appName = "Third"))
        val history = JSONObject().put("2024/01/01 10:00:00", responses)
        putLastResult(history.toString())

        viewModel.loadResults(context)
        advanceUntilIdle()

        val results = viewModel.results.value
        assertThat(results).hasSize(3)
        assertThat(results!!.map { it.resultNameText }).containsExactly("Third", "Second", "First").inOrder()
    }

    @Test
    fun `loadResults detects tomography results via tomographyNetwork key`() = runTest {
        val responses = JSONArray().put(
            response(status = "tomo succ", tomographyNetwork = "Comcast")
        )
        val history = JSONObject().put("2024/01/01 10:00:00", responses)
        putLastResult(history.toString())

        viewModel.loadResults(context)
        advanceUntilIdle()

        val result = viewModel.results.value!![0]
        assertThat(result.isTomography).isTrue()
        assertThat(result.differentiationNetwork).isEqualTo("Comcast")
    }

    @Test
    fun `loadResults with malformed JSON in preferences is caught and leaves results unset`() = runTest {
        // Not valid JSON at all -> JSONObject(...) constructor itself throws JSONException,
        // which IS caught by loadResults' try/catch.
        putLastResult("not valid json{{{")

        viewModel.loadResults(context)
        advanceUntilIdle()

        assertThat(viewModel.results.value).isNull()
    }

    @Test
    fun `BUG loadResults crashes uncaught when a result's date field is not numeric`() {
        // ResultsViewModel.kt only catches JSONException around the parsing loop, but
        // parseResult() calls response.getString("date").toLong() which throws
        // NumberFormatException (not a JSONException) for any non-numeric date string. A single
        // corrupted history entry (e.g. from an older/rolled-back app version, manual edits to
        // the backing shared prefs file, or a future format change) will crash the coroutine
        // launched by viewModelScope, since there is no catch-all. This test documents that
        // currently-uncaught crash so it doesn't regress unnoticed; if parseResult's error
        // handling is hardened, this test should be updated to assert graceful handling instead.
        //
        // Note: kotlinx-coroutines-test only surfaces an uncaught exception from a launched
        // child coroutine when the enclosing runTest {} block itself completes, not
        // synchronously from advanceUntilIdle() - so the whole runTest call must be inside
        // assertThrows, not just its body.
        val responses = JSONArray().put(response(dateMillis = "not-a-number"))
        val history = JSONObject().put("2024/01/01 10:00:00", responses)
        putLastResult(history.toString())

        assertThrows(NumberFormatException::class.java) {
            runTest {
                viewModel.loadResults(context)
                advanceUntilIdle()
            }
        }
    }
}
