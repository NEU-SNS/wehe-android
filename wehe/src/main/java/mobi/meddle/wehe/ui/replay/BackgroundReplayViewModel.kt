package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import javax.inject.Inject

/**
 * ViewModel for managing background replay tests state and UI
 */
@HiltViewModel
class BackgroundReplayViewModel @Inject constructor(
    application: Application,
    private val repository: ReplayRepository
) : AndroidViewModel(application) {

    // Test parameters
    var runPortTests: Boolean = false
        private set
    var carrier: String? = null
        private set
    private val _selectedApps = MutableLiveData<List<ApplicationBean>>()
    val selectedApps: List<ApplicationBean>?
        get() = _selectedApps.value

    // Test status
    private val _isReplayOngoing = MutableLiveData<Boolean>(false)
    val isReplayOngoing: LiveData<Boolean> = _isReplayOngoing

    // Current app being tested
    private val _currentTestingApp = MutableLiveData<ApplicationBean?>()
    val currentTestingApp: LiveData<ApplicationBean?> = _currentTestingApp

    // Test progress
    private val _progress = MutableLiveData<Int>(0)
    val progress: LiveData<Int> = _progress

    // Test status
    private val _status = MutableLiveData<Pair<String, String>>()
    val status: LiveData<Pair<String, String>> = _status

    // Test results
    private val _testResults = MutableLiveData<Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>>()
    val testResults: LiveData<Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>> = _testResults

    /**
     * Set test parameters
     */
    fun setTestParameters(
        runPortTests: Boolean,
        carrier: String?,
        selectedApps: List<ApplicationBean>
    ) {
        this.runPortTests = runPortTests
        this.carrier = carrier
        _selectedApps.value = selectedApps
    }

    /**
     * Update replay ongoing status
     */
    fun setReplayOngoing(isOngoing: Boolean) {
        _isReplayOngoing.value = isOngoing
    }

    /**
     * Update current testing app
     */
    fun setCurrentTestingApp(app: ApplicationBean?) {
        _currentTestingApp.value = app
    }

    /**
     * Update progress
     */
    fun setProgress(progress: Int) {
        _progress.value = progress
    }

    /**
     * Update status
     */
    fun setStatus(status: Pair<String, String>) {
        _status.value = status
    }

    /**
     * Set test results
     */
    fun setTestResults(results: Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>) {
        _testResults.value = results
    }
}