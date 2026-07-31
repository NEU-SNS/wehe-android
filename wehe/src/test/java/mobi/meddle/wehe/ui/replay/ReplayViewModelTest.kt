package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.ContactsContract
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.model.CombinedAppJSONInfoBean
import mobi.meddle.wehe.data.model.UDPReplayInfoBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import mobi.meddle.wehe.data.repository.ServerRepository
import mobi.meddle.wehe.ui.main.viewmodels.MainDispatcherRule
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [ReplayViewModel], the largest and most stateful ViewModel in scope.
 *
 * Approach:
 *  - [ReplayRepository] and [ServerRepository] are mocked with Mockito (mockito-inline lets us
 *    mock these final Kotlin classes) so no real networking/socket I/O ever happens - every
 *    method that would talk to a server is stubbed.
 *  - A real Robolectric [Application] is used as the Context so that untouched, unmocked parts
 *    of the flow (SharedPreferences, string resources, reading the small bundled
 *    `configuration.properties` asset, ConnectivityManager) behave like the real app instead of
 *    forcing us to hand-stub dozens of `context.getString(...)` calls that don't have a fallback.
 *  - `execute()` hardcodes `viewModelScope.launch(Dispatchers.IO)` (ReplayViewModel.kt:117),
 *    which can NOT be swapped for a TestDispatcher via `Dispatchers.setMain(...)` - only the
 *    `withContext(Dispatchers.Main)` hops inside it respect the Main dispatcher override. Tests
 *    that need `doInBackground()` to actually finish therefore synchronize on real LiveData
 *    updates via a [CountDownLatch] with a generous timeout, rather than
 *    `advanceUntilIdle()`. This is a testability wart worth fixing (see final report).
 */
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

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    // ReplayViewModel.execute() hardcodes viewModelScope.launch(Dispatchers.IO) and only ever
    // hops back to Dispatchers.Main via withContext for a handful of LiveData updates
    // (ReplayViewModel.kt:117-127). Tests here synchronize on those updates via a real
    // CountDownLatch rather than kotlinx-coroutines-test's advanceUntilIdle(), so Dispatchers.Main
    // must run eagerly (UnconfinedTestDispatcher) instead of requiring manual pumping
    // (StandardTestDispatcher) - otherwise the Main hop never runs and every test times out.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(kotlinx.coroutines.test.UnconfinedTestDispatcher())

    private lateinit var application: Application
    private lateinit var repository: ReplayRepository
    private lateinit var serverRepository: ServerRepository
    private lateinit var viewModel: ReplayViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        repository = mock(ReplayRepository::class.java)
        serverRepository = mock(ServerRepository::class.java)
        `when`(repository.serverRepository).thenReturn(serverRepository)
        viewModel = ReplayViewModel(application, repository)
    }

    private fun testApp(name: String = "TestApp"): ApplicationBean = ApplicationBean().apply {
        this.name = name
        dataFile = "data.json"
        randomDataFile = "random.json"
        size = 10
        time = 10
        category = ApplicationBean.Category.VIDEO
    }

    private fun awaitReplayFinished(viewModel: ReplayViewModel, timeoutSeconds: Long = 10) {
        val latch = CountDownLatch(1)
        val observer = androidx.lifecycle.Observer<Boolean> { ongoing ->
            if (ongoing == false) latch.countDown()
        }
        viewModel.isReplayOngoing.observeForever(observer)
        try {
            // isReplayOngoing already false initially, so also bail out fast if execute() already
            // finished by the time we attach the observer.
            if (viewModel.isReplayOngoing.value == false) return
            assertThat(latch.await(timeoutSeconds, TimeUnit.SECONDS)).isTrue()
        } finally {
            viewModel.isReplayOngoing.removeObserver(observer)
        }
    }

    private fun makeNetworkAvailable(context: android.content.Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val shadowCm = shadowOf(cm)
        val network = cm.activeNetwork ?: throw IllegalStateException("no active network in Robolectric shadow")
        val capabilities = ShadowNetworkCapabilitiesBuilderCompat.withInternet()
        shadowCm.setNetworkCapabilities(network, capabilities)
    }

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    fun `initial state is idle with empty lists`() {
        assertThat(viewModel.isReplayOngoing.value).isFalse()
        assertThat(viewModel.diffApps).isEmpty()
        assertThat(viewModel.inconclusiveApps).isEmpty()
        assertThat(viewModel.carrier).isNull()
        assertThat(viewModel.isTomography).isFalse()
    }

    // ------------------------------------------------------------------
    // initializeData
    // ------------------------------------------------------------------

    @Test
    fun `initializeData stores carrier and marks every app pending`() {
        val apps = arrayListOf(testApp("A"), testApp("B"))

        viewModel.initializeData(
            runPortTests = true,
            carrier = "Verizon",
            selectedApps = apps,
            context = application
        )

        assertThat(viewModel.carrier).isEqualTo("Verizon")
        assertThat(apps.map { it.status }).containsExactly(
            application.getString(mobi.meddle.wehe.R.string.pending),
            application.getString(mobi.meddle.wehe.R.string.pending)
        )
    }

    @Test
    fun `initializeData tolerates a null app list`() {
        viewModel.initializeData(
            runPortTests = false,
            carrier = null,
            selectedApps = null,
            context = application
        )
        assertThat(viewModel.carrier).isNull()
    }

    // ------------------------------------------------------------------
    // isNetworkUnavailable
    // ------------------------------------------------------------------

    @Test
    fun `isNetworkUnavailable is true when there is no active network`() {
        val cm = application.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).setActiveNetworkInfo(null)

        assertThat(viewModel.isNetworkUnavailable(application)).isTrue()
    }

    @Test
    fun `isNetworkUnavailable is true when capabilities lack INTERNET (Robolectric default)`() {
        // Robolectric's ShadowConnectivityManager ships a default active (mobile) network but an
        // *empty* capabilities map, so getNetworkCapabilities(activeNetwork) returns null and the
        // real method falls through to `return true`. This is exercised without any extra setup.
        assertThat(viewModel.isNetworkUnavailable(application)).isTrue()
    }

    @Test
    fun `isNetworkUnavailable is false once INTERNET capability is granted`() {
        makeNetworkAvailable(application)
        assertThat(viewModel.isNetworkUnavailable(application)).isFalse()
    }

    // ------------------------------------------------------------------
    // showNoNetworkDialog
    // ------------------------------------------------------------------

    @Test
    fun `showNoNetworkDialog posts the network error dialog with exitReplays true`() {
        viewModel.initializeData(false, null, null, application)
        viewModel.showNoNetworkDialog()

        val dialog = viewModel.dialogEvent.value
        assertThat(dialog).isNotNull()
        assertThat(dialog!!.first).isEqualTo(application.getString(mobi.meddle.wehe.R.string.network_error))
        assertThat(dialog.second).isEqualTo(application.getString(mobi.meddle.wehe.R.string.text_network_error))
        assertThat(dialog.third).isTrue()
    }

    // ------------------------------------------------------------------
    // prepareRerunTests
    // ------------------------------------------------------------------

    @Test
    fun `prepareRerunTests with differentiation apps resets flags and clears both lists`() {
        viewModel.initializeData(false, null, null, application)
        val diffApp = testApp("Diff").apply {
            isTomography = true
            arcepNeedsAlerting = true
            setAlertFCC(true)
        }
        viewModel.diffApps.add(diffApp)
        viewModel.inconclusiveApps.add(testApp("Inconclusive"))
        viewModel.isTomography = true

        val result = viewModel.prepareRerunTests(isRunningDifferentiation = true)

        assertThat(result).containsExactly(diffApp)
        assertThat(diffApp.isTomography).isFalse()
        assertThat(diffApp.arcepNeedsAlerting).isFalse()
        assertThat(diffApp.isAlertFCC).isFalse()
        assertThat(diffApp.status).isEqualTo(application.getString(mobi.meddle.wehe.R.string.pending))
        assertThat(viewModel.isTomography).isFalse()
        assertThat(viewModel.diffApps).isEmpty()
        assertThat(viewModel.inconclusiveApps).isEmpty()
        assertThat(viewModel.appsList.value).containsExactly(diffApp)
    }

    @Test
    fun `prepareRerunTests with isRunningDifferentiation false uses inconclusiveApps`() {
        viewModel.initializeData(false, null, null, application)
        val inconclusiveApp = testApp("Inconclusive")
        viewModel.inconclusiveApps.add(inconclusiveApp)
        viewModel.diffApps.add(testApp("Diff"))

        val result = viewModel.prepareRerunTests(isRunningDifferentiation = false)

        assertThat(result).containsExactly(inconclusiveApp)
        assertThat(viewModel.diffApps).isEmpty()
        assertThat(viewModel.inconclusiveApps).isEmpty()
    }

    // ------------------------------------------------------------------
    // cancel()
    // ------------------------------------------------------------------

    @Test
    fun `cancel before execute does not throw and leaves isReplayOngoing false`() {
        viewModel.cancel()
        assertThat(viewModel.isReplayOngoing.value).isFalse()
    }

    // ------------------------------------------------------------------
    // execute() - fast, deterministic paths
    // ------------------------------------------------------------------

    @Test
    fun `execute with no network shows the network dialog and resets isReplayOngoing`() {
        // Network unavailable is the Robolectric default (see test above) - no extra stubbing.
        viewModel.initializeData(false, "Verizon", arrayListOf(testApp()), application)

        viewModel.execute()
        assertThat(viewModel.isReplayOngoing.value).isTrue() // set synchronously before launch

        awaitReplayFinished(viewModel)

        val dialog = viewModel.dialogEvent.value
        assertThat(dialog).isNotNull()
        assertThat(dialog!!.first).isEqualTo(application.getString(mobi.meddle.wehe.R.string.network_error))
    }

    @Test
    fun `BUG calling execute twice in a row silently discards the first Job reference`() {
        // ReplayViewModel.execute() (ReplayViewModel.kt:114-128) has no guard against concurrent
        // invocation - it unconditionally does `job = viewModelScope.launch(...)`. If execute()
        // is called again while a previous run is still active (e.g. a double-tap on the "run
        // tests" button before the button is disabled), the previous Job is simply overwritten
        // and becomes unreachable from cancel() - it keeps running in the background with no way
        // for the UI to cancel or observe it directly, and two runTest()/doInBackground()
        // executions can race against shared mutable ViewModel state (selectedApps, diffApps,
        // inconclusiveApps, results, historyCount, etc). This test proves the reference is
        // discarded (not, e.g., ignored or queued); it does not attempt to prove the race itself
        // since that requires a slow suspend point, which is out of scope for a fast unit test.
        viewModel.initializeData(false, null, arrayListOf(testApp()), application)

        viewModel.execute()
        val jobField = ReplayViewModel::class.java.getDeclaredField("job")
        jobField.isAccessible = true
        val firstJob = jobField.get(viewModel) as Job?

        viewModel.execute()
        val secondJob = jobField.get(viewModel) as Job?

        assertThat(firstJob).isNotNull()
        assertThat(secondJob).isNotNull()
        assertThat(firstJob).isNotSameInstanceAs(secondJob)
        // cancel() can now only ever reach the second job - the first is orphaned.
        awaitReplayFinished(viewModel)
    }

    // ------------------------------------------------------------------
    // execute() - historyCount persistence bug (ContactsContract.STATUS vs "STATUS")
    // ------------------------------------------------------------------

    @Test
    fun `BUG historyCount shared prefs file name is case-mismatched between ViewModel and Repository`() {
        // ReplayViewModel.kt:8 imports android.provider.ContactsContract.ProviderStatus.STATUS
        // and uses it at line 413 as a SharedPreferences file name:
        //     settings = applicationContext.getSharedPreferences(STATUS, Context.MODE_PRIVATE)
        // That constant's real value is the lower-case string "status" (an unrelated
        // ContactsContract column name, almost certainly picked by IDE autocomplete instead of a
        // literal). Meanwhile ReplayRepository.updateHistoryCount() (ReplayRepository.kt:705)
        // persists the *updated* history count to a *different*, differently-cased file:
        //     context.getSharedPreferences("STATUS", Context.MODE_PRIVATE)
        // SharedPreferences file names are case-sensitive, so every historyCount increment made
        // via updateHistoryCount() is written to "STATUS" but ReplayViewModel always reads back
        // from "status" on the next run - meaning the persisted historyCount can never actually
        // advance across separate execute() invocations/app restarts once bootstrapped.
        assertThat(ContactsContract.ProviderStatus.STATUS).isEqualTo("status")
        assertThat(ContactsContract.ProviderStatus.STATUS).isNotEqualTo("STATUS")
    }

    // ------------------------------------------------------------------
    // execute() - full doInBackground() orchestration with a fully-stubbed repository
    // ------------------------------------------------------------------

    private fun stubSuccessfulSetup() {
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), org.mockito.Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.success(true))
            `when`(serverRepository.getPublicIP(anyString())).thenReturn("9.8.7.6")
        }
    }

    private fun stubSingleAppReplay(analysis: ReplayRepository.ResultAnalysis) {
        val appData = CombinedAppJSONInfoBean() // tcpCSPs empty -> isTCP() == false
        `when`(repository.loadAppDataForReplay(anyObject(), anyString())).thenReturn(appData)
        `when`(repository.setupSideChannels(anyObject())).thenReturn(
            Pair(ArrayList(), ArrayList())
        )
        `when`(repository.updateHistoryCount(anyInt())).thenReturn(1)
        `when`(
            repository.initiateTestWithServer(
                anyObject(), anyObject(), org.mockito.Mockito.nullable(String::class.java), anyInt(), anyInt(),
                anyBoolean(), anyBoolean(), anyString()
            )
        ).thenReturn(Result.success(ArrayList()))
        `when`(repository.getPortMappingFromServer(anyObject())).thenReturn(Pair(ArrayList(), ArrayList<UDPReplayInfoBean>()))
        `when`(repository.createTCPClients(anyObject(), anyObject())).thenReturn(ArrayList())
        `when`(repository.createUDPClients(anyObject())).thenReturn(ArrayList())
        `when`(repository.runPacketQueue(anyObject(), anyInt(), anyObject(), anyObject(), anyObject(), anyObject(), anyObject(), anyObject())).thenReturn(0.0)
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

    @Test
    fun `execute runs a full no-differentiation replay end-to-end`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(noDifferentiationAnalysis())

        val app = testApp("NoDiffApp")
        viewModel.initializeData(false, "Verizon", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        assertThat(viewModel.diffApps).isEmpty()
        assertThat(viewModel.inconclusiveApps).isEmpty()
        assertThat(app.status).isEqualTo(application.getString(mobi.meddle.wehe.R.string.no_diff))
        assertThat(viewModel.showRerunTomoButtonsEvent.value).isNull() // never fired - nothing to rerun
        val dialog = viewModel.dialogEvent.value
        assertThat(dialog!!.first).isEqualTo(application.getString(mobi.meddle.wehe.R.string.replay_finished_title))
        Mockito.verify(repository).saveResults(any(), any())
    }

    @Test
    fun `execute detects differentiation, flags US apps for FCC alerting, and offers a rerun`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(differentiationAnalysis())
        application.resources.configuration.setLocale(Locale.US)

        val app = testApp("DiffApp")
        viewModel.initializeData(false, "Verizon", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        assertThat(viewModel.diffApps).containsExactly(app)
        assertThat(app.isAlertFCC).isTrue()
        assertThat(app.arcepNeedsAlerting).isFalse()
        assertThat(app.status).isEqualTo(application.getString(mobi.meddle.wehe.R.string.has_diff))
        assertThat(viewModel.showRerunTomoButtonsEvent.value).isTrue()
    }

    @Test
    fun `execute detects differentiation and flags FR apps for ARCEP alerting instead of FCC`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        stubSingleAppReplay(differentiationAnalysis())
        application.resources.configuration.setLocale(Locale.FRANCE)

        val app = testApp("DiffAppFR")
        viewModel.initializeData(false, "Orange", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        assertThat(viewModel.diffApps).containsExactly(app)
        assertThat(app.arcepNeedsAlerting).isTrue()
        assertThat(app.isAlertFCC).isFalse()
    }

    @Test
    fun `execute marks an app inconclusive when the server never returns a result and not a port test`() {
        makeNetworkAvailable(application)
        stubSuccessfulSetup()
        // loadAppDataForReplay / setupSideChannels / initiateTestWithServer etc all still needed:
        stubSingleAppReplay(noDifferentiationAnalysis())
        // ...but override retrieveResults to come back empty, which is the "server never
        // finished analyzing" case for a non-port test.
        runBlocking {
            `when`(repository.retrieveResults(anyString(), anyInt(), anyBoolean()))
                .thenReturn(Result.success(emptyList()))
        }

        val app = testApp("TimeoutApp")
        viewModel.initializeData(false, "Verizon", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        assertThat(viewModel.inconclusiveApps).containsExactly(app)
        assertThat(app.status).isEqualTo(application.getString(mobi.meddle.wehe.R.string.inconclusive))
    }

    @Test
    fun `execute toasts and marks apps unavailable when server setup fails`() {
        makeNetworkAvailable(application)
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), org.mockito.Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.failure(Exception("boom")))
        }

        val app = testApp("UnreachableApp")
        viewModel.initializeData(false, "Verizon", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        assertThat(viewModel.toastEvent.value).isEqualTo(application.getString(mobi.meddle.wehe.R.string.server_unavailable))
        assertThat(app.status).isEqualTo(application.getString(mobi.meddle.wehe.R.string.server_unavailable))
    }

    @Test
    fun `execute shows a connection error dialog when the public IP cannot be resolved`() {
        makeNetworkAvailable(application)
        `when`(repository.servers).thenReturn(arrayListOf("1.2.3.4"))
        `when`(repository.isMlabServerUsed()).thenReturn(false)
        runBlocking {
            `when`(
                repository.setupServersAndCertificates(
                    anyString(), org.mockito.Mockito.nullable(String::class.java), anyInt(), anyBoolean()
                )
            ).thenReturn(Result.success(true))
            `when`(serverRepository.getPublicIP(anyString())).thenReturn("-1")
        }

        val app = testApp("NoIpApp")
        viewModel.initializeData(false, "Verizon", arrayListOf(app), application)

        viewModel.execute()
        awaitReplayFinished(viewModel)

        val dialog = viewModel.dialogEvent.value
        assertThat(dialog).isNotNull()
        assertThat(dialog!!.first).isEqualTo(application.getString(mobi.meddle.wehe.R.string.simple_error))
        assertThat(dialog.second).isEqualTo(application.getString(mobi.meddle.wehe.R.string.error_no_connection))
    }
}

/**
 * Small helper isolating the (slightly awkward) construction of a [NetworkCapabilities] instance
 * that reports internet access. `NetworkCapabilities.Builder`/`addCapability(int)` are real,
 * usable methods at runtime (Robolectric's own ShadowNetworkCapabilities calls them via
 * reflection - see ShadowNetworkCapabilities.addCapability()), but are not present in the public
 * compileSdk stub jar, so we go through reflection here the same way Robolectric's shadow does
 * internally.
 */
private object ShadowNetworkCapabilitiesBuilderCompat {
    fun withInternet(): NetworkCapabilities {
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        val addCapability = NetworkCapabilities::class.java
            .getDeclaredMethod("addCapability", Int::class.javaPrimitiveType)
        addCapability.isAccessible = true
        addCapability.invoke(capabilities, NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return capabilities
    }
}
