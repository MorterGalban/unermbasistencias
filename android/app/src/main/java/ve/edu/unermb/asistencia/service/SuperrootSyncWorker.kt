package ve.edu.unermb.asistencia.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.firstOrNull
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.repository.SuperrootRepository

@HiltWorker
class SuperrootSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val dataStoreManager: DataStoreManager,
    private val superrootRepository: SuperrootRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = dataStoreManager.getCachedToken()
        val rol = dataStoreManager.userRolFlow.firstOrNull()

        // Segmentación estricta por rol: solo se ejecuta para el Rol SUPERROOT (Coordinador)
        if (token.isNullOrBlank() || rol != Constants.ROL_SUPERROOT) {
            return Result.success()
        }

        NotificationHelper.setSessionRole(context, Constants.ROL_SUPERROOT)

        val eventosRes = superrootRepository.getNotificacionesEventos()
        if (eventosRes is Resource.Success && eventosRes.data != null) {
            val data = eventosRes.data

            // 1. Solicitudes de registro de nuevos usuarios (PENDIENTE)
            data.solicitudesPendientes.orEmpty().forEach { sol ->
                val key = "sr_sol_${sol.id}"
                if (!NotificationHelper.wasEventNotifiedToday(context, key)) {
                    NotificationHelper.markEventNotifiedToday(context, key)
                    NotificationHelper.showSuperrootSolicitudAlert(
                        context = context,
                        docenteNombre = "${sol.nombre} ${sol.apellido}",
                        cedula = sol.cedula
                    )
                }
            }

            // 2. Registros de marcaje de entrada y salida
            data.marcajesRecientes.orEmpty().take(5).forEach { m ->
                val key = "sr_marcaje_${m.id}"
                if (!NotificationHelper.wasEventNotifiedToday(context, key)) {
                    NotificationHelper.markEventNotifiedToday(context, key)
                    NotificationHelper.showSuperrootMarcajeAlert(
                        context = context,
                        docenteNombre = "${m.nombre} ${m.apellido}",
                        tipo = m.tipo,
                        materia = m.materia_detectada ?: "Jornada Docente",
                        puntualidad = m.puntualidad_estado ?: "A_TIEMPO",
                        minutosRetraso = m.minutos_retraso ?: 0
                    )
                }
            }

            // 3. Alertas por inasistencia o retardo en clases programadas hoy
            data.alertasInasistencia.orEmpty().forEach { alerta ->
                val key = "sr_inasist_${alerta.horario_id}_${alerta.fecha}"
                if (!NotificationHelper.wasEventNotifiedToday(context, key)) {
                    NotificationHelper.markEventNotifiedToday(context, key)
                    NotificationHelper.showSuperrootInasistenciaAlert(
                        context = context,
                        docenteNombre = "${alerta.nombre} ${alerta.apellido}",
                        materia = alerta.materia_nombre,
                        bloqueHorario = alerta.bloque_horario,
                        minutosTranscurridos = alerta.minutos_transcurridos
                    )
                }
            }

            // 4. Recepción de justificativos enviados por los docentes
            data.justificacionesRecientes.orEmpty().take(5).forEach { justif ->
                val key = "sr_justif_${justif.id}_${justif.justificacion_fecha ?: ""}"
                if (!NotificationHelper.wasEventNotifiedToday(context, key)) {
                    NotificationHelper.markEventNotifiedToday(context, key)
                    NotificationHelper.showSuperrootJustificativoAlert(
                        context = context,
                        docenteNombre = "${justif.nombre} ${justif.apellido}",
                        materia = justif.materia_detectada ?: "Clase",
                        motivo = justif.justificacion_texto ?: "Evidencia adjunta"
                    )
                }
            }
        }

        return Result.success()
    }
}
