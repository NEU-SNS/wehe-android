package mobi.meddle.wehe.ui.replay

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker to schedule and manage background replay tests
 */
@HiltWorker
class ReplayWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ReplayRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val runPortTests = inputData.getBoolean("runPortTests", false)
        val carrier = inputData.getString("carrier")
        val selectedAppIds = inputData.getStringArray("selectedAppIds") ?: return Result.failure()

        try {
            // Load apps from IDs
//            val selectedApps = withContext(Dispatchers.IO) {
//                // This would need to be implemented in your repository
//                repository.loadAppsByIds(selectedAppIds)
//            }

            // placeholder logic
            val selectedApps: List<ApplicationBean> = emptyList()


            if (selectedApps.isEmpty()) {
                return Result.failure()
            }

            // Start the foreground service to run tests
            val serviceIntent = Intent(appContext, ReplayForegroundService::class.java).apply {
                putExtra("runPortTests", runPortTests)
                putExtra("carrier", carrier)
                putParcelableArrayListExtra("selectedApps", ArrayList(selectedApps))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting replay tests", e)
            return Result.failure()
        }
    }

    companion object {
        private const val TAG = "ReplayWorker"

        /**
         * Schedule a replay test to run immediately
         */
        fun scheduleReplayTest(
            context: Context,
            runPortTests: Boolean,
            carrier: String?,
            selectedApps: ArrayList<ApplicationBean>
        ): Operation {
            // Convert apps to IDs for work request
            val appIds = selectedApps.map { it.name }.toTypedArray()

            // Create work request
            val workData = Data.Builder()
                .putBoolean("runPortTests", runPortTests)
                .putString("carrier", carrier)
                .putStringArray("selectedAppIds", appIds)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ReplayWorker>()
                .setInputData(workData)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            // Schedule the work
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "replay_test_${System.currentTimeMillis()}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
        }

        /**
         * Cancel all scheduled replay tests
         */
        fun cancelAllReplayTests(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag("replay_test")
        }
    }
}