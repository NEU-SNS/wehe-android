package mobi.meddle.wehe.ui.replay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import mobi.meddle.wehe.ui.main.MainActivity

class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Cancel service
        val cancelIntent = Intent(context, ReplayForegroundService::class.java).apply {
            action = ReplayForegroundService.ACTION_CANCEL_TEST
        }
        context.startService(cancelIntent)

        // Bring existing MainActivity to front
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(activityIntent)
    }
}
