package mobi.meddle.wehe.ui.main.viewmodels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Shared JUnit rule that swaps [kotlinx.coroutines.Dispatchers.Main] for a [TestDispatcher] so
 * that `viewModelScope.launch { ... }` bodies which use the default Main dispatcher can be driven
 * deterministically from tests via `advanceUntilIdle()`.
 *
 * Reused across the viewmodels and replay test packages.
 */
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        super.starting(description)
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        super.finished(description)
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
