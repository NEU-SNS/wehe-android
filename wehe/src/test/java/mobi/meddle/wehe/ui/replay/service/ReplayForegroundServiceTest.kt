package mobi.meddle.wehe.ui.replay.service

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import mobi.meddle.wehe.data.model.ApplicationBean
import mobi.meddle.wehe.data.repository.ReplayRepository
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Tests for [ReplayForegroundService], which owns the actual background run and the persistent
 * notification.
 *
 * This class had no coverage despite being the core of the background feature. The behaviours
 * pinned here are the ones a user notices directly: that the notification channel exists (without
 * it the notification is dropped), that the service promotes itself to the foreground so the run
 * survives leaving the app, and that the notification's Cancel action stops the run.
 *
 * The Hilt-injected [ReplayRepository] is swapped for a mock after construction so no test ever
 * touches the network - the mock's null returns make the run abort immediately during setup, which
 * is all these tests need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReplayForegroundServiceTest {

    private lateinit var application: Application
    private lateinit var controller: ServiceController<ReplayForegroundService>
    private lateinit var service: ReplayForegroundService

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        controller = Robolectric.buildService(ReplayForegroundService::class.java).create()
        service = controller.get()
        // Replace the real, Hilt-injected repository so startReplayTests() cannot do any I/O.
        service.replayRepository = mock(ReplayRepository::class.java)
    }

    private fun testApp(name: String = "TestApp") = ApplicationBean().apply {
        this.name = name
        dataFile = "data.json"
        randomDataFile = "random.json"
        size = 10
        time = 10
        category = ApplicationBean.Category.VIDEO
    }

    private fun startIntent(apps: ArrayList<ApplicationBean> = arrayListOf(testApp())) =
        Intent(application, ReplayForegroundService::class.java).apply {
            putExtra("runPortTests", true)
            putExtra("carrier", "Verizon")
            putParcelableArrayListExtra("selectedApps", apps)
        }

    // ------------------------------------------------------------------
    // Notification channel
    // ------------------------------------------------------------------

    @Test
    fun `onCreate registers the notification channel the foreground notification posts to`() {
        // Without a registered channel the system silently drops the notification on API 26+, so
        // the user would see nothing after leaving the app.
        val manager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = manager.notificationChannels.firstOrNull { it.id == "wehe_replay_channel" }

        assertThat(channel).isNotNull()
        // IMPORTANCE_LOW keeps a long-running progress notification silent, but still visible.
        assertThat(channel!!.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    // ------------------------------------------------------------------
    // Foreground promotion
    // ------------------------------------------------------------------

    @Test
    fun `onStartCommand promotes the service to the foreground with an ongoing notification`() {
        controller.withIntent(startIntent()).startCommand(0, 1)

        val notification = shadowOf(service).lastForegroundNotification
        assertThat(notification).isNotNull()
        // Ongoing stops the user swiping away the only indication that tests are still running.
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(shadowOf(service).lastForegroundNotificationId).isEqualTo(1)
    }

    @Test
    fun `the foreground notification offers a cancel action`() {
        controller.withIntent(startIntent()).startCommand(0, 1)

        val notification = shadowOf(service).lastForegroundNotification
        assertThat(notification.actions).isNotNull()
        assertThat(notification.actions).isNotEmpty()
    }

    @Test
    fun `onStartCommand records the run so a relaunched activity can rebuild from it`() {
        // BackgroundReplayActivity relaunched from the notification has no intent extras and reads
        // these back off the bound service instead.
        val apps = arrayListOf(testApp("A"), testApp("B"))

        controller.withIntent(startIntent(apps)).startCommand(0, 1)

        assertThat(service.activeRunPortTests).isTrue()
        assertThat(service.activeCarrier).isEqualTo("Verizon")
        assertThat(service.activeApps).hasSize(2)
        assertThat(service.activeApps!!.map { it.name }).containsExactly("A", "B").inOrder()
    }

    // ------------------------------------------------------------------
    // Cancel action
    // ------------------------------------------------------------------

    @Test
    fun `a cancel intent stops the run without promoting to the foreground`() {
        val cancel = Intent(application, ReplayForegroundService::class.java).apply {
            action = ReplayForegroundService.ACTION_CANCEL_TEST
        }

        val result = service.onStartCommand(cancel, 0, 1)

        // START_NOT_STICKY: a cancelled run must not be resurrected by the system.
        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        // The cancel path returns before startForeground(), so no notification is posted by it.
        assertThat(shadowOf(service).lastForegroundNotification).isNull()
    }

    @Test
    fun `a cancel intent broadcasts the cancellation so the UI can react`() {
        val cancel = Intent(application, ReplayForegroundService::class.java).apply {
            action = ReplayForegroundService.ACTION_CANCEL_TEST
        }

        controller.withIntent(cancel).startCommand(0, 1)

        val broadcasts = shadowOf(application).broadcastIntents
        assertThat(broadcasts.map { it.action })
            .contains(ReplayForegroundService.ACTION_TEST_CANCELLED)
    }

    // ------------------------------------------------------------------
    // Degenerate input
    // ------------------------------------------------------------------

    @Test
    fun `a null intent stops the service instead of crashing`() {
        // The system redelivers a null intent when it restarts a START_STICKY service. There is
        // nothing to run in that case, so the service must shut itself down cleanly.
        val result = service.onStartCommand(null, 0, 1)

        assertThat(result).isEqualTo(Service.START_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `binding hands back the service instance`() {
        val binder = service.onBind(Intent(application, ReplayForegroundService::class.java))

        assertThat(binder).isInstanceOf(ReplayForegroundService.ReplayServiceBinder::class.java)
        assertThat((binder as ReplayForegroundService.ReplayServiceBinder).getService())
            .isSameInstanceAs(service)
    }
}
