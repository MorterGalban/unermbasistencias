package ve.edu.unermb.asistencia.data.repository

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.remote.ApiService
import ve.edu.unermb.asistencia.data.remote.dto.*
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val dataStoreManager: DataStoreManager
) {

    suspend fun login(identificador: String, pass: String): Resource<LoginResponse> {
        return try {
            val response = apiService.login(LoginRequest(identificador.trim(), pass))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.success && body.token != null && body.usuario != null) {
                    // Persistir sesión automáticamente
                    dataStoreManager.saveAuthSession(body.token, body.usuario)
                    Resource.Success(body)
                } else {
                    Resource.Error(body.error ?: "Error al iniciar sesión.")
                }
            } else {
                val errorBody = response.errorBody()?.string()
                val errorMsg = try {
                    JSONObject(errorBody ?: "").optString("error", "Error de autenticación.")
                } catch (_: Exception) {
                    "Error ${response.code()}: Verifique sus credenciales."
                }
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de conexión con el servidor UNERMB: ${e.localizedMessage ?: "Revise su red."}")
        }
    }

    suspend fun registerDocente(
        nombre: String,
        apellido: String,
        tipoCedula: String,
        cedulaDigits: String,
        email: String,
        password: String,
        confirmPassword: String,
        materiasIds: List<Int>,
        fotoFile: File
    ): Resource<SimpleResponse> {
        return try {
            val textPlain = "text/plain".toMediaTypeOrNull()

            val nombrePart = nombre.trim().toRequestBody(textPlain)
            val apellidoPart = apellido.trim().toRequestBody(textPlain)
            val tipoCedulaPart = tipoCedula.trim().toRequestBody(textPlain)
            val cedulaPart = cedulaDigits.trim().toRequestBody(textPlain)
            val emailPart = email.trim().lowercase().toRequestBody(textPlain)
            val passPart = password.toRequestBody(textPlain)
            val confirmPassPart = confirmPassword.toRequestBody(textPlain)

            val jsonArray = JSONArray(materiasIds)
            val materiasPart = jsonArray.toString().toRequestBody(textPlain)

            val mime = when {
                fotoFile.name.endsWith(".png", ignoreCase = true) -> "image/png"
                fotoFile.name.endsWith(".webp", ignoreCase = true) -> "image/webp"
                else -> "image/jpeg"
            }
            val requestFile = fotoFile.asRequestBody(mime.toMediaTypeOrNull())
            val fotoPart = MultipartBody.Part.createFormData("foto_carnet", fotoFile.name, requestFile)

            val response = apiService.registerDocente(
                nombre = nombrePart,
                apellido = apellidoPart,
                tipoCedula = tipoCedulaPart,
                cedula = cedulaPart,
                email = emailPart,
                password = passPart,
                confirmPassword = confirmPassPart,
                materias = materiasPart,
                fotoCarnet = fotoPart
            )

            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = try {
                    JSONObject(errorBody ?: "").optString("error", "Error en el registro.")
                } catch (_: Exception) {
                    "Error en registro: código ${response.code()}"
                }
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Resource.Error("Error al enviar registro: ${e.localizedMessage}")
        }
    }

    suspend fun checkSession(): Resource<UserDto> {
        return try {
            val response = apiService.getMe()
            if (response.isSuccessful && response.body()?.success == true && response.body()?.usuario != null) {
                val user = response.body()!!.usuario!!
                dataStoreManager.updateUserData(user)
                Resource.Success(user)
            } else {
                Resource.Error("Sesión expirada o inválida.")
            }
        } catch (e: Exception) {
            Resource.Error("No se pudo verificar la sesión: ${e.localizedMessage}")
        }
    }

    suspend fun resetPassword(request: ResetPasswordRequest): Resource<SimpleResponse> {
        return try {
            val response = apiService.resetPassword(request)
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string()
                val msg = try {
                    JSONObject(errorBody ?: "").optString("error", "Error al restablecer contraseña.")
                } catch (_: Exception) {
                    "Error al restablecer contraseña."
                }
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            Resource.Error("Fallo de red: ${e.localizedMessage}")
        }
    }

    suspend fun getPensumEstructura(): Resource<PensumEstructuraResponse> {
        return try {
            val response = apiService.getPensumEstructura()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("No se pudo obtener el pensum.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun getMateriasCatalog(): Resource<MateriasCatalogResponse> {
        return try {
            val response = apiService.getMateriasCatalog()
            if (response.isSuccessful && response.body() != null) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("No se pudo obtener el catálogo de materias.")
            }
        } catch (e: Exception) {
            Resource.Error("Error de red: ${e.localizedMessage}")
        }
    }

    suspend fun logout() {
        dataStoreManager.clearSession()
    }
}

