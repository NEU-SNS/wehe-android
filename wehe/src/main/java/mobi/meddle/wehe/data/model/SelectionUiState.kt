package mobi.meddle.wehe.data.model


/**
 * Represents the UI state for the selection screen, which includes a list of applications,
 */
data class SelectionUiState(
    val isLoading: Boolean = true,
    val apps: List<ApplicationBean> = emptyList(),
    val error: String? = null
)
