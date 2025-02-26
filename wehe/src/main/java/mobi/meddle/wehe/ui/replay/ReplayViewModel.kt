package mobi.meddle.wehe.ui.replay

import androidx.lifecycle.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.model.TestProgressState
import mobi.meddle.wehe.data.repository.UserPreferencesRepository
import javax.inject.Inject

/**
 * ViewModel for replay tests
 */
class ReplayViewModel @Inject constructor(
    private val runTestUseCase: RunTestUseCase,
    private val serverConnectionUseCase: ServerConnectionUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _testProgressState = MutableStateFlow(TestProgressState())
    val testProgressState: StateFlow<TestProgressState> = _testProgressState

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents

    private val _isNetworkAvailable = MutableStateFlow(true)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable

    private val _selectedApps = MutableStateFlow<List<ApplicationBean>>(emptyList())
    private val _differentiatingApps = MutableStateFlow<List<ApplicationBean>>(emptyList())
    private val _inconclusiveApps = MutableStateFlow<List<ApplicationBean>>(emptyList())

    val differentiatingApps: StateFlow<List<ApplicationBean>> = _differentiatingApps
    val inconclusiveApps: StateFlow<List<ApplicationBean>> = _inconclusiveApps

    fun setNetworkStatus(isAvailable: Boolean) {
        _isNetworkAvailable.value = isAvailable
    }

    fun setSelectedApps(apps: List<ApplicationBean>) {
        _selectedApps.value = apps
    }

    fun setInconclusiveApps(apps: List<ApplicationBean>) {
        _inconclusiveApps.value = apps
    }

    fun getInconclusiveApps() {
        viewModelScope.launch {
            try {

            } catch (e : Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getNetworkStatus() {
        viewModelScope.launch {
            try {
                var isAvailable : Boolean = false

                setNetworkStatus(isAvailable)
            } catch (e : Exception) {
                e.printStackTrace()
            }
        }
    }
}