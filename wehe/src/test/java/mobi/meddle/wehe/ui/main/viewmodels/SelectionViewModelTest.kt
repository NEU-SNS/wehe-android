package mobi.meddle.wehe.ui.main.viewmodels

import android.content.Context
import android.content.res.AssetManager
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mobi.meddle.wehe.data.model.ApplicationBean
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Tests for [SelectionViewModel].
 *
 * Uses a real [SavedStateHandle] (plain JVM object, no Android framework needed for that part)
 * and Robolectric only so [SelectionViewModel.loadInitialData] can read the real
 * `apps_list.json` asset bundled with the app via a real [Context] - this both avoids having to
 * hand-roll a fake JSON fixture that could drift from the real schema, and exercises the parsing
 * logic against production data.
 */
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SelectionViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context

    // NOTE: these must stay in sync with the private KEY_* constants in SelectionViewModel's
    // companion object - they are not exposed for tests, which is itself a minor testability
    // wart (see report).
    private val keySelectedApps = "selected_apps"
    private val keyCarrierDisplay = "carrier_display"
    private val keyAppToggleStates = "app_toggle_states"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()) = SelectionViewModel(handle)

    private fun amazonBean(): ApplicationBean = ApplicationBean().apply {
        name = "Amazon Prime Video"
        size = 21
        time = 22
        image = "amazon"
        dataFile = "Amazon_02272024.pcap_client_all.json"
        randomDataFile = "AmazonRandom_02272024.pcap_client_all.json"
        category = ApplicationBean.Category.VIDEO
    }

    @Test
    fun `initial state is empty and loading`() {
        val viewModel = newViewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        assertThat(viewModel.uiState.value.apps).isEmpty()
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.payloadSize.value).isEqualTo(0)
        assertThat(viewModel.carrierDisplay.value).isNull()
        assertThat(viewModel.currentTabIndex.value).isEqualTo(0)
        assertThat(viewModel.appToggleStates).isEmpty()
    }

    @Test
    fun `loadInitialData populates apps from the real apps_list json asset`() = runTest {
        val viewModel = newViewModel()

        viewModel.loadInitialData(context)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.error).isNull()
        // apps_list.json currently ships 55 apps/ports across 5 categories.
        assertThat(state.apps).hasSize(55)
        assertThat(state.apps.map { it.category }.toSet()).containsExactly(
            ApplicationBean.Category.VIDEO,
            ApplicationBean.Category.MUSIC,
            ApplicationBean.Category.CONFERENCING,
            ApplicationBean.Category.SMALL_PORT,
            ApplicationBean.Category.LARGE_PORT
        )
    }

    @Test
    fun `loadInitialData is a no-op once apps are already loaded`() = runTest {
        val viewModel = newViewModel()
        viewModel.loadInitialData(context)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.apps).isNotEmpty()

        // Second call is guarded by `uiState.value.apps.isEmpty()`. Pass a context that would
        // blow up if it were actually used, to prove the guard short-circuits before touching it.
        val poisonedContext = Mockito.mock(Context::class.java)
        Mockito.`when`(poisonedContext.assets).thenThrow(RuntimeException("should not be called"))

        viewModel.loadInitialData(poisonedContext)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.apps).hasSize(55)
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `loadInitialData surfaces an error when the asset cannot be read`() = runTest {
        val viewModel = newViewModel()
        val brokenContext = Mockito.mock(Context::class.java)
        val brokenAssets = Mockito.mock(AssetManager::class.java)
        Mockito.`when`(brokenContext.assets).thenReturn(brokenAssets)
        Mockito.`when`(brokenAssets.open(anyString())).thenThrow(IOException("missing asset"))

        viewModel.loadInitialData(brokenContext)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.apps).isEmpty()
        assertThat(state.error).isEqualTo("missing asset")
    }

    @Test
    fun `toggleApp selecting an app increases payloadSize by its size for the matching tab`() {
        val viewModel = newViewModel()
        val app = amazonBean() // VIDEO, tab 0, default (non-port) test type

        viewModel.toggleApp(app, true)

        assertThat(viewModel.appToggleStates[app]).isTrue()
        assertThat(viewModel.payloadSize.value).isEqualTo(21)
        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(app)
    }

    @Test
    fun `toggleApp deselecting an app restores payloadSize to zero`() {
        val viewModel = newViewModel()
        val app = amazonBean()

        viewModel.toggleApp(app, true)
        viewModel.toggleApp(app, false)

        assertThat(viewModel.appToggleStates[app]).isFalse()
        assertThat(viewModel.payloadSize.value).isEqualTo(0)
        assertThat(viewModel.getFilteredSelectedApps()).isEmpty()
    }

    @Test
    fun `BUG toggling the same app selected twice in a row double-counts it`() {
        // toggleApp() unconditionally does `_selectedApps.add(app)` whenever isSelected is true,
        // with no check for whether the app is already present (SelectionViewModel.kt around
        // toggleApp(), ~line 163-178). Two consecutive "select" calls for the same app (e.g. a
        // duplicate click/recomposition event before a deselect happens) silently insert it
        // twice, inflating the reported payload size. This test documents that bug; if a
        // dedup check is added, this test should be updated to expect a single count.
        val viewModel = newViewModel()
        val app = amazonBean()

        viewModel.toggleApp(app, true)
        viewModel.toggleApp(app, true) // select again without deselecting first

        assertThat(viewModel.payloadSize.value).isEqualTo(21 * 2)
        assertThat(viewModel.getFilteredSelectedApps()).hasSize(2)

        // A single subsequent deselect only removes one occurrence (List.remove semantics),
        // leaving the app still selected/counted - compounding the confusion for callers.
        viewModel.toggleApp(app, false)
        assertThat(viewModel.getFilteredSelectedApps()).hasSize(1)
        assertThat(viewModel.payloadSize.value).isEqualTo(21)
    }

    @Test
    fun `getFilteredSelectedApps filters by test type and current tab`() {
        val viewModel = newViewModel()
        val video = amazonBean()
        val music = ApplicationBean().apply {
            name = "Spotify"; size = 5; category = ApplicationBean.Category.MUSIC
        }
        val smallPort = ApplicationBean().apply {
            name = "port_443"; size = 1; category = ApplicationBean.Category.SMALL_PORT
        }

        viewModel.toggleApp(video, true)
        viewModel.toggleApp(music, true)
        viewModel.toggleApp(smallPort, true)

        // Default: app test (not port), tab 0 -> VIDEO only.
        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(video)

        viewModel.setCurrentTabIndex(1) // MUSIC tab for app tests
        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(music)

        viewModel.setTestType(true) // switch to port test
        viewModel.setCurrentTabIndex(0) // SMALL_PORT tab
        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(smallPort)

        viewModel.setCurrentTabIndex(1) // LARGE_PORT tab - none selected
        assertThat(viewModel.getFilteredSelectedApps()).isEmpty()
    }

    @Test
    fun `getFilteredSelectedApps falls back to all apps of the test type for an out-of-range tab index`() {
        val viewModel = newViewModel()
        val video = amazonBean()
        val music = ApplicationBean().apply {
            name = "Spotify"; size = 5; category = ApplicationBean.Category.MUSIC
        }
        viewModel.toggleApp(video, true)
        viewModel.toggleApp(music, true)

        viewModel.setCurrentTabIndex(99) // not a recognized tab for app tests

        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(video, music)
    }

    @Test
    fun `setCarrierDisplay updates state and persists to SavedStateHandle`() {
        val handle = SavedStateHandle()
        val viewModel = newViewModel(handle)

        viewModel.setCarrierDisplay("Verizon")

        assertThat(viewModel.carrierDisplay.value).isEqualTo("Verizon")
        assertThat(handle.get<String>(keyCarrierDisplay)).isEqualTo("Verizon")
    }

    @Test
    fun `init restores selectedApps and carrierDisplay from a previously saved handle`() {
        val app = amazonBean()
        val handle = SavedStateHandle(
            mapOf(
                keySelectedApps to listOf(app),
                keyCarrierDisplay to "AT&T"
            )
        )

        val viewModel = newViewModel(handle)

        assertThat(viewModel.carrierDisplay.value).isEqualTo("AT&T")
        assertThat(viewModel.getFilteredSelectedApps()).containsExactly(app)
    }

    @Test
    fun `BUG app toggle state restoration in init is dead code because apps are not loaded yet`() {
        // The init{} block tries to restore _appToggleStates from the SavedStateHandle by
        // iterating `uiState.value.apps` (SelectionViewModel.kt lines ~56-65), but at
        // construction time uiState.value.apps is always emptyList() (the default
        // SelectionUiState()) since loadInitialData() hasn't run yet. The forEach body never
        // executes, so this restoration path is a permanent no-op - only the separate
        // restoreToggleStates() call from loadInitialData() ever actually restores toggle state.
        val app = amazonBean()
        val handle = SavedStateHandle(
            mapOf(keyAppToggleStates to mapOf(app.name to true))
        )

        val viewModel = newViewModel(handle)

        assertThat(viewModel.appToggleStates).isEmpty()
    }

    @Test
    fun `BUG restoring both selectedApps and appToggleStates after loadInitialData double-counts a re-selected app`() = runTest {
        // Reproduces a real duplication bug end-to-end:
        // 1) SavedStateHandle already has the app in KEY_SELECTED_APPS (e.g. restored across a
        //    process restart / config change).
        // 2) SavedStateHandle also has it marked "selected" in KEY_APP_TOGGLE_STATES.
        // 3) loadInitialData() parses apps fresh from JSON and calls restoreToggleStates(),
        //    which unconditionally does `_selectedApps.add(app)` for every toggle-state entry
        //    marked true (SelectionViewModel.kt ~lines 143-156), without checking whether that
        //    (logical) app is already present in _selectedApps from step 1.
        // Net effect: the same app is counted twice in payloadSize/getFilteredSelectedApps().
        val restoredInstance = amazonBean() // simulates the ApplicationBean restored via Parcelable
        val handle = SavedStateHandle(
            mapOf(
                keySelectedApps to listOf(restoredInstance),
                keyAppToggleStates to mapOf("Amazon Prime Video" to true)
            )
        )
        val viewModel = newViewModel(handle)

        viewModel.loadInitialData(context) // parses real apps_list.json (contains "Amazon Prime Video")
        advanceUntilIdle()

        val amazonEntries = viewModel.getFilteredSelectedApps().filter { it.name == "Amazon Prime Video" }
        assertThat(amazonEntries).hasSize(2) // BUG: should be 1 - the same app selected once
        assertThat(viewModel.payloadSize.value).isEqualTo(21 * 2) // BUG: payload size is doubled
    }
}
