package mobi.meddle.wehe.ui.replay.viewmodel

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mobi.meddle.wehe.R
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import mobi.meddle.wehe.ui.main.viewmodels.MainDispatcherRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [BackgroundReplayViewModel], the UI-state half of the background replay feature.
 *
 * This suite replaces the state/lifecycle half of the old `ReplayViewModelTest`. When replays
 * moved to a background service, `ReplayViewModel` was split in two: the UI state it exposed to
 * the activity became this class, and the actual replay orchestration became
 * `BackgroundTestRunner` (covered by `BackgroundTestRunnerTest`). The assertions below are the
 * same behaviours the old suite pinned down, retargeted onto the class that now owns them.
 *
 * [ReplayRepository] is mocked so nothing here touches the network - this ViewModel never calls
 * the repository on any path these tests exercise, it only holds state the service pushes into it.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundReplayViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    // updateAppStatus()/cancel() hop through viewModelScope + Dispatchers.Main, so Main must run
    // eagerly for those effects to be observable without manual pumping.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(kotlinx.coroutines.test.UnconfinedTestDispatcher())

    private lateinit var application: Application
    private lateinit var repository: ReplayRepository
    private lateinit var viewModel: BackgroundReplayViewModel

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        repository = mock(ReplayRepository::class.java)
        viewModel = BackgroundReplayViewModel(application, repository)
    }

    private fun testApp(name: String = "TestApp"): ApplicationBean = ApplicationBean().apply {
        this.name = name
        dataFile = "data.json"
        randomDataFile = "random.json"
        size = 10
        time = 10
        category = ApplicationBean.Category.VIDEO
    }

    private fun makeNetworkAvailable(context: android.content.Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val shadowCm = shadowOf(cm)
        val network = cm.activeNetwork
            ?: throw IllegalStateException("no active network in Robolectric shadow")
        shadowCm.setNetworkCapabilities(network, ShadowNetworkCapabilitiesBuilderCompat.withInternet())
    }

    // ------------------------------------------------------------------
    // Initial state
    // ------------------------------------------------------------------

    @Test
    fun `initial state is idle with empty lists`() {
        assertThat(viewModel.isReplayOngoing.value).isFalse()
        assertThat(viewModel.diffApps).isEmpty()
        assertThat(viewModel.inconclusiveApps).isEmpty()
        assertThat(viewModel.allApps).isEmpty()
        assertThat(viewModel.carrier).isNull()
        assertThat(viewModel.progress.value).isEqualTo(0)
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
        assertThat(viewModel.runPortTests).isTrue()
        assertThat(apps.map { it.status }).containsExactly(
            application.getString(R.string.pending),
            application.getString(R.string.pending)
        )
        // The activity's RecyclerView binds to appsList, so it has to be seeded up front.
        assertThat(viewModel.appsList.value).containsExactly(apps[0], apps[1]).inOrder()
        assertThat(viewModel.allApps).containsExactly(apps[0], apps[1]).inOrder()
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
        assertThat(viewModel.selectedApps).isNull()
        assertThat(viewModel.appsList.value).isNull()
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
        // real method falls through to `return true`. Exercised without any extra setup.
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
    fun `showNoNetworkDialog posts the localized network error dialog with exitReplays true`() {
        viewModel.initializeData(false, null, null, application)
        viewModel.showNoNetworkDialog()

        val dialog = viewModel.dialogEvent.value
        assertThat(dialog).isNotNull()
        // Must come from string resources, not hardcoded English - the app ships an fr-FR locale.
        assertThat(dialog!!.first).isEqualTo(application.getString(R.string.network_error))
        assertThat(dialog.second).isEqualTo(application.getString(R.string.text_network_error))
        assertThat(dialog.third).isTrue()
    }

    // ------------------------------------------------------------------
    // prepareRerunTests
    // ------------------------------------------------------------------

    @Test
    fun `prepareRerunTests with differentiation apps resets status and clears both lists`() {
        viewModel.initializeData(false, null, null, application)
        val diffApp = testApp("Diff").apply { status = "has diff" }
        viewModel.diffApps.add(diffApp)
        viewModel.inconclusiveApps.add(testApp("Inconclusive"))

        val result = viewModel.prepareRerunTests(isRunningDifferentiation = true)

        assertThat(result).containsExactly(diffApp)
        assertThat(diffApp.status).isEqualTo(application.getString(R.string.pending))
        assertThat(viewModel.selectedApps).containsExactly(diffApp)
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
    fun `cancel before any run does not throw and leaves isReplayOngoing false`() {
        viewModel.cancel()
        assertThat(viewModel.isReplayOngoing.value).isFalse()
    }

    @Test
    fun `cancel marks still-pending apps cancelled and clears the current app`() {
        val pendingApp = testApp("Pending")
        val finishedApp = testApp("Finished")
        viewModel.initializeData(false, "Verizon", arrayListOf(pendingApp, finishedApp), application)
        // initializeData marks both pending; give one a terminal status so we can prove only
        // unfinished apps get rewritten.
        finishedApp.status = application.getString(R.string.no_diff)
        viewModel.setReplayOngoing(true)

        viewModel.cancel()

        assertThat(viewModel.isReplayOngoing.value).isFalse()
        assertThat(pendingApp.status).isEqualTo(application.getString(R.string.cancel_test))
        assertThat(finishedApp.status).isEqualTo(application.getString(R.string.no_diff))
        assertThat(viewModel.currentTestingApp.value).isNull()
    }

    // ------------------------------------------------------------------
    // setReplayOngoing
    // ------------------------------------------------------------------

    @Test
    fun `setReplayOngoing drives the isReplayOngoing flag the activity gates its back press on`() {
        viewModel.setReplayOngoing(true)
        assertThat(viewModel.isReplayOngoing.value).isTrue()

        viewModel.setReplayOngoing(false)
        assertThat(viewModel.isReplayOngoing.value).isFalse()
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
internal object ShadowNetworkCapabilitiesBuilderCompat {
    fun withInternet(): NetworkCapabilities {
        val capabilities = org.robolectric.shadows.ShadowNetworkCapabilities.newInstance()
        val addCapability = NetworkCapabilities::class.java
            .getDeclaredMethod("addCapability", Int::class.javaPrimitiveType)
        addCapability.isAccessible = true
        addCapability.invoke(capabilities, NetworkCapabilities.NET_CAPABILITY_INTERNET)
        return capabilities
    }
}
