package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import mobi.meddle.wehe.R
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

    var selectedApps: ArrayList<ApplicationBean>? = null // Apps to run

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

    // Activity reference for context
    private var applicationContext: Context = application.applicationContext

    /**
     * Initialize data with parameters from activity
     */
    fun initializeData(
        runPortTests: Boolean,
        carrier: String?,
        selectedApps: ArrayList<ApplicationBean>?,
        context: Context
    ) {
        this.runPortTests = runPortTests
        this.carrier = carrier
        this.selectedApps = selectedApps
        this.applicationContext = context

        selectedApps?.let {
            for (app in it) {
                app.status = context.getString(R.string.pending) ?: "Waiting to start"
            }
        }
    }

    /**
     * Check network availability
     */
    fun isNetworkUnavailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Check if connectivityManager is not null
        if (connectivityManager != null) {
            // Get the active network
            val activeNetwork = connectivityManager.activeNetwork ?: return true
            // If there is no active network, the network is unavailable

            // Get network capabilities and check for connectivity
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork)
            // Check if the network is connected to Wi-Fi or mobile data
            if (networkCapabilities != null) {
                // Return true if the network is connected to the internet (either Wi-Fi or mobile data)
                return !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        // If the connectivityManager is null, consider the network unavailable
        return true
    }

    /**
     * Set test parameters
     */
    fun setTestParameters(
        runPortTests: Boolean,
        carrier: String?,
        selectedApps: ArrayList<ApplicationBean>
    ) {
        this.runPortTests = runPortTests
        this.carrier = carrier
        this.selectedApps = selectedApps
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