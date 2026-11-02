package ve.edu.unermb.asistencia.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.remote.dto.ResetPasswordRequest
import ve.edu.unermb.asistencia.data.repository.AuthRepository
import ve.edu.unermb.asistencia.ui.navigation.Screen
import javax.inject.Inject

sealed class LoginEvent {
    data class Navigate(val route: String) : LoginEvent()
    data class ShowSnackbar(val message: String) : LoginEvent()
}

data class LoginUiState(
    val identificador: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isCheckingSession: Boolean = true,
    val showForgotPasswordDialog: Boolean = false,
    val showServerConfigDialog: Boolean = false,
    val currentBaseUrl: String = Constants.DEFAULT_BASE_URL
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<LoginEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        checkPersistentSession()
    }

    /**
     * Requerimiento 2: Sesión Persistente Automática.
     * Al abrir la app, verifica en DataStore si existe un JWT previo.
     * Si es válido, salta el login directamente al panel correspondiente.
     */
    fun checkPersistentSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingSession = true)
            val token = dataStoreManager.getCachedToken()
            val savedBaseUrl = dataStoreManager.getBaseUrl()
            _uiState.value = _uiState.value.copy(currentBaseUrl = savedBaseUrl)

            if (!token.isNullOrBlank()) {
                val sessionRes = authRepository.checkSession()
                if (sessionRes is Resource.Success && sessionRes.data != null) {
                    val user = sessionRes.data
                    if (user.estado == Constants.ESTADO_ACTIVO) {
                        val destination = if (user.rol == Constants.ROL_SUPERROOT) {
                            Screen.SuperrootDashboard.route
                        } else {
                            Screen.DocenteDashboard.route
                        }
                        _uiState.value = _uiState.value.copy(isCheckingSession = false)
                        _eventFlow.emit(LoginEvent.Navigate(destination))
                        return@launch
                    } else {
                        // Estado pendiente o inactivo
                        _eventFlow.emit(LoginEvent.ShowSnackbar("Su cuenta está ${user.estado}. Contacte a Coordinación."))
                        dataStoreManager.clearSession()
                    }
                } else {
                    // Token inválido o expirado
                    dataStoreManager.clearSession()
                }
            }
            _uiState.value = _uiState.value.copy(isCheckingSession = false)
        }
    }

    fun onIdentificadorChange(value: String) {
        _uiState.value = _uiState.value.copy(identificador = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun togglePasswordVisibility() {
        _uiState.value = _uiState.value.copy(isPasswordVisible = !_uiState.value.isPasswordVisible)
    }

    fun setForgotPasswordDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showForgotPasswordDialog = visible)
    }

    fun setServerConfigDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showServerConfigDialog = visible)
    }

    fun updateBaseUrl(newUrl: String) {
        viewModelScope.launch {
            dataStoreManager.setBaseUrl(newUrl)
            _uiState.value = _uiState.value.copy(currentBaseUrl = newUrl, showServerConfigDialog = false)
            _eventFlow.emit(LoginEvent.ShowSnackbar("URL del backend actualizada: $newUrl"))
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.identificador.isBlank() || state.password.isBlank()) {
            viewModelScope.launch {
                _eventFlow.emit(LoginEvent.ShowSnackbar("Ingrese su correo/cédula y contraseña."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = authRepository.login(state.identificador, state.password)
            _uiState.value = _uiState.value.copy(isLoading = false)

            when (result) {
                is Resource.Success -> {
                    val user = result.data?.usuario
                    val destination = if (user?.rol == Constants.ROL_SUPERROOT) {
                        Screen.SuperrootDashboard.route
                    } else {
                        Screen.DocenteDashboard.route
                    }
                    _eventFlow.emit(LoginEvent.Navigate(destination))
                }
                is Resource.Error -> {
                    _eventFlow.emit(LoginEvent.ShowSnackbar(result.message ?: "Credenciales inválidas."))
                }
                else -> Unit
            }
        }
    }

    fun submitResetPassword(
        tipoCedula: String,
        cedulaDigits: String,
        email: String,
        newPass: String,
        confirmPass: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val request = ResetPasswordRequest(
                cedula = cedulaDigits,
                tipo_cedula = tipoCedula,
                email = email,
                new_password = newPass,
                confirm_password = confirmPass
            )
            val res = authRepository.resetPassword(request)
            _uiState.value = _uiState.value.copy(isLoading = false, showForgotPasswordDialog = false)

            if (res is Resource.Success) {
                _eventFlow.emit(LoginEvent.ShowSnackbar(res.data?.message ?: "Contraseña actualizada exitosamente."))
            } else {
                _eventFlow.emit(LoginEvent.ShowSnackbar(res.message ?: "Error al restablecer contraseña."))
            }
        }
    }
}

