package mobi.meddle.wehe.ui.replay

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mobi.meddle.wehe.data.bean.ApplicationBean
import mobi.meddle.wehe.data.bean.CombinedAppJSONInfoBean
import mobi.meddle.wehe.constant.Consts
import mobi.meddle.wehe.data.repository.UserPreferencesRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/**
 * Use case that handles the core functionality of running replay tests.
 * This class encapsulates the logic extracted from TraceRunAsync.
 */
class RunTestUseCase @Inject constructor(
    private val serverConnectionUseCase: ServerConnectionUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    /**
     * Runs tests for the selected applications and provides progress updates via Flow.
     *
     * @param selectedApps List of applications selected for testing
     * @param isTomography Whether running tomography tests
     * @param runPortTests Whether running port tests
     * @return Flow of TestProgressState for UI updates
     */
    fun runTests(
        selectedApps: List<ApplicationBean>,
        isTomography: Boolean,
        runPortTests: Boolean
    ): Flow<TestProgressState> = flow {
        // Initialize tests
        emit(TestProgressState(isLoading = true, progress = 0))

        // Check network availability first
        if (!serverConnectionUseCase.isNetworkAvailable()) {
            emit(TestProgressState(errorType = TestErrorType.NETWORK_UNAVAILABLE))
            return@flow
        }

        // Get user preferences
        val preferences = userPreferencesRepository.getUserPreferences()
        val serverDisplay = if (isTomography) {
            Consts.DEFAULT_SERVER
        } else {
            preferences.serverAddress ?: Consts.DEFAULT_SERVER
        }

        // Set up server connections
        val serverSetupResult = serverConnectionUseCase.setupServersAndCertificates(
            serverDisplay,
            Consts.METADATA_SERVER
        )

        if (!serverSetupResult.isSuccess) {
            emit(TestProgressState(
                errorType = serverSetupResult.errorType ?: TestErrorType.SERVER_CONNECTION_ERROR,
                errorMessage = serverSetupResult.errorMessage
            ))
            return@flow
        }

        // Initialize testing parameters
        val randomID = userPreferencesRepository.getDeviceId()
        val historyCount = userPreferencesRepository.getHistoryCount()
        val confirmationReplays = preferences.confirmationReplays
        val useDefaultThresholds = preferences.useDefaultThresholds
        val aThreshold = preferences.areaThreshold
        val ks2pvalueThreshold = preferences.ks2pThreshold

        // Configure testing settings
        val config = mapOf(
            "timing" to (if (runPortTests) "false" else "true"),
            "server" to serverConnectionUseCase.getServersList(),
            "publicIP" to serverConnectionUseCase.getPublicIP("80")
        )

        // Run tests for each application
        val results = JSONArray()
        var appIndex = 0

        for (app in selectedApps) {
            // Check if test was cancelled
            if (!isActive) {
                emit(TestProgressState(isCancelled = true))
                break
            }

            // Prepare UI state for this app
            emit(TestProgressState(
                isLoading = true,
                currentApp = app,
                progress = (appIndex * 100) / selectedApps.size,
                phase = TestPhase.INITIAL_TEST
            ))

            // Run first test
            val firstResult = runSingleTest(
                app,
                historyCount,
                randomID,
                TestPhase.INITIAL_TEST,
                isTomography
            )

            if (firstResult.hasError) {
                app.status = "Inconclusive"
                app.error = firstResult.errorMessage
                emit(TestProgressState(
                    isLoading = false,
                    currentApp = app,
                    progress = ((appIndex + 0.5f) * 100) / selectedApps.size,
                    phase = TestPhase.INITIAL_TEST,
                    appStatus = AppStatus.INCONCLUSIVE
                ))
                continue
            }

            emit(TestProgressState(
                isLoading = true,
                currentApp = app,
                progress = ((appIndex + 0.5f) * 100) / selectedApps.size,
                phase = TestPhase.CONFIRMATION_TEST
            ))

            // Check if confirmation test is needed
            val needsConfirmation = !isTomography &&
                    confirmationReplays &&
                    (firstResult.hasDifferentiation || firstResult.isInconclusive)

            if (needsConfirmation) {
                val confirmationResult = runSingleTest(
                    app,
                    historyCount,
                    randomID,
                    TestPhase.CONFIRMATION_TEST,
                    isTomography
                )

                if (confirmationResult.hasDifferentiation) {
                    app.status = "Differentiation"
                    emit(TestProgressState(
                        isLoading = false,
                        currentApp = app,
                        progress = ((appIndex + 1f) * 100) / selectedApps.size,
                        phase = TestPhase.COMPLETE,
                        appStatus = AppStatus.DIFFERENTIATION
                    ))
                } else if (confirmationResult.isInconclusive) {
                    app.status = "Inconclusive"
                    app.error = confirmationResult.errorMessage
                    emit(TestProgressState(
                        isLoading = false,
                        currentApp = app,
                        progress = ((appIndex + 1f) * 100) / selectedApps.size,
                        phase = TestPhase.COMPLETE,
                        appStatus = AppStatus.INCONCLUSIVE
                    ))
                } else {
                    app.status = "No Differentiation"
                    emit(TestProgressState(
                        isLoading = false,
                        currentApp = app,
                        progress = ((appIndex + 1f) * 100) / selectedApps.size,
                        phase = TestPhase.COMPLETE,
                        appStatus = AppStatus.NO_DIFFERENTIATION
                    ))
                }
            } else {
                // No confirmation needed
                if (firstResult.hasDifferentiation) {
                    app.status = "Differentiation"
                    emit(TestProgressState(
                        isLoading = false,
                        currentApp = app,
                        progress = ((appIndex + 1f) * 100) / selectedApps.size,
                        phase = TestPhase.COMPLETE,
                        appStatus = AppStatus.DIFFERENTIATION
                    ))
                } else {
                    app.status = "No Differentiation"
                    emit(TestProgressState(
                        isLoading = false,
                        currentApp = app,
                        progress = ((appIndex + 1f) * 100) / selectedApps.size,
                        phase = TestPhase.COMPLETE,
                        appStatus = AppStatus.NO_DIFFERENTIATION
                    ))
                }
            }

            // Save result to results array
            results.put(firstResult.resultJson)
            appIndex++
        }

        // Save results to preferences
        if (results.length() > 0) {
            saveResults(results)
        }

        // Emit completion state
        emit(TestProgressState(
            isLoading = false,
            progress = 100,
            phase = TestPhase.COMPLETE,
            isFinished = true
        ))
    }

    /**
     * Runs a single test for an application.
     *
     * @param app The application to test
     * @param historyCount The test number
     * @param randomID The unique device ID
     * @param phase The current test phase
     * @param isTomography Whether running tomography tests
     * @return TestResult containing the result information
     */
    private suspend fun runSingleTest(
        app: ApplicationBean,
        historyCount: Int,
        randomID: String,
        phase: TestPhase,
        isTomography: Boolean
    ): TestResult {
        // Load app data (replay file)
        val appData = loadAppData(app.getReplayFileName())

        // Run the replay
        val replayResult = serverConnectionUseCase.runReplay(
            app,
            appData,
            historyCount,
            randomID,
            phase == TestPhase.CONFIRMATION_TEST
        )

        if (!replayResult.isSuccess) {
            return TestResult(
                hasError = true,
                errorMessage = replayResult.errorMessage ?: "Unknown error during replay"
            )
        }

        // Analyze results
        val analyzedResult = serverConnectionUseCase.analyzeResults(
            app,
            historyCount,
            randomID
        )

        return TestResult(
            hasError = analyzedResult.hasError,
            errorMessage = analyzedResult.errorMessage,
            hasDifferentiation = analyzedResult.hasDifferentiation,
            isInconclusive = analyzedResult.isInconclusive,
            resultJson = analyzedResult.resultJson
        )
    }

    /**
     * Loads application data from its replay file.
     *
     * @param filename The replay file name
     * @return CombinedAppJSONInfoBean containing the app's replay data
     */
    private fun loadAppData(filename: String): CombinedAppJSONInfoBean {
        // Implementation would read the JSON file and parse it
        // This is a simplification of the unpickleJSON method
        return CombinedAppJSONInfoBean()
    }

    /**
     * Saves test results to shared preferences.
     *
     * @param results The JSONArray of test results
     */
    private fun saveResults(results: JSONArray) {
        userPreferencesRepository.saveResults(results)
    }

    private val CoroutineScope.isActive: Boolean
        get() = true // In actual implementation, this should check if coroutine is active
}

/**
 * Represents the result of a single test.
 */
data class TestResult(
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val hasDifferentiation: Boolean = false,
    val isInconclusive: Boolean = false,
    val resultJson: JSONObject? = null
)

/**
 * Represents the current state of the test progress.
 */
data class TestProgressState(
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val currentApp: ApplicationBean? = null,
    val phase: TestPhase = TestPhase.NONE,
    val appStatus: AppStatus = AppStatus.NONE,
    val errorType: TestErrorType? = null,
    val errorMessage: String? = null,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false
)

/**
 * Represents the phase of testing.
 */
enum class TestPhase {
    NONE,
    INITIAL_TEST,
    CONFIRMATION_TEST,
    COMPLETE
}

/**
 * Represents the status of an app test.
 */
enum class AppStatus {
    NONE,
    DIFFERENTIATION,
    NO_DIFFERENTIATION,
    INCONCLUSIVE
}

/**
 * Represents types of errors that can occur during testing.
 */
enum class TestErrorType {
    NONE,
    NETWORK_UNAVAILABLE,
    SERVER_CONNECTION_ERROR,
    UNKNOWN_HOST,
    META_HOST_ERROR,
    MLAB_CONNECTION_ERROR,
    REPLAY_ERROR,
    ANALYSIS_ERROR
}