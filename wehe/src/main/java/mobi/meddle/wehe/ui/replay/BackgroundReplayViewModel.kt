package mobi.meddle.wehe.ui.replay

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Additional LiveData to match ReplayActivity functionality
    private val _statusUpdateEvent = MutableLiveData<Pair<ApplicationBean, String>>()
    val statusUpdateEvent: LiveData<Pair<ApplicationBean, String>> = _statusUpdateEvent

    private val _progressUpdateEvent = MutableLiveData<Int>()
    val progressUpdateEvent: LiveData<Int> = _progressUpdateEvent

    private val _progressCompleteEvent = MutableLiveData<Int>()
    val progressCompleteEvent: LiveData<Int> = _progressCompleteEvent

    private val _toastEvent = MutableLiveData<String>()
    val toastEvent: LiveData<String> = _toastEvent

    private val _dialogEvent = MutableLiveData<Triple<String, String, Boolean>>()
    val dialogEvent: LiveData<Triple<String, String, Boolean>> = _dialogEvent

    private val _showRerunTomoButtonsEvent = MutableLiveData<Boolean>()
    val showRerunTomoButtonsEvent: LiveData<Boolean> = _showRerunTomoButtonsEvent

    private val _appsList = MutableLiveData<List<ApplicationBean>>()
    val appsList: LiveData<List<ApplicationBean>> = _appsList

    private val _iteration = MutableLiveData<Int>()
    val iteration: LiveData<Int> = _iteration

    // Apps categorized by results
    var diffApps: ArrayList<ApplicationBean> = ArrayList()
    var inconclusiveApps: ArrayList<ApplicationBean> = ArrayList()
    var allApps: ArrayList<ApplicationBean> = ArrayList()

    // Coroutine jobs
    private var job: Job? = null
    private val uiUpdateJobs = ArrayList<Job>()

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
            _appsList.value = it
            allApps = ArrayList(it)
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
     * Show no network dialog
     */
    fun showNoNetworkDialog() {
        _dialogEvent.value = Triple(
            "Network Error",
            "No network connection available. Please check your internet connection and try again.",
            true
        )
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
        _appsList.value = selectedApps
        allApps = ArrayList(selectedApps)
    }

    /**
     * Update replay ongoing status
     */
    fun setReplayOngoing(isOngoing: Boolean) {
        _isReplayOngoing.value = isOngoing
    }

    /**
     * Update current testing app - This now properly updates iteration and posts to LiveData
     */
    fun setCurrentTestingApp(app: ApplicationBean?) {
        _currentTestingApp.postValue(app)

        // Update iteration when app changes
        if (app != null) {
            // Set iteration to 1 when starting a new app
            _iteration.postValue(1)
        }
    }

    /**
     * Update progress - Enhanced to match ReplayViewModel behavior
     */
    fun setProgress(progress: Int) {
        _progress.postValue(progress)
        _progressUpdateEvent.postValue(progress)
    }

    /**
     * Update status
     */
    fun setStatus(status: Pair<String, String>) {
        _status.postValue(status)
    }

    /**
     * Set test results
     */
    private fun setTestResults(results: Triple<List<ApplicationBean>, List<ApplicationBean>, List<ApplicationBean>>) {
        _testResults.postValue(results)

        val (allTestApps, diffTestApps, inconclusiveTestApps) = results
        allApps = ArrayList(allTestApps)
        diffApps = ArrayList(diffTestApps)
        inconclusiveApps = ArrayList(inconclusiveTestApps)

        _appsList.postValue(allTestApps)

        // Show completion dialog
        val message = buildString {
            append("Background tests completed!")
            // Uncomment if you want to show detailed results
//            append("Background tests completed!\n\n")
//            append("Total apps tested: ${allTestApps.size}\n")
//            append("Apps with differentiation: ${diffTestApps.size}\n")
//            append("Apps without differentiation: ${allTestApps.size - inconclusiveTestApps.size - diffTestApps.size}\n")
//            append("Inconclusive tests: ${inconclusiveTestApps.size}\n")
        }

        _dialogEvent.postValue(Triple("Replays Finished!", message, false))

        // Show rerun buttons if needed
        if (diffTestApps.isNotEmpty() || inconclusiveTestApps.isNotEmpty()) {
            _showRerunTomoButtonsEvent.postValue(true)
        }
    }

    /**
     * Update status for specific app - Enhanced to match ReplayViewModel behavior
     */
    private fun updateAppStatus(app: ApplicationBean, status: String) {
        // Create a UI update job like in ReplayViewModel
        val uiJob = viewModelScope.launch(Dispatchers.Main) {
            app.status = status
            _statusUpdateEvent.value = Pair(app, status)

            // Update apps list
            selectedApps?.let { apps ->
                val updatedApps = apps.map { if (it.name == app.name) app else it }
                _appsList.value = updatedApps
            }
        }
        uiUpdateJobs.add(uiJob)
    }

    /**
     * Update app status by name with iteration - New method to match ReplayViewModel
     */
    private fun updateAppStatus(appName: String, status: String, iteration: Int = -1) {
        val uiJob = viewModelScope.launch(Dispatchers.Main) {
            var finalStatus = status
            if (iteration != -1) {
                finalStatus = "$iteration/2 $status"
            }

            selectedApps?.forEach { app ->
                if (app.name == appName) {
                    app.status = finalStatus
                    _statusUpdateEvent.value = Pair(app, finalStatus)
                }
            }

            // Update the apps list
            selectedApps?.let { apps ->
                _appsList.value = ArrayList(apps)
            }
        }
        uiUpdateJobs.add(uiJob)
    }

    /**
     * Set current iteration - Enhanced posting
     */
    private fun setIteration(iter: Int) {
        _iteration.postValue(iter)
    }

    /**
     * Set progress complete - Enhanced to match ReplayViewModel behavior
     */
    fun setProgressComplete(iteration: Int) {
        _progressCompleteEvent.postValue(iteration)
    }

    /**
     * Show toast message
     */
    fun showToast(message: String) {
        _toastEvent.postValue(message)
    }

    /**
     * Show dialog
     */
    fun showDialog(title: String, message: String, exitReplays: Boolean) {
        _dialogEvent.postValue(Triple(title, message, exitReplays))
    }

    /**
     * Clear progress bar - New method to match ReplayViewModel
     */
    private fun clearProgressBar() {
        _progressCompleteEvent.postValue(0)
    }

    /**
     * Update progress with proper coroutine context
     */
    private fun updateProgress(progress: Int) {
        val uiJob = viewModelScope.launch(Dispatchers.Main) {
            _progressUpdateEvent.value = progress
        }
        uiUpdateJobs.add(uiJob)
    }

    /**
     * Finish progress for iteration
     */
    private fun finishProgress(iteration: Int) {
        val uiJob = viewModelScope.launch(Dispatchers.Main) {
            _progressCompleteEvent.value = iteration
        }
        uiUpdateJobs.add(uiJob)
    }

    /**
     * Show rerun and tomography buttons
     */
    fun showRerunTomoButtons() {
        _showRerunTomoButtonsEvent.postValue(true)
    }

    /**
     * Prepare rerun tests
     */
    fun prepareRerunTests(isRunningDifferentiation: Boolean): ArrayList<ApplicationBean> {
        val appsToRerun = if (isRunningDifferentiation) {
            ArrayList(diffApps)
        } else {
            ArrayList(inconclusiveApps)
        }

        // Reset status for apps to rerun
        for (app in appsToRerun) {
            app.status = applicationContext.getString(R.string.pending) ?: "Waiting to start"
        }

        selectedApps = appsToRerun
        _appsList.postValue(appsToRerun)

        // Clear previous results
        diffApps.clear()
        inconclusiveApps.clear()

        return appsToRerun
    }

    /**
     * Cancel ongoing tests - Enhanced to match ReplayViewModel
     */
    fun cancel() {
        // Cancel the main job
        job?.cancel()

        // Cancel all UI update jobs
        for (uiJob in uiUpdateJobs) {
            uiJob.cancel()
        }
        uiUpdateJobs.clear()

        _isReplayOngoing.postValue(false)

        selectedApps?.forEach { app ->
            if (app.status == applicationContext.getString(R.string.pending) ||
                app.status == applicationContext.getString(R.string.interrupt_ongoing_replay_text)) {
                app.status = applicationContext.getString(R.string.cancel_test) ?: "Cancelled"
            }
        }
        _appsList.postValue(selectedApps)

        // Clear current testing app
        _currentTestingApp.postValue(null)
    }

    /**
     * Start test execution - New method to initiate UI updates during testing
     */
    fun startTestExecution() {
        _isReplayOngoing.postValue(true)

        job = viewModelScope.launch(Dispatchers.IO) {
            try {
                simulateTestExecution()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error during test execution: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isReplayOngoing.value = false
                }
            }
        }
    }

    /**
     * Simulate test execution to update UI properly - mirrors ReplayViewModel behavior
     */
    private suspend fun simulateTestExecution() {
        selectedApps?.let { apps ->
            for ((index, app) in apps.withIndex()) {
                if (!isActive) return

                // Update current testing app
                withContext(Dispatchers.Main) {
                    setCurrentTestingApp(app)
                    setIteration(1)
                }

                // Clear progress bar
                clearProgressBar()
                updateProgress(0)

                // Update app status to testing
                updateAppStatus(app.name, applicationContext.getString(R.string.interrupt_ongoing_replay_text) ?: "Testing", 1)

                // Simulate test progress
                for (progress in 0..100 step 10) {
                    if (!isActive) return
                    updateProgress(progress)
                    kotlinx.coroutines.delay(500) // Simulate work
                }

                // Finish first iteration
                finishProgress(1)

                // Start second iteration if needed
                withContext(Dispatchers.Main) {
                    setIteration(2)
                }

                clearProgressBar()
                updateProgress(0)

                updateAppStatus(app.name, applicationContext.getString(R.string.interrupt_ongoing_replay_text) ?: "Testing", 2)

                // Simulate second iteration progress
                for (progress in 0..100 step 10) {
                    if (!isActive) return
                    updateProgress(progress)
                    kotlinx.coroutines.delay(500)
                }

                // Finish second iteration
                finishProgress(2)

                // Update final status
                val finalStatus = when {
                    Math.random() < 0.3 -> {
                        diffApps.add(app)
                        "Differentiation detected"
                    }
                    Math.random() < 0.6 -> {
                        inconclusiveApps.add(app)
                        "Inconclusive"
                    }
                    else -> "No differentiation"
                }

                updateAppStatus(app.name, finalStatus)

                if (!isActive) return
            }

            // Remove current testing app when done
            withContext(Dispatchers.Main) {
                setCurrentTestingApp(null)
            }

            // Set final results
            val allTestApps = ArrayList(apps)
            setTestResults(Triple(allTestApps, diffApps, inconclusiveApps))
        }
    }

    /**
     * Sync with service updates - call this when service is bound
     */
    fun syncWithService(service: ReplayForegroundService) {
        // Mirror service LiveData to ViewModel LiveData
        service.currentTestingApp.observeForever { app ->
            _currentTestingApp.postValue(app)

            app?.let { updatedApp ->
                selectedApps?.let { apps ->
                    val appIndex = apps.indexOfFirst { it.name == updatedApp.name }
                    if (appIndex != -1) {
                        // Update the app in the list with the new throughput values
                        apps[appIndex] = updatedApp

                        // Also update the status if it's provided
                        if (updatedApp.status.isNotEmpty()) {
                            updateAppStatus(updatedApp, updatedApp.status)
                        }

                        // Post the updated list to trigger UI refresh
                        _appsList.postValue(ArrayList(apps))

                        Log.d(TAG, "Updated app ${updatedApp.name} with throughput - original: ${updatedApp.originalThroughput}, random: ${updatedApp.randomThroughput}")
                    }
                }
            }
        }

        service.progressUpdate.observeForever { progress ->
            _progress.postValue(progress)
            _progressUpdateEvent.postValue(progress)
        }

        service.statusUpdate.observeForever { status ->
            _status.postValue(status)
            // Update the specific app status in the list
            selectedApps?.find { it.name == status.first }?.let { app ->
                updateAppStatus(app, status.second)
            }
        }

        service.iteration.observeForever { iter ->
            _iteration.postValue(iter)
        }

        service.testResults.observeForever { results ->
            _testResults.postValue(results)
            setTestResults(results)
        }

        service.isReplayOngoing.observeForever { isOngoing ->
            _isReplayOngoing.postValue(isOngoing)
        }
    }

    companion object {
        private const val TAG = "BackgroundReplayViewModel"
    }

}