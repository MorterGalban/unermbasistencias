package ve.edu.unermb.asistencia.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.data.remote.dto.HorarioBloqueDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Programa estrictamente los dos eventos temporizados del Rol DOCENTE para las clases de hoy:
     * 1. Notificación preventiva exactamente 10 minutos antes del inicio (hora_inicio - 10 min)
     * 2. Notificación de alerta de retardo exactamente 10 minutos después del inicio (hora_inicio + 10 min)
     */
    fun scheduleTodayClassReminders(clasesHoy: List<HorarioBloqueDto>) {
        val diaActual = DateTimeUtils.todayDayOfWeekSpanish()

        for (clase in clasesHoy) {
            if (clase.dia_semana.equals(diaActual, ignoreCase = true)) {
                schedulePreventiveReminder10MinBefore(clase)
                scheduleDelayCheck10MinAfter(clase)
            }
        }
    }

    private fun schedulePreventiveReminder10MinBefore(clase: HorarioBloqueDto) {
        val triggerEpochMillis = DateTimeUtils.calculateReminderEpochMillis(clase.hora_inicio) ?: return

        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            action = ClassAlarmReceiver.ACTION_ALARM
            putExtra(ClassAlarmReceiver.EXTRA_ALARM_TYPE, ClassAlarmReceiver.TYPE_PREVENTIVE_10_MIN_BEFORE)
            putExtra(ClassAlarmReceiver.EXTRA_CLASE_ID, clase.id)
            putExtra(ClassAlarmReceiver.EXTRA_MATERIA_NOMBRE, clase.materia_nombre)
            putExtra(ClassAlarmReceiver.EXTRA_BLOQUE_HORARIO, clase.bloque_horario)
            putExtra(ClassAlarmReceiver.EXTRA_HORA_INICIO, clase.hora_inicio)
        }

        val requestCode = (clase.id * 1000) + (clase.bloque_numero * 10) + 1
        scheduleExactAlarm(requestCode, triggerEpochMillis, intent)
    }

    private fun scheduleDelayCheck10MinAfter(clase: HorarioBloqueDto) {
        val triggerEpochMillis = DateTimeUtils.calculateDelayCheckEpochMillis(clase.hora_inicio) ?: return

        val intent = Intent(context, ClassAlarmReceiver::class.java).apply {
            action = ClassAlarmReceiver.ACTION_ALARM
            putExtra(ClassAlarmReceiver.EXTRA_ALARM_TYPE, ClassAlarmReceiver.TYPE_DELAY_10_MIN_AFTER)
            putExtra(ClassAlarmReceiver.EXTRA_CLASE_ID, clase.id)
            putExtra(ClassAlarmReceiver.EXTRA_MATERIA_NOMBRE, clase.materia_nombre)
            putExtra(ClassAlarmReceiver.EXTRA_BLOQUE_HORARIO, clase.bloque_horario)
            putExtra(ClassAlarmReceiver.EXTRA_HORA_INICIO, clase.hora_inicio)
        }

        val requestCode = (clase.id * 1000) + (clase.bloque_numero * 10) + 2
        scheduleExactAlarm(requestCode, triggerEpochMillis, intent)
    }

    private fun scheduleExactAlarm(requestCode: Int, triggerAtMillis: Long, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e("Scheduler", "Permiso de alarma exacta no disponible: ${e.message}")
        }
    }
}
