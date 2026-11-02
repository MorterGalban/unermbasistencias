package ve.edu.unermb.asistencia.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val dataStoreManager: DataStoreManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        val token = runBlocking {
            dataStoreManager.getCachedToken()
        }

        val requestBuilder = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("User-Agent", "UNERMB-Android-Native/1.0.0")

        if (!token.isNullOrBlank() && originalRequest.header("Authorization") == null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        // Si el token expira o es revocado (401), se puede limpiar la sesión
        if (response.code == 401 && !token.isNullOrBlank()) {
            runBlocking {
                dataStoreManager.clearSession()
            }
        }

        return response
    }
}

