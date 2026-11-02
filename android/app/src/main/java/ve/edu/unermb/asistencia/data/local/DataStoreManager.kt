package ve.edu.unermb.asistencia.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.data.remote.dto.UserDto
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

@Singleton
class DataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    // Cache en memoria para acceso sincrónico rápido desde OkHttp AuthInterceptor y ViewModels
    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cachedBaseUrl: String = Constants.DEFAULT_BASE_URL

    companion object {
        val KEY_JWT = stringPreferencesKey(Constants.KEY_JWT_TOKEN)
        val KEY_ID = intPreferencesKey(Constants.KEY_USER_ID)
        val KEY_NOMBRE = stringPreferencesKey(Constants.KEY_USER_NOMBRE)
        val KEY_APELLIDO = stringPreferencesKey(Constants.KEY_USER_APELLIDO)
        val KEY_CEDULA = stringPreferencesKey(Constants.KEY_USER_CEDULA)
        val KEY_EMAIL = stringPreferencesKey(Constants.KEY_USER_EMAIL)
        val KEY_ROL = stringPreferencesKey(Constants.KEY_USER_ROL)
        val KEY_ESTADO = stringPreferencesKey(Constants.KEY_USER_ESTADO)
        val KEY_FOTO = stringPreferencesKey(Constants.KEY_USER_FOTO)
        val KEY_BASE_URL = stringPreferencesKey(Constants.KEY_CUSTOM_BASE_URL)
    }

    // Flujos observables
    val tokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        val token = prefs[KEY_JWT]
        cachedToken = token
        token
    }

    val userRolFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ROL]
    }

    val userEstadoFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_ESTADO]
    }

    val baseUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        val url = prefs[KEY_BASE_URL] ?: Constants.DEFAULT_BASE_URL
        cachedBaseUrl = url
        url
    }

    val userProfileFlow: Flow<UserDto?> = dataStore.data.map { prefs ->
        val id = prefs[KEY_ID] ?: return@map null
        UserDto(
            id = id,
            nombre = prefs[KEY_NOMBRE] ?: "",
            apellido = prefs[KEY_APELLIDO] ?: "",
            cedula = prefs[KEY_CEDULA] ?: "",
            email = prefs[KEY_EMAIL] ?: "",
            foto_carnet_url = prefs[KEY_FOTO],
            rol = prefs[KEY_ROL] ?: Constants.ROL_DOCENTE,
            estado = prefs[KEY_ESTADO] ?: Constants.ESTADO_ACTIVO,
            materias = emptyList()
        )
    }

    suspend fun saveAuthSession(token: String, user: UserDto) {
        cachedToken = token
        dataStore.edit { prefs ->
            prefs[KEY_JWT] = token
            prefs[KEY_ID] = user.id
            prefs[KEY_NOMBRE] = user.nombre
            prefs[KEY_APELLIDO] = user.apellido
            prefs[KEY_CEDULA] = user.cedula
            prefs[KEY_EMAIL] = user.email
            prefs[KEY_ROL] = user.rol
            prefs[KEY_ESTADO] = user.estado
            user.foto_carnet_url?.let { prefs[KEY_FOTO] = it }
        }
    }

    suspend fun updateUserData(user: UserDto) {
        dataStore.edit { prefs ->
            prefs[KEY_ID] = user.id
            prefs[KEY_NOMBRE] = user.nombre
            prefs[KEY_APELLIDO] = user.apellido
            prefs[KEY_CEDULA] = user.cedula
            prefs[KEY_EMAIL] = user.email
            prefs[KEY_ROL] = user.rol
            prefs[KEY_ESTADO] = user.estado
            user.foto_carnet_url?.let { prefs[KEY_FOTO] = it }
        }
    }

    suspend fun getCachedToken(): String? {
        if (cachedToken != null) return cachedToken
        cachedToken = dataStore.data.firstOrNull()?.get(KEY_JWT)
        return cachedToken
    }

    fun getCachedBaseUrl(): String = cachedBaseUrl

    suspend fun getBaseUrl(): String {
        val url = dataStore.data.firstOrNull()?.get(KEY_BASE_URL) ?: Constants.DEFAULT_BASE_URL
        cachedBaseUrl = url
        return url
    }

    suspend fun setBaseUrl(url: String) {
        cachedBaseUrl = url
        dataStore.edit { prefs ->
            prefs[KEY_BASE_URL] = url
        }
    }

    suspend fun clearSession() {
        cachedToken = null
        dataStore.edit { prefs ->
            prefs.remove(KEY_JWT)
            prefs.remove(KEY_ID)
            prefs.remove(KEY_NOMBRE)
            prefs.remove(KEY_APELLIDO)
            prefs.remove(KEY_CEDULA)
            prefs.remove(KEY_EMAIL)
            prefs.remove(KEY_ROL)
            prefs.remove(KEY_ESTADO)
            prefs.remove(KEY_FOTO)
        }
    }
}

