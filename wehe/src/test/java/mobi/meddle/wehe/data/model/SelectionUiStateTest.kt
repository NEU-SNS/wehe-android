package mobi.meddle.wehe.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * SelectionUiState is a pure data holder with default values - light construction/default test.
 */
class SelectionUiStateTest {

    @Test
    fun defaultConstructor_hasExpectedDefaults() {
        val state = SelectionUiState()

        assertThat(state.isLoading).isTrue()
        assertThat(state.apps).isEmpty()
        assertThat(state.error).isNull()
    }

    @Test
    fun constructor_withArgs_setsAllFields() {
        val bean = ApplicationBean()
        bean.name = "App1"
        val state = SelectionUiState(isLoading = false, apps = listOf(bean), error = "oops")

        assertThat(state.isLoading).isFalse()
        assertThat(state.apps).hasSize(1)
        assertThat(state.apps[0].name).isEqualTo("App1")
        assertThat(state.error).isEqualTo("oops")
    }

    @Test
    fun copy_changesOnlyRequestedField() {
        val state = SelectionUiState()
        val loaded = state.copy(isLoading = false)

        assertThat(loaded.isLoading).isFalse()
        assertThat(loaded.apps).isEqualTo(state.apps)
        assertThat(loaded.error).isEqualTo(state.error)
    }
}
