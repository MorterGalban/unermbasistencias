package ve.edu.unermb.asistencia.data.repository

import org.json.JSONObject
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.remote.ApiService
import ve.edu.unermb.asistencia.data.remote.dto.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuperrootRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getDashboardStats(): Resource<DashboardStatsResponse> {
        return try {
            val response = apiService.getDashboardStats()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al cargar métricas del dashboard.")
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de conexión: ${e.localizedMessage}")
        }
    }

    suspend fun getSolicitudesPendientes(): Resource<SolicitudesResponse> {
        return try {
            val response = apiService.getSolicitudesPendientes()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al obtener solicitudes pendientes.")
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de red: ${e.localizedMessage}")
        }
    }

    suspend fun aprobarSolicitud(docenteId: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.aprobarSolicitud(docenteId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    JSONObject(it).optString("error", "Error al aprobar solicitud.")
                } ?: "Error al aprobar."
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun rechazarSolicitud(docenteId: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.rechazarSolicitud(docenteId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    JSONObject(it).optString("error", "Error al rechazar solicitud.")
                } ?: "Error al rechazar."
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun getDocentes(): Resource<DocentesListResponse> {
        return try {
            val response = apiService.getDocentes()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al cargar lista de docentes.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun getDocenteHorario(docenteId: Int): Resource<DocenteHorarioResponse> {
        return try {
            val response = apiService.getDocenteHorario(docenteId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al cargar horario del docente.")
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun asignarDocenteHorario(docenteId: Int, materiaId: Int, diaSemana: String, bloqueNumero: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.asignarDocenteHorario(
                docenteId = docenteId,
                request = AsignarHorarioRequest(materiaId, diaSemana, bloqueNumero)
            )
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    JSONObject(it).optString("error", "Error al asignar horario.")
                } ?: "Error al asignar horario."
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun eliminarDocenteHorario(horarioId: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.eliminarDocenteHorario(horarioId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("No se pudo eliminar el bloque.")
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun asignarMateriaDocente(docenteId: Int, materiaId: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.asignarMateriaDocente(docenteId, AsignarMateriaRequest(materiaId))
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorMsg = response.errorBody()?.string()?.let {
                    JSONObject(it).optString("error", "Error al asignar materia.")
                } ?: "Error al asignar materia."
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun eliminarMateriaDocente(docenteId: Int, materiaId: Int): Resource<SimpleResponse> {
        return try {
            val response = apiService.eliminarMateriaDocente(docenteId, materiaId)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("No se pudo remover la materia.")
            }
        } catch (e: Exception) {
            Resource.Error("Error: ${e.localizedMessage}")
        }
    }

    suspend fun generarQROficial(): Resource<QRTokenResponse> {
        return try {
            val response = apiService.generarQROficial()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al solicitar código QR institucional.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun renovarQROficial(): Resource<QRTokenResponse> {
        return try {
            val response = apiService.renovarQROficial()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al renovar código QR institucional.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun getAsistenciasLogs(
        fecha: String? = null,
        docenteId: String? = null,
        tipo: String? = null,
        puntualidad: String? = null
    ): Resource<AsistenciasLogsResponse> {
        return try {
            val response = apiService.getAsistenciasLogs(fecha, docenteId, tipo, puntualidad)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al consultar logs de asistencia.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun getNotificacionesEventos(): Resource<NotificacionesEventosResponse> {
        return try {
            val response = apiService.getNotificacionesEventos()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al consultar eventos de notificación.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }
}

