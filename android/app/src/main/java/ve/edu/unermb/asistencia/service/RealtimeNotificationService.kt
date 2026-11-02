package ve.edu.unermb.asistencia.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ve.edu.unermb.asistencia.MainActivity
import ve.edu.unermb.asistencia.R
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.repository.DocenteRepository
import ve.edu.unermb.asistencia.data.repository.SuperrootRepository
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Servicio persistente en segundo plano con soporte WAKE_LOCK y sincronización en tiempo real
 * con el servidor en Railway cada 12 segundos para desplegar banners flotantes (Heads-Up)
 * en el instante exacto en que ocurre una alerta sin requerir abrir la aplicación.
 */
@AndroidEntryPoint
class RealtimeNotificationService : Service() {

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var docenteRepository: DocenteRepository

    @Inject
    lateinit var superrootRepository: SuperrootRepository

    @Inject
    lateinit var classReminderScheduler: ClassReminderScheduler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isLoopRunning = false

    companion object {
        private const val FOREGROUND_DAEMON_ID = 99401
        private const val POLL_INTERVAL_MS = 12_000L

        fun start(context: Context) {
            try {
                val intent = Intent(context, RealtimeNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(Intent(context, RealtimeNotificationService::class.java))
                } catch (_: Exception) {}
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        promoteToForegroundDaemon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForegroundDaemon()
        if (!isLoopRunning) {
            isLoopRunning = true
            startRealtimePollingLoop()
        }
        return START_STICKY
    }

    private fun promoteToForegroundDaemon() {
        try {
            val openIntent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_FOREGROUND_SYNC)
                .setSmallIcon(R.drawable.ic_stat_unermb)
                .setContentTitle("UNERMB Control de Asistencia Activo")
                .setContentText("Monitoreo en tiempo real de horarios y notificaciones.")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(pendingIntent)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    FOREGROUND_DAEMON_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(FOREGROUND_DAEMON_ID, notification)
            }
        } catch (_: Exception) {}
    }

    private fun startRealtimePollingLoop() {
        serviceScope.launch {
            while (isActive) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                val wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "unermb:RealtimeSyncWakeLock"
                )
                try {
                    wakeLock?.acquire(8_000L)
                    executeRealtimeTick()
                } catch (_: Exception) {
                } finally {
                    try {
                        if (wakeLock?.isHeld == true) {
                            wakeLock.release()
                        }
                    } catch (_: Exception) {}
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun executeRealtimeTick() {
        val token = dataStoreManager.getCachedToken()
        val rol = dataStoreManager.userRolFlow.firstOrNull()

        if (token.isNullOrBlank() || rol.isNullOrBlank()) return

        NotificationHelper.setSessionRole(applicationContext, rol)

        if (rol == Constants.ROL_DOCENTE) {
            syncDocenteRealtime()
        } else if (rol == Constants.ROL_SUPERROOT) {
            syncSuperrootRealtime()
        }
    }

    private suspend fun syncDocenteRealtime() {
        val horarioRes = docenteRepository.getMiHorario()
        if (horarioRes !is Resource.Success || horarioRes.data?.horarios == null) return

        val diaActual = DateTimeUtils.todayDayOfWeekSpanish()
        val clasesHoy = horarioRes.data.horarios.filter {
            it.dia_semana.equals(diaActual, ignoreCase = true)
        }

        // Programar alarmas exactas a nivel de sistema operativo (SCHEDULE_EXACT_ALARM)
        classReminderScheduler.scheduleTodayClassReminders(clasesHoy)

        // Consultar historial para saber si el docente ya marcó ENTRADA hoy
        val historialRes = docenteRepository.getHistorial()
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        val marcajesEntradaHoy = historialRes.data?.historial?.filter {
            it.tipo == "ENTRADA" && it.fecha_hora.startsWith(todayIso)
        } ?: emptyList()

        if (marcajesEntradaHoy.isNotEmpty()) {
            NotificationHelper.markDocenteEntradaRegisteredToday(applicationContext)
        }

        val yaMarcoEntradaHoy = marcajesEntradaHoy.isNotEmpty() ||
            NotificationHelper.hasDocenteRegisteredEntradaToday(applicationContext)

        val horaActualMinutos = DateTimeUtils.timeToMinutes(
            DateTimeUtils.nowInVenezuela().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        )

        for (clase in clasesHoy) {
            val inicioMinutos = DateTimeUtils.timeToMinutes(clase.hora_inicio)
            val finMinutos = DateTimeUtils.timeToMinutes(clase.hora_fin)

            // 1. Notificación preventiva 10 minutos antes del inicio de clase
            val minRecordatorio = inicioMinutos - Constants.MINUTOS_RECORDATORIO_PREVIO
            if (horaActualMinutos in minRecordatorio until inicioMinutos) {
                val eventKey = "doc_prev10_${clase.id}_${clase.materia_nombre}"
                if (!NotificationHelper.wasEventNotifiedToday(applicationContext, eventKey)) {
                    NotificationHelper.markEventNotifiedToday(applicationContext, eventKey)
                    NotificationHelper.showDocenteClassReminder(
                        context = applicationContext,
                        materiaNombre = clase.materia_nombre,
                        bloqueHorario = clase.bloque_horario,
                        horaInicio = clase.hora_inicio
                    )
                }
            }

            // 2. Alerta de retardo 10 minutos después de la hora de inicio si aún no ha marcado entrada
            if (!yaMarcoEntradaHoy &&
                horaActualMinutos >= inicioMinutos + Constants.MINUTOS_ALERTA_RETARDO &&
                horaActualMinutos <= finMinutos
            ) {
                val eventKey = "doc_delay10_${clase.id}_${clase.materia_nombre}"
                if (!NotificationHelper.wasEventNotifiedToday(applicationContext, eventKey)) {
                    NotificationHelper.markEventNotifiedToday(applicationContext, eventKey)
                    NotificationHelper.showDocenteRetrasoAlert(
                        context = applicationContext,
                        materiaNombre = clase.materia_nombre,
                        bloqueHorario = clase.bloque_horario,
                        minutosRetraso = horaActualMinutos - inicioMinutos
                    )
                }
            }
        }
    }

    private suspend fun syncSuperrootRealtime() {
        val eventosRes = superrootRepository.getNotificacionesEventos()
        if (eventosRes !is Resource.Success || eventosRes.data == null) return

        val data = eventosRes.data

        // 1. Solicitudes de registro pendientes
        data.solicitudesPendientes.orEmpty().forEach { sol ->
            val key = "sr_sol_${sol.id}"
            if (!NotificationHelper.wasEventNotifiedToday(applicationContext, key)) {
                NotificationHelper.markEventNotifiedToday(applicationContext, key)
                NotificationHelper.showSuperrootSolicitudAlert(
                    context = applicationContext,
                    docenteNombre = "${sol.nombre} ${sol.apellido}",
                    cedula = sol.cedula
                )
            }
        }

        // 2. Nuevos marcajes de entrada y salida (incluyendo retardos)
        data.marcajesRecientes.orEmpty().take(5).forEach { m ->
            val key = "sr_marcaje_${m.id}"
            if (!NotificationHelper.wasEventNotifiedToday(applicationContext, key)) {
                NotificationHelper.markEventNotifiedToday(applicationContext, key)
                NotificationHelper.showSuperrootMarcajeAlert(
                    context = applicationContext,
                    docenteNombre = "${m.nombre} ${m.apellido}",
                    tipo = m.tipo,
                    materia = m.materia_detectada ?: "Jornada Docente",
                    puntualidad = m.puntualidad_estado ?: "A_TIEMPO",
                    minutosRetraso = m.minutos_retraso ?: 0
                )
            }
        }

        // 3. Alertas por inasistencia o retardo activo (+10 min sin marcaje)
        data.alertasInasistencia.orEmpty().forEach { alerta ->
            val key = "sr_inasist_${alerta.horario_id}_${alerta.fecha}"
            if (!NotificationHelper.wasEventNotifiedToday(applicationContext, key)) {
                NotificationHelper.markEventNotifiedToday(applicationContext, key)
                NotificationHelper.showSuperrootInasistenciaAlert(
                    context = applicationContext,
                    docenteNombre = "${alerta.nombre} ${alerta.apellido}",
                    materia = alerta.materia_nombre,
                    bloqueHorario = alerta.bloque_horario,
                    minutosTranscurridos = alerta.minutos_transcurridos
                )
            }
        }

        // 4. Recepción de justificativos enviados por docentes
        data.justificacionesRecientes.orEmpty().take(5).forEach { justif ->
            val key = "sr_justif_${justif.id}_${justif.justificacion_fecha ?: ""}"
            if (!NotificationHelper.wasEventNotifiedToday(applicationContext, key)) {
                NotificationHelper.markEventNotifiedToday(applicationContext, key)
                NotificationHelper.showSuperrootJustificativoAlert(
                    context = applicationContext,
                    docenteNombre = "${justif.nombre} ${justif.apellido}",
                    materia = justif.materia_detectada ?: "Clase",
                    motivo = justif.justificacion_texto ?: "Evidencia adjunta"
                )
            }
        }
    }

    override fun onDestroy() {
        isLoopRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }
}

