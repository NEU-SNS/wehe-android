package mobi.meddle.wehe.ui.replay

/**
 * Sealed class representing UI events emitted by the ViewModel.
 */
sealed class UiEvent {
    /**
     * Event when tests have completed.
     */
    data class TestsCompleted<ApplicationBean>(
        val message: String,
        val differentiatingApps: List<ApplicationBean>,
        val inconclusiveApps: List<ApplicationBean>
    ) : UiEvent()

    /**
     * Event when an error occurs.
     */
    data class ShowError(
        val title: String,
        val message: String,
        val shouldExit: Boolean = false
    ) : UiEvent()

    /**
     * Event to show a toast message.
     */
    data class ShowToast(val message: String) : UiEvent()

    /**
     * Event when network is unavailable.
     */
    object NetworkUnavailable : UiEvent()

    /**
     * Event to update the app bar title.
     */
    data class UpdateTitle(val title: String) : UiEvent()

    /**
     * Event to display rerun buttons.
     */
    object DisplayRerunButtons : UiEvent()
}