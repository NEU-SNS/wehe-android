package mobi.meddle.wehe.ui.replay.worker

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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.meddle.wehe.ui.replay.service.ReplayForegroundService

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
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting doWork with ID: ${this@ReplayWorker.id}")

                // Extract input data with error handling
                val runPortTests = inputData.getBoolean("runPortTests", false)
                val carrier = inputData.getString("carrier")

                // Try to get data from different methods
                val selectedAppList = ArrayList<ApplicationBean>()

                // Method 1: Try to get from string array (original method)
                val appsJsonArray = inputData.getStringArray("selectedApps")
                if (!appsJsonArray.isNullOrEmpty()) {
                    Log.d(TAG, "Using string array method: ${appsJsonArray.size} items")
                    parseAppsFromJsonArray(appsJsonArray, selectedAppList)
                }
                // Method 2: Try to get from single compressed JSON
                else {
                    val compressedJson = inputData.getString("appsCompressedJson")
                    if (!compressedJson.isNullOrEmpty()) {
                        Log.d(TAG, "Using compressed JSON method")
                        parseAppsFromCompressedJson(compressedJson, selectedAppList)
                    }
                }

                if (selectedAppList.isEmpty()) {
                    Log.e(TAG, "Failed to parse any apps from input data")
                    return@withContext Result.failure()
                }

                Log.d(TAG, "Successfully parsed ${selectedAppList.size} apps")
                Log.d(TAG, "Starting service with ${selectedAppList.size} apps")

                // Start the foreground service to run tests
                val serviceIntent = Intent(appContext, ReplayForegroundService::class.java).apply {
                    putExtra("runPortTests", runPortTests)
                    putExtra("carrier", carrier)
                    putParcelableArrayListExtra("selectedApps", selectedAppList)
                }

                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Log.d(TAG, "Starting foreground service (Android O+)")
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        Log.d(TAG, "Starting service (pre-Android O)")
                        appContext.startService(serviceIntent)
                    }
                    Log.d(TAG, "Service started successfully")
                    return@withContext Result.success()
                } catch (e: Exception) {
                    Log.e(TAG, "Error starting service", e)
                    return@withContext Result.failure()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled exception in doWork", e)
                return@withContext Result.failure()
            }
        }
    }

    private fun parseAppsFromJsonArray(appsJsonArray: Array<String>, selectedAppList: ArrayList<ApplicationBean>) {
        val gson = Gson()
        for (appJson in appsJsonArray) {
            try {
                val app = gson.fromJson(appJson, ApplicationBean::class.java)
                selectedAppList.add(app)
                Log.d(TAG, "Parsed app: ${app.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing app JSON: $appJson", e)
                // Continue with other apps
            }
        }
    }

    private fun parseAppsFromCompressedJson(compressedJson: String, selectedAppList: ArrayList<ApplicationBean>) {
        try {
            val gson = Gson()
            val listType = object : TypeToken<ArrayList<ApplicationBean>>() {}.type
            val apps: ArrayList<ApplicationBean> = gson.fromJson(compressedJson, listType)
            selectedAppList.addAll(apps)
            Log.d(TAG, "Parsed ${apps.size} apps from compressed JSON")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing compressed JSON", e)
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
            Log.d(TAG, "Scheduling replay test with ${selectedApps.size} apps")

            try {
                val gson = Gson()

                // Prepare data builder
                val dataBuilder = Data.Builder()
                    .putBoolean("runPortTests", runPortTests)
                    .putString("carrier", carrier)

                // Try two methods to avoid data size issues
                try {
                    // Method 1: Traditional array method
                    if (selectedApps.size <= 5) { // Arbitrary limit to avoid size issues
                        val appsJsonArray = selectedApps.map { gson.toJson(it) }.toTypedArray()
                        dataBuilder.putStringArray("selectedApps", appsJsonArray)
                        Log.d(TAG, "Using string array method for ${appsJsonArray.size} apps")
                    }
                    // Method 2: Compressed single JSON for larger lists
                    else {
                        val compressedJson = gson.toJson(selectedApps)
                        if (compressedJson.length > 8000) { // WorkManager Data has ~10KB limit
                            Log.w(TAG, "JSON data may be too large: ${compressedJson.length} bytes")
                            // Store only essential data
                            val minimalApps = selectedApps.map { app ->
                                val minimal = ApplicationBean()
                                minimal.name = app.name
                                minimal.dataFile = app.dataFile
                                minimal.randomDataFile = app.randomDataFile
                                minimal.image = app.image
                                minimal
                            }
                            val minimalJson = gson.toJson(minimalApps)
                            Log.d(TAG, "Using minimal JSON: ${minimalJson.length} bytes")
                            dataBuilder.putString("appsCompressedJson", minimalJson)
                        } else {
                            dataBuilder.putString("appsCompressedJson", compressedJson)
                            Log.d(TAG, "Using compressed JSON: ${compressedJson.length} bytes")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error preparing app data", e)
                    throw e
                }

                val workData = dataBuilder.build()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ReplayWorker>()
                    .setInputData(workData)
                    .setConstraints(constraints)
                    .addTag("replay_test") // Add the tag for observation and cancellation
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()

                Log.d(TAG, "Created work request with ID: ${workRequest.id}")

                // Schedule the work
                val operation = WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "replay_test_${System.currentTimeMillis()}",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

                Log.d(TAG, "Enqueued work request")
                return operation
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling replay test", e)
                throw e
            }
        }

        /**
         * Cancel all scheduled replay tests
         */
        fun cancelAllReplayTests(context: Context) {
            Log.d(TAG, "Cancelling all replay tests")
            WorkManager.getInstance(context).cancelAllWorkByTag("replay_test")
        }
    }
}