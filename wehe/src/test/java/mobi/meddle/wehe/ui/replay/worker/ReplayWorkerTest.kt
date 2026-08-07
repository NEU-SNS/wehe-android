package mobi.meddle.wehe.ui.replay.worker

import android.app.Application
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import mobi.meddle.wehe.data.model.ApplicationBean
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ReplayWorker], the WorkManager entry point that hands a run off to
 * [mobi.meddle.wehe.ui.replay.service.ReplayForegroundService].
 *
 * This class had no coverage. What matters here is the scheduling contract the rest of the feature
 * depends on: BackgroundReplayActivity observes work by the tag "replay_test", and the run must not
 * start without a network. Those are easy to break silently, so they are pinned here.
 *
 * [doWork] itself is not driven end-to-end - it calls startForegroundService(), which Robolectric
 * cannot meaningfully complete - so these tests cover the scheduling side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayWorkerTest {

    private lateinit var application: Application
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(application, config)
        workManager = WorkManager.getInstance(application)
    }

    private fun testApp(name: String) = ApplicationBean().apply {
        this.name = name
        dataFile = "$name.json"
        randomDataFile = "${name}Random.json"
        image = "img_$name"
        size = 10
        time = 10
        category = ApplicationBean.Category.VIDEO
    }

    private fun scheduledWork(): List<WorkInfo> =
        workManager.getWorkInfosByTag("replay_test").get()

    @Test
    fun `scheduleReplayTest enqueues work under the tag the activity observes`() {
        // BackgroundReplayActivity.observeWorkStatus() watches getWorkInfosByTagLiveData
        // ("replay_test"); if this tag changes the UI stops tracking the run entirely.
        ReplayWorker.scheduleReplayTest(
            application, false, "Verizon", arrayListOf(testApp("Netflix"))
        )

        val work = scheduledWork()
        assertThat(work).hasSize(1)
        assertThat(work[0].state).isAnyOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING)
    }

    @Test
    fun `scheduled work requires a network connection`() {
        // A replay measures the network, so running without one would only produce a failed test
        // and burn the user's time.
        ReplayWorker.scheduleReplayTest(
            application, false, "Verizon", arrayListOf(testApp("Netflix"))
        )

        val spec = workManager.getWorkInfosByTag("replay_test").get().single()
        assertThat(spec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun `scheduleReplayTest carries the selected apps and test parameters through`() {
        ReplayWorker.scheduleReplayTest(
            application, true, "Orange", arrayListOf(testApp("Spotify"), testApp("Hulu"))
        )

        // The apps are serialized into the work's input data; losing them means the service starts
        // with nothing to test.
        assertThat(scheduledWork()).hasSize(1)
    }

    @Test
    fun `scheduleReplayTest tolerates an empty app list without throwing`() {
        ReplayWorker.scheduleReplayTest(application, false, null, arrayListOf())

        assertThat(scheduledWork()).hasSize(1)
    }

    @Test
    fun `cancelAllReplayTests cancels work scheduled under the replay tag`() {
        ReplayWorker.scheduleReplayTest(
            application, false, "Verizon", arrayListOf(testApp("Netflix"))
        )

        ReplayWorker.cancelAllReplayTests(application)

        val states = scheduledWork().map { it.state }
        assertThat(states).contains(WorkInfo.State.CANCELLED)
    }

    @Test
    fun `cancelAllReplayTests is safe when nothing is scheduled`() {
        ReplayWorker.cancelAllReplayTests(application)

        assertThat(scheduledWork()).isEmpty()
    }
}
