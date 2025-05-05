package mobi.meddle.wehe.data.model

// SelectionUiState.kt
data class SelectionUiState(
    val isLoading: Boolean = true,
    val apps: List<ApplicationBean> = emptyList(),
    val error: String? = null
)
