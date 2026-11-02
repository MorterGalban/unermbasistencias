package ve.edu.unermb.asistencia.core

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T, message: String? = null) : Resource<T>(data, message)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(val isLoading: Boolean = true) : Resource<T>(null)
}

