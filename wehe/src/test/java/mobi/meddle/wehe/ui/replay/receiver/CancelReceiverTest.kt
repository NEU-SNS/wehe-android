package mobi.meddle.wehe.ui.replay.receiver

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import mobi.meddle.wehe.ui.main.activity.MainActivity
import mobi.meddle.wehe.ui.replay.service.ReplayForegroundService
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Tests for [CancelReceiver], the target of the persistent notification's "Cancel" action.
 *
 * This receiver had no coverage at all. It is worth pinning down because it is the only path that
 * stops a run from outside the app, and because it is reachable from a notification the user sees
 * while Wehe is not in the foreground.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CancelReceiverTest {

    private lateinit var application: Application
    private lateinit var receiver: CancelReceiver

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        receiver = CancelReceiver()
    }

    @Test
    fun `onReceive asks the service to cancel the running test`() {
        receiver.onReceive(application, Intent())

        val started = shadowOf(application).nextStartedService
        assertThat(started).isNotNull()
        assertThat(started.component?.className)
            .isEqualTo(ReplayForegroundService::class.java.name)
        assertThat(started.action).isEqualTo(ReplayForegroundService.ACTION_CANCEL_TEST)
    }

    @Test
    fun `onReceive brings MainActivity back rather than starting a second task`() {
        receiver.onReceive(application, Intent())

        val activityIntent = shadowOf(application).nextStartedActivity
        assertThat(activityIntent).isNotNull()
        assertThat(activityIntent.component?.className).isEqualTo(MainActivity::class.java.name)

        // CLEAR_TOP|SINGLE_TOP reuses the existing MainActivity instead of stacking a duplicate;
        // NEW_TASK is required because a receiver has no activity context to launch from.
        val flags = activityIntent.flags
        assertThat(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isNotEqualTo(0)
        assertThat(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP).isNotEqualTo(0)
        assertThat(flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `onReceive tolerates a null intent`() {
        // The system may deliver a null intent; onReceive ignores its contents entirely, so this
        // must not throw and must still cancel.
        receiver.onReceive(application, null)

        assertThat(shadowOf(application).nextStartedService).isNotNull()
    }
}
