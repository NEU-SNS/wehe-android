package mobi.meddle.wehe.ui.replay.runner

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.model.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.model.UDPReplayInfoBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import mobi.meddle.wehe.data.repository.ServerRepository
import mobi.meddle.wehe.ui.replay.viewmodel.ShadowNetworkCapabilitiesBuilderCompat
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Tests for [BackgroundTestRunner], which owns replay orchestration now that tests run in a
 * background service.
 *
 * This suite replaces the orchestration half of the old `ReplayViewModelTest` (the UI-state half
 * moved to `BackgroundReplayViewModelTest`). Retargeting made these tests markedly simpler than
 * the originals: the old `ReplayViewModel.execute()` hardcoded `viewModelScope.launch(
 * Dispatchers.IO)`, so tests had to synchronize on LiveData through a `CountDownLatch` with a
 * ten-second timeout. [BackgroundTestRunner.runTests] is a plain `suspend fun` reporting through
 * constructor callbacks, so `runBlocking { }` is enough and every assertion is deterministic.
 *
 * [ReplayRepository]/[ServerRepository] are mocked with Mockito (mockito-inline lets us mock these
 * final Kotlin classes) so no real networking or socket I/O ever happens. A real Robolectric
 * [Application] supplies the Context so SharedPreferences, string resources and the bundled
 * `configuration.properties` asset behave like the real app.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundTestRunnerTest {

    private lateinit var application: Application
    private lateinit var repository: ReplayRepository
    private lateinit var serverRepository: ServerRepository

    // Captured callback output - the runner's entire observable surface.
    private val progressUpdates = mutableListOf<Int>()
    private val statusUpdates = mutableListOf<Pair<String, String>>()
    private val currentAppUpdates = mutableListOf<ApplicationBean?>()
    private val iterationUpdates = mutableListOf<Int>()
    private val errors = mutableListOf<String>()
    private var completion: Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>? = null

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        repository = mock(ReplayRepository::class.java)
        serverRepository = mock(ServerRepository::class.java)
        `when`(repository.serverRepository).thenReturn(serverRepository)

        progressUpdates.clear()
        statusUpdates.clear()
        currentAppUpdates.clear()
        iterationUpdates.clear()
        errors.clear()
        completion = null
    }

    private fun newRunner() = BackgroundTestRunner(
        replayRepository = repository,
        applicationContext = application,
        onProgressUpdate = { progressUpdates.add(it) },
        onStatusUpdate = { statusUpdates.add(it) },
        onCurrentAppUpdate = { currentAppUpdates.add(it) },
        onIterationUpdate = { iterationUpdates.add(it) },
        onTestComplete = { all, diff, inconclusive -> completion = Triple(all, diff, inconclusive) },
        onError = { errors.add(it) }
    )

    private fun testApp(name: String = "TestApp"): ApplicationBean = ApplicationBean().apply {
        this.name = name
        dataFile = "data.json"
        randomDataFile = "random.json"
        size = 10
        time = 10
        category = ApplicationBean.Category.VIDEO
    }

    private fun makeNetworkAvailable(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val shadowCm = shadowOf(cm)
        val network = cm.activeNetwork
            ?: throw IllegalStateException("no active network in Robolectric shadow")
        shadowCm.setNetworkCapabilities(network, ShadowNetworkCapabilitiesBuilderCompat.withInternet())
    }

    // ------------------------------------------------------------------
    // Stubbing helpers
    // ------------------------------------------------------------------

    private fun stubSuccessfulSetup() {
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.success(true))
            `when`(serverRepository.getPublicIP(anyString())).thenReturn("9.8.7.6")
        }
    }

    private fun stubSingleAppReplay(analysis: ReplayRepository.ResultAnalysis) {
        val appData = CombinedAppJSONInfoBean() // tcpCSPs empty -> isTCP() == false
        `when`(repository.loadAppDataForReplay(anyObject(), anyString())).thenReturn(appData)
        `when`(repository.setupSideChannels(anyObject())).thenReturn(Pair(ArrayList(), ArrayList()))
        `when`(repository.updateHistoryCount(anyInt())).thenReturn(1)
        `when`(
            repository.initiateTestWithServer(
                anyObject(), anyObject(), Mockito.nullable(String::class.java), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyString()
            )
        ).thenReturn(Result.success(ArrayList()))
        `when`(repository.getPortMappingFromServer(anyObject()))
            .thenReturn(Pair(ArrayList(), ArrayList<UDPReplayInfoBean>()))
        `when`(repository.createTCPClients(anyObject(), anyObject())).thenReturn(ArrayList())
        `when`(repository.createUDPClients(anyObject())).thenReturn(ArrayList())
        `when`(
            repository.runPacketQueue(
                anyObject(), anyInt(), anyObject(), anyObject(), anyObject(), anyObject(),
                anyObject(), anyObject()
            )
        ).thenReturn(0.0)
        `when`(repository.wsConns).thenReturn(ArrayList())
        `when`(repository.isIPv6()).thenReturn(false)
        `when`(repository.requestAnalysis(anyString(), anyInt())).thenReturn(Result.success(ArrayList()))
        runBlocking {
            `when`(repository.retrieveResults(anyString(), anyInt(), anyBoolean()))
                .thenReturn(Result.success(listOf(JSONObject().put("response", JSONObject()))))
        }
        `when`(
            repository.analyzeResults(
                anyObject(), anyString(), anyInt(), anyString(), anyBoolean(), anyInt(), anyInt(), anyDouble()
            )
        ).thenReturn(Result.success(analysis))
    }

    private fun noDifferentiationAnalysis() = ReplayRepository.ResultAnalysis(
        differentiation = false, inconclusive = false, portBlocked = false,
        area_test = 0.01, ks2pVal = 0.9, ks2RatioTest = 1.0,
        xputOriginal = 5.0, xputTest = 5.0, errorMessage = null, response = JSONObject()
    )

    private fun differentiationAnalysis() = ReplayRepository.ResultAnalysis(
        differentiation = true, inconclusive = false, portBlocked = false,
        area_test = 0.9, ks2pVal = 0.001, ks2RatioTest = 1.0,
        xputOriginal = 10.0, xputTest = 2.0, errorMessage = "throttled", response = JSONObject()
    )

    // ------------------------------------------------------------------
    // Setup / failure paths
    // ------------------------------------------------------------------

    @Test
    fun `runTests with no network reports the localized network error and never completes`() {
        // Network unavailable is the Robolectric default - no extra stubbing needed.
        val runner = newRunner()

        runBlocking { runner.runTests(false, "Verizon", arrayListOf(testApp())) }

        assertThat(errors).contains(application.getString(R.string.text_network_error))
        assertThat(completion).isNull()
    }

    @Test
    fun `runTests marks apps unavailable when server setup fails`() {
        makeNetworkAvailable(application)
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.failure(Exception("boom")))
        }
        val app = testApp("UnreachableApp")

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(app)) }

        val unavailable = application.getString(R.string.server_unavailable)
        assertThat(errors).contains(unavailable)
        assertThat(statusUpdates).contains(Pair("UnreachableApp", unavailable))
        assertThat(completion).isNull()
    }

    @Test
    fun `runTests reports a connection error when the public IP cannot be resolved`() {
        makeNetworkAvailable(application)
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.success(true))
            `when`(serverRepository.getPublicIP(anyString())).thenReturn("-1")
        }

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(testApp("NoIpApp"))) }

        assertThat(errors).contains(application.getString(R.string.error_no_connection))
        assertThat(completion).isNull()
    }

    // ------------------------------------------------------------------
    // End-to-end orchestration
    // ------------------------------------------------------------------

    @Test
    fun `runTests runs a full no-differentiation replay end-to-end`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())
        val app = testApp("NoDiffApp")

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(app)) }

        assertThat(app.status).isEqualTo(application.getString(R.string.no_diff))
        assertThat(completion).isNotNull()
        val (all, diff, inconclusive) = completion!!
        assertThat(all).containsExactly(app)
        assertThat(diff).isEmpty()
        assertThat(inconclusive).isEmpty()
        assertThat(progressUpdates).contains(100)
        Mockito.verify(repository).saveResults(any(), any())
    }

    @Test
    fun `runTests detects differentiation and flags US apps for FCC alerting`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(differentiationAnalysis())
        application.resources.configuration.setLocale(Locale.US)
        val app = testApp("DiffApp")

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(app)) }

        assertThat(app.isAlertFCC).isTrue()
        assertThat(app.arcepNeedsAlerting).isFalse()
        assertThat(app.status).isEqualTo(application.getString(R.string.has_diff))
        assertThat(completion!!.second).containsExactly(app)
    }

    @Test
    fun `runTests detects differentiation and flags FR apps for ARCEP alerting instead of FCC`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(differentiationAnalysis())
        application.resources.configuration.setLocale(Locale.FRANCE)
        val app = testApp("DiffAppFR")

        runBlocking { newRunner().runTests(false, "Orange", arrayListOf(app)) }

        assertThat(app.arcepNeedsAlerting).isTrue()
        assertThat(app.isAlertFCC).isFalse()
        assertThat(completion!!.second).containsExactly(app)
    }

    @Test
    fun `runTests marks an app inconclusive when the server never returns a result`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())
        // Override retrieveResults to come back empty: the "server never finished analyzing" case
        // for a non-port test.
        runBlocking {
            `when`(repository.retrieveResults(anyString(), anyInt(), anyBoolean()))
                .thenReturn(Result.success(emptyList()))
        }
        val app = testApp("TimeoutApp")

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(app)) }

        assertThat(app.status).isEqualTo(application.getString(R.string.inconclusive))
        assertThat(completion!!.third).containsExactly(app)
    }

    @Test
    fun `runTests reports progress and the current app for every app in the list`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())
        val first = testApp("First")
        val second = testApp("Second")

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(first, second)) }

        // Progress starts at 0 and ends at 100, and the runner clears the current app when it
        // finishes so the notification stops naming a stale app.
        assertThat(iterationUpdates).isNotEmpty()
        assertThat(iterationUpdates.all { it >= 1 }).isTrue()
        assertThat(progressUpdates.first()).isEqualTo(0)
        assertThat(progressUpdates.last()).isEqualTo(100)
        assertThat(currentAppUpdates).contains(first)
        assertThat(currentAppUpdates).contains(second)
        assertThat(currentAppUpdates.last()).isNull()
        assertThat(completion!!.first).containsExactly(first, second).inOrder()
    }

    // ------------------------------------------------------------------
    // Regressions carried over from the old ReplayViewModel suite
    // ------------------------------------------------------------------

    @Test
    fun `REGRESSION concurrent runTests is rejected instead of orphaning the first run`() {
        // The old ReplayViewModel.execute() had no concurrency guard: it unconditionally did
        // `job = viewModelScope.launch(...)`, so a second call (e.g. a double-tap on "run tests")
        // overwrote the first Job reference, orphaning a run that cancel() could no longer reach
        // while both raced on shared mutable state. BackgroundTestRunner.runTests() opens with an
        // `if (isRunning) return` guard instead. This pins that guard down.
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())
        val runner = newRunner()

        // Simulate "a run is already in flight" directly, since re-entering a suspend fun from
        // its own synchronous callback isn't possible.
        val isRunningField = BackgroundTestRunner::class.java.getDeclaredField("isRunning")
        isRunningField.isAccessible = true
        isRunningField.setBoolean(runner, true)

        runBlocking { runner.runTests(false, "Verizon", arrayListOf(testApp("GuardedApp"))) }

        // The guard makes the second call a no-op: no work, no callbacks, no completion report.
        assertThat(completion).isNull()
        assertThat(statusUpdates).isEmpty()
        assertThat(progressUpdates).isEmpty()
        assertThat(errors).isEmpty()

        // And once the in-flight run finishes, a fresh run is accepted normally.
        isRunningField.setBoolean(runner, false)
        runBlocking { runner.runTests(false, "Verizon", arrayListOf(testApp("LaterApp"))) }
        assertThat(completion).isNotNull()
    }

    @Test
    fun `REGRESSION historyCount is persisted to the same prefs file the repository writes`() {
        // ReplayViewModel used to read historyCount from
        // `getSharedPreferences(ContactsContract.ProviderStatus.STATUS, ...)`, whose value is the
        // lower-case "status", while ReplayRepository.updateHistoryCount() persisted to "STATUS".
        // SharedPreferences file names are case-sensitive, so the count could never advance across
        // runs. BackgroundTestRunner uses the literal "STATUS", matching the repository.
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())

        runBlocking { newRunner().runTests(false, "Verizon", arrayListOf(testApp())) }

        // Deliberately asserted via the literal "STATUS" - the same name
        // ReplayRepository.updateHistoryCount() writes to - rather than by comparing against the
        // lower-case file, because macOS filesystems are case-insensitive by default and would
        // make that comparison pass for the wrong reason.
        val prefs = application.getSharedPreferences("STATUS", Context.MODE_PRIVATE)
        assertThat(prefs.getBoolean("hasHistoryCount", false)).isTrue()
        assertThat(prefs.contains("historyCount")).isTrue()
    }

    // ------------------------------------------------------------------
    // cancel()
    // ------------------------------------------------------------------

    @Test
    fun `cancel before any run does not throw`() {
        newRunner().cancel()
    }

    @Test
    fun `cancel mid-run stops the runner before it starts the next app`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())
        val first = testApp("First")
        val second = testApp("Second")
        second.status = "untouched"

        // cancel() is what the notification's Cancel action and the activity's back press call.
        // Fire it as soon as the first app starts; the per-app loop re-checks isRunning at the
        // top of every iteration, so the second app must never be started.
        lateinit var runner: BackgroundTestRunner
        runner = BackgroundTestRunner(
            replayRepository = repository,
            applicationContext = application,
            onProgressUpdate = { progressUpdates.add(it) },
            onStatusUpdate = { statusUpdates.add(it) },
            onCurrentAppUpdate = {
                currentAppUpdates.add(it)
                if (it === first) runner.cancel()
            },
            onIterationUpdate = { iterationUpdates.add(it) },
            onTestComplete = { all, diff, inc -> completion = Triple(all, diff, inc) },
            onError = { errors.add(it) }
        )

        runBlocking { runner.runTests(false, "Verizon", arrayListOf(first, second)) }

        assertThat(currentAppUpdates).doesNotContain(second)
        assertThat(second.status).isEqualTo("untouched")
    }
}

/**
 * Plain-Mockito replacement for `org.mockito.kotlin.any()` (that library isn't a project
 * dependency). A bare `org.mockito.ArgumentMatchers.any()` call, when its result is assigned
 * directly into a Kotlin non-null parameter slot, makes the Kotlin compiler insert an
 * `Intrinsics.checkNotNullExpressionValue` check that immediately NPEs (since `any()` really does
 * return `null` under the hood) with a confusing "any(...) must not be null" message - and worse,
 * corrupts Mockito's ThreadLocal matcher stack for every subsequent stubbing call in the same
 * test run. Registering the matcher and returning the null via an explicit `as T` cast (instead
 * of an implicit conversion) sidesteps the inserted check.
 */
private fun <T> anyObject(): T {
    Mockito.any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}
