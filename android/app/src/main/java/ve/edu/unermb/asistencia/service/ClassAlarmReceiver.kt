package ve.edu.unermb.asistencia.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import ve.edu.unermb.asistencia.core.Constants

class ClassAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALARM = "ve.edu.unermb.asistencia.ACTION_CLASS_ALARM"
        const val EXTRA_ALARM_TYPE = "extra_alarm_type"
        const val EXTRA_CLASE_ID = "extra_clase_id"
        const val EXTRA_MATERIA_NOMBRE = "extra_materia_nombre"
        const val EXTRA_BLOQUE_HORARIO = "extra_bloque_horario"
        const val EXTRA_HORA_INICIO = "extra_hora_inicio"

        const val TYPE_PREVENTIVE_10_MIN_BEFORE = "PREVENTIVE_10_MIN_BEFORE"
        const val TYPE_DELAY_10_MIN_AFTER = "DELAY_10_MIN_AFTER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "unermb:ClassAlarmWakeLock"
        )
        try {
            wakeLock?.acquire(10_000L)

            // Segmentación estricta por rol: solo disparar si la sesión activa corresponde a DOCENTE
            val activeRole = NotificationHelper.getSessionRole(context)
            if (activeRole == Constants.ROL_SUPERROOT) return

            val alarmType = intent.getStringExtra(EXTRA_ALARM_TYPE) ?: TYPE_PREVENTIVE_10_MIN_BEFORE
            val claseId = intent.getIntExtra(EXTRA_CLASE_ID, 0)
            val materia = intent.getStringExtra(EXTRA_MATERIA_NOMBRE) ?: "Clase Asignada"
            val bloque = intent.getStringExtra(EXTRA_BLOQUE_HORARIO) ?: "Horario Oficial"
            val horaInicio = intent.getStringExtra(EXTRA_HORA_INICIO) ?: ""

            when (alarmType) {
                TYPE_PREVENTIVE_10_MIN_BEFORE -> {
                    val eventKey = "doc_prev10_${claseId}_$materia"
                    if (!NotificationHelper.wasEventNotifiedToday(context, eventKey)) {
                        NotificationHelper.markEventNotifiedToday(context, eventKey)
                        NotificationHelper.showDocenteClassReminder(
                            context = context,
                            materiaNombre = materia,
                            bloqueHorario = bloque,
                            horaInicio = horaInicio
                        )
                    }
                }
                TYPE_DELAY_10_MIN_AFTER -> {
                    // Solo disparar si el docente aún no ha registrado su asistencia de ENTRADA hoy
                    if (!NotificationHelper.hasDocenteRegisteredEntradaToday(context)) {
                        val eventKey = "doc_delay10_${claseId}_$materia"
                        if (!NotificationHelper.wasEventNotifiedToday(context, eventKey)) {
                            NotificationHelper.markEventNotifiedToday(context, eventKey)
                            NotificationHelper.showDocenteRetrasoAlert(
                                context = context,
                                materiaNombre = materia,
                                bloqueHorario = bloque,
                                minutosRetraso = Constants.MINUTOS_ALERTA_RETARDO
                            )
                        }
                    }
                }
            }
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Exception) {}
        }
    }
}
