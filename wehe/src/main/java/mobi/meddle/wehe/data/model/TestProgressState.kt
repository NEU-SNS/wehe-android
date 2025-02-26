package mobi.meddle.wehe.data.model

/**
 * UI state for test progress
 */
data class TestProgressState(
    val isRunning: Boolean = false,
    val currentApp: String = "",
    val currentStatus: String = "",
    val progress: Int = 0,
    val totalApps: Int = 0,
    val currentAppIndex: Int = 0,
    val isFirstReplay: Boolean = true
)
