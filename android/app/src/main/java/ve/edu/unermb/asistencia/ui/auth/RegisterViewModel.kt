package ve.edu.unermb.asistencia.ui.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.remote.dto.PensumEstructuraResponse
import ve.edu.unermb.asistencia.data.remote.dto.TrayectoDto
import ve.edu.unermb.asistencia.data.repository.AuthRepository
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class RegisterEvent {
    data class ShowSnackbar(val message: String) : RegisterEvent()
    object RegistrationSuccessPending : RegisterEvent()
}

data class RegisterUiState(
    val nombre: String = "",
    val apellido: String = "",
    val tipoCedula: String = "V",
    val cedulaDigits: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val selectedPhotoUri: Uri? = null,
    val compressedPhotoFile: File? = null,
    val selectedMateriasIds: List<Int> = emptyList(),
    val estructuraPensum: List<TrayectoDto> = emptyList(),
    val isLoading: Boolean = false,
    val showSuccessDialog: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<RegisterEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        loadPensumEstructura()
    }

    private fun loadPensumEstructura() {
        viewModelScope.launch {
            val res = authRepository.getPensumEstructura()
            if (res is Resource.Success && res.data?.estructura != null) {
                _uiState.value = _uiState.value.copy(estructuraPensum = res.data.estructura)
            }
        }
    }

    fun onNombreChange(value: String) {
        _uiState.value = _uiState.value.copy(nombre = value)
    }

    fun onApellidoChange(value: String) {
        _uiState.value = _uiState.value.copy(apellido = value)
    }

    fun onTipoCedulaChange(value: String) {
        _uiState.value = _uiState.value.copy(tipoCedula = value)
    }

    fun onCedulaDigitsChange(value: String) {
        // Filtro estricto: solo dígitos y máximo 8 caracteres
        if (value.length <= 8 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(cedulaDigits = value)
        }
    }

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value)
    }

    fun toggleMateriaSelection(materiaId: Int) {
        val current = _uiState.value.selectedMateriasIds.toMutableList()
        if (current.contains(materiaId)) {
            current.remove(materiaId)
        } else {
            if (current.size >= 5) {
                viewModelScope.launch {
                    _eventFlow.emit(RegisterEvent.ShowSnackbar("Máximo permitido: 5 materias del pensum."))
                }
                return
            }
            current.add(materiaId)
        }
        _uiState.value = _uiState.value.copy(selectedMateriasIds = current)
    }

    fun onPhotoSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(selectedPhotoUri = uri)
            val compressed = compressImageFile(context, uri)
            _uiState.value = _uiState.value.copy(compressedPhotoFile = compressed)
        }
    }

    /**
     * Compresión de imagen previa al envío multipart (< 5MB, 1024px)
     */
    private suspend fun compressImageFile(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Escalar manteniendo relación de aspecto si supera 1024 px
            val maxDimension = 1024
            val width = originalBitmap.width
            val height = originalBitmap.height
            val scale = if (width > maxDimension || height > maxDimension) {
                if (width > height) maxDimension.toFloat() / width else maxDimension.toFloat() / height
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(
                    originalBitmap,
                    (width * scale).toInt(),
                    (height * scale).toInt(),
                    true
                )
            } else {
                originalBitmap
            }

            val outputFile = File(context.cacheDir, "carnet_temp_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(outputFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            outputFile
        } catch (_: Exception) {
            null
        }
    }

    fun submitRegister() {
        val s = _uiState.value

        if (s.nombre.isBlank() || s.apellido.isBlank()) {
            emitSnackbar("Debe ingresar nombres y apellidos completos.")
            return
        }
        if (s.cedulaDigits.isBlank() || s.cedulaDigits.length > 8) {
            emitSnackbar("Cédula de Identidad requerida (máx. 8 dígitos).")
            return
        }
        if (s.email.isBlank() || !s.email.contains("@")) {
            emitSnackbar("Debe ingresar un correo electrónico válido.")
            return
        }
        if (s.password.length < 8) {
            emitSnackbar("La contraseña debe tener al menos 8 caracteres.")
            return
        }
        if (s.password != s.confirmPassword) {
            emitSnackbar("La confirmación de contraseña no coincide.")
            return
        }
        if (s.compressedPhotoFile == null) {
            emitSnackbar("La Fotografía Tipo Carnet es OBLIGATORIA para el registro.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = authRepository.registerDocente(
                nombre = s.nombre,
                apellido = s.apellido,
                tipoCedula = s.tipoCedula,
                cedulaDigits = s.cedulaDigits,
                email = s.email,
                password = s.password,
                confirmPassword = s.confirmPassword,
                materiasIds = s.selectedMateriasIds,
                fotoFile = s.compressedPhotoFile
            )

            _uiState.value = _uiState.value.copy(isLoading = false)

            if (result is Resource.Success) {
                _uiState.value = _uiState.value.copy(showSuccessDialog = true)
                _eventFlow.emit(RegisterEvent.RegistrationSuccessPending)
            } else {
                emitSnackbar(result.message ?: "Ocurrió un error en el registro.")
            }
        }
    }

    private fun emitSnackbar(msg: String) {
        viewModelScope.launch {
            _eventFlow.emit(RegisterEvent.ShowSnackbar(msg))
        }
    }
}

