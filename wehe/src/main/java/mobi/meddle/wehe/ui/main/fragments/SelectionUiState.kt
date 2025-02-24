package mobi.meddle.wehe.ui.main.fragments
import mobi.meddle.wehe.data.bean.ApplicationBean

// SelectionUiState.kt
data class SelectionUiState(
    val isLoading: Boolean = true,
    val apps: List<ApplicationBean> = emptyList(),
    val error: String? = null
)
