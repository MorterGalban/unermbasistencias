package ve.edu.unermb.asistencia.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-sincronizar servicio en tiempo real y programar alarmas exactas de clases tras reinicio
            RealtimeNotificationService.start(context)
            val workRequest = OneTimeWorkRequestBuilder<ClassReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}

