package ve.edu.unermb.asistencia.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat

/**
 * Receptor para el botón de acción rápida "Marcar como leído" en los banners flotantes Heads-Up.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_AS_READ = "ve.edu.unermb.asistencia.ACTION_MARK_AS_READ"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_MARK_AS_READ) {
            val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            if (notifId != -1) {
                try {
                    NotificationManagerCompat.from(context).cancel(notifId)
                } catch (_: Exception) {}
            }
        }
    }
}

