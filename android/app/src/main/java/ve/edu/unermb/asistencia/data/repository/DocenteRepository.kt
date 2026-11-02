package ve.edu.unermb.asistencia.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.remote.ApiService
import ve.edu.unermb.asistencia.data.remote.dto.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocenteRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getMiHorario(): Resource<HorarioDocenteResponse> {
        return try {
            val response = apiService.getMiHorario()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al cargar horario del docente.")
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de conexión al obtener horario: ${e.localizedMessage}")
        }
    }

    suspend fun getHistorial(): Resource<HistorialResponse> {
        return try {
            val response = apiService.getHistorialDocente()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al obtener historial de asistencias.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de conexión: ${e.localizedMessage}")
        }
    }

    suspend fun marcarAsistenciaQR(qrPayload: String): Resource<MarcajeQRResponse> {
        return try {
            val response = apiService.registrarMarcajeQR(MarcajeQRRequest(qrPayload))
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = try {
                    JSONObject(errorBody ?: "").optString("error", "Error al procesar marcaje QR.")
                } catch (_: Exception) {
                    "Error de marcaje: código ${response.code()}"
                }
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Resource.Error("Error de comunicación: ${e.localizedMessage}")
        }
    }

    suspend fun getMisMaterias(): Resource<MateriasCatalogResponse> {
        return try {
            val response = apiService.getMisMaterias()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("Error al consultar materias asignadas.")
            }
        } catch (e: Exception) {
            Resource.Error("Error al obtener materias: ${e.localizedMessage}")
        }
    }

    suspend fun justificarRetardo(
        asistenciaId: Int,
        motivo: String,
        evidenciaFile: File?
    ): Resource<JustificarRetardoResponse> {
        return try {
            val motivoBody = motivo.toRequestBody("text/plain".toMediaTypeOrNull())
            val evidenciaPart = evidenciaFile?.let { file ->
                val reqFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("evidencia", file.name, reqFile)
            }
            val response = apiService.justificarAsistencia(
                asistenciaId = asistenciaId,
                motivo = motivoBody,
                evidencia = evidenciaPart
            )
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = try {
                    JSONObject(errorBody ?: "").optString("error", "No se pudo enviar el justificativo.")
                } catch (_: Exception) {
                    "Error al enviar justificativo: código ${response.code()}"
                }
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de conexión al enviar justificativo: ${e.localizedMessage}")
        }
    }
}

