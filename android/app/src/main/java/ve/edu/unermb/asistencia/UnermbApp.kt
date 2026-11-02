package ve.edu.unermb.asistencia

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import ve.edu.unermb.asistencia.service.ClassReminderWorker
import ve.edu.unermb.asistencia.service.NotificationHelper
import ve.edu.unermb.asistencia.service.SuperrootSyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class UnermbApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // 1. Inicializar Canales de Notificación de Alta Prioridad
        NotificationHelper.createNotificationChannels(this)

        // 2. Programar Workers periódicos en segundo plano
        setupPeriodicWorkers()
    }

    private fun setupPeriodicWorkers() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Worker para sincronización de recordatorios y control de retrasos de docentes
        val docenteWork = PeriodicWorkRequestBuilder<ClassReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        // Worker para alertas de nuevos marcajes en segundo plano para el Superroot
        val superrootWork = PeriodicWorkRequestBuilder<SuperrootSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "unermb_docente_reminder_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            docenteWork
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "unermb_superroot_sync_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            superrootWork
        )
    }
}

