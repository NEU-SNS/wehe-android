package mobi.meddle.wehe.ui.replay.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mobi.meddle.wehe.ui.main.activity.MainActivity
import mobi.meddle.wehe.ui.replay.service.ReplayForegroundService

/**
 * [CancelReceiver] is a [BroadcastReceiver] that handles cancellation of background replay tests
 * via the persistent notification.
 *
 * This receiver is triggered when the user selects the "Cancel" action from the foreground
 * notification shown by [ReplayForegroundService] during an ongoing test.
 *
 * Functionality:
 * 1. Sends an intent with [ReplayForegroundService.ACTION_CANCEL_TEST] to the
 *    [ReplayForegroundService] to gracefully stop the ongoing replay test.
 * 2. Brings the existing [MainActivity] to the foreground to update the UI and inform
 *    the user about the cancellation or allow further interaction.
 *
 * The receiver is typically registered via a [PendingIntent] attached to the notification action.
 *
 * @see ReplayForegroundService
 * @see MainActivity
 */
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Cancel the ongoing replay test service
        val cancelIntent = Intent(context, ReplayForegroundService::class.java).apply {
            action = ReplayForegroundService.ACTION_CANCEL_TEST
        }
        context.startService(cancelIntent)

        // Bring MainActivity to the foreground if it exists, or launch it if not
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(activityIntent)
    }
}
