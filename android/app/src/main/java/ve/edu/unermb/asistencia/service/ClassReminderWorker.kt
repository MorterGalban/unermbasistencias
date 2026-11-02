package ve.edu.unermb.asistencia.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.repository.DocenteRepository

@HiltWorker
class ClassReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dataStoreManager: DataStoreManager,
    private val docenteRepository: DocenteRepository,
    private val scheduler: ClassReminderScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = dataStoreManager.getCachedToken()
        val rol = dataStoreManager.userRolFlow.firstOrNull()

        if (token.isNullOrBlank() || rol != Constants.ROL_DOCENTE) {
            return Result.success()
        }

        NotificationHelper.setSessionRole(context, Constants.ROL_DOCENTE)

        // 1. Obtener horario oficial asignado
        val horarioRes = docenteRepository.getMiHorario()
        if (horarioRes !is Resource.Success || horarioRes.data?.horarios == null) {
            return Result.retry()
        }

        val diaActual = DateTimeUtils.todayDayOfWeekSpanish()
        val clasesHoy = horarioRes.data.horarios.filter { it.dia_semana.equals(diaActual, ignoreCase = true) }

        // 2. Programar alarmas exactas (10 min antes y 10 min después del inicio de cada bloque)
        scheduler.scheduleTodayClassReminders(clasesHoy)

        // 3. Verificar historial de hoy para sincronizar si ya marcó entrada
        val historialRes = docenteRepository.getHistorial()
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        val marcajesEntradaHoy = historialRes.data?.historial?.filter {
            it.tipo == "ENTRADA" && it.fecha_hora.startsWith(todayIso)
        } ?: emptyList()

        if (marcajesEntradaHoy.isNotEmpty()) {
            NotificationHelper.markDocenteEntradaRegisteredToday(context)
            return Result.success()
        }

        // Si ya pasaron >= 10 minutos del inicio de un bloque activo hoy y aún no ha marcado entrada, disparar alerta de retardo (una sola vez por bloque)
        val horaActualMinutos = DateTimeUtils.timeToMinutes(
            DateTimeUtils.nowInVenezuela().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        )

        for (clase in clasesHoy) {
            val inicioMinutos = DateTimeUtils.timeToMinutes(clase.hora_inicio)
            val finMinutos = DateTimeUtils.timeToMinutes(clase.hora_fin)

            if (horaActualMinutos >= inicioMinutos + Constants.MINUTOS_ALERTA_RETARDO && horaActualMinutos <= finMinutos) {
                val eventKey = "doc_delay10_${clase.id}_${clase.materia_nombre}"
                if (!NotificationHelper.wasEventNotifiedToday(context, eventKey)) {
                    NotificationHelper.markEventNotifiedToday(context, eventKey)
                    NotificationHelper.showDocenteRetrasoAlert(
                        context = context,
                        materiaNombre = clase.materia_nombre,
                        bloqueHorario = clase.bloque_horario,
                        minutosRetraso = horaActualMinutos - inicioMinutos
                    )
                }
            }
        }

        return Result.success()
    }
}
