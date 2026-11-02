package ve.edu.unermb.asistencia.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Re-sincronizar y programar alarmas de clases tras reinicio
            val workRequest = OneTimeWorkRequestBuilder<ClassReminderWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}

