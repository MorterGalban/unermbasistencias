package ve.edu.unermb.asistencia.ui.docente

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.core.ImageUtils
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.remote.dto.*
import ve.edu.unermb.asistencia.data.repository.AuthRepository
import ve.edu.unermb.asistencia.data.repository.DocenteRepository
import ve.edu.unermb.asistencia.service.ClassReminderScheduler
import ve.edu.unermb.asistencia.service.NotificationHelper
import javax.inject.Inject

sealed class DocenteEvent {
    data class ShowSnackbar(val message: String) : DocenteEvent()
    object SessionTerminated : DocenteEvent()
    data class MarcajeSuccess(val response: MarcajeQRResponse) : DocenteEvent()
}

data class DocenteUiState(
    val userProfile: UserDto? = null,
    val baseUrl: String = Constants.DEFAULT_BASE_URL,
    val liveClockVenezuela: String = "",
    val liveDateVenezuela: String = "",
    val activeTab: Int = 0, // 0: Horario, 1: Escáner QR, 2: Historial
    val horarioData: HorarioDocenteResponse? = null,
    val historialData: HistorialResponse? = null,
    val isLoadingHorario: Boolean = false,
    val isLoadingHistorial: Boolean = false,
    val isScanningLocked: Boolean = false,
    val lastMarcajeResult: MarcajeQRResponse? = null,
    val showResultDialog: Boolean = false,
    val scanErrorMessage: String? = null,
    // Módulo opcional de justificación por retardo
    val showJustificacionDialog: Boolean = false,
    val justificacionTargetId: Int? = null,
    val justificacionMateria: String = "",
    val justificacionMinutos: Int = 0,
    val isSubmittingJustificacion: Boolean = false
)

@HiltViewModel
class DocenteViewModel @Inject constructor(
    private val docenteRepository: DocenteRepository,
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager,
    private val classReminderScheduler: ClassReminderScheduler,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DocenteUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<DocenteEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        NotificationHelper.setSessionRole(context, Constants.ROL_DOCENTE)
        observeUserProfile()
        startLiveVenezuelaClock()
        loadHorario()
        loadHistorial()
    }

    private fun observeUserProfile() {
        viewModelScope.launch {
            val currentBase = dataStoreManager.getBaseUrl()
            _uiState.value = _uiState.value.copy(baseUrl = currentBase)
            dataStoreManager.userProfileFlow.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userProfile = profile,
                    baseUrl = dataStoreManager.getBaseUrl()
                )
            }
        }
    }

    /**
     * Reloj en vivo sincronizado con la hora de Venezuela (UTC-4)
     */
    private fun startLiveVenezuelaClock() {
        viewModelScope.launch {
            while (isActive) {
                _uiState.value = _uiState.value.copy(
                    liveClockVenezuela = DateTimeUtils.formatVenezuelaClock(),
                    liveDateVenezuela = DateTimeUtils.formatVenezuelaDate()
                )
                delay(1000)
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTab = index)
        if (index == 0) loadHorario()
        if (index == 2) loadHistorial()
    }

    fun loadHorario() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHorario = true)
            val res = docenteRepository.getMiHorario()
            _uiState.value = _uiState.value.copy(isLoadingHorario = false)

            if (res is Resource.Success && res.data != null) {
                _uiState.value = _uiState.value.copy(horarioData = res.data)
                // Programar automáticamente los 2 eventos temporizados de hoy (10 min antes y 10 min después)
                val clasesHoy = res.data.clasesHoy ?: emptyList()
                classReminderScheduler.scheduleTodayClassReminders(clasesHoy)
            } else {
                _eventFlow.emit(DocenteEvent.ShowSnackbar(res.message ?: "Error al cargar horario."))
            }
        }
    }

    fun loadHistorial() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingHistorial = true)
            val res = docenteRepository.getHistorial()
            _uiState.value = _uiState.value.copy(isLoadingHistorial = false)

            if (res is Resource.Success && res.data != null) {
                _uiState.value = _uiState.value.copy(historialData = res.data)
                val todayIso = DateTimeUtils.todayIsoInVenezuela()
                val marcoEntradaHoy = res.data.historial.orEmpty().any {
                    it.tipo == "ENTRADA" && it.fecha_hora.startsWith(todayIso)
                }
                if (marcoEntradaHoy) {
                    NotificationHelper.markDocenteEntradaRegisteredToday(context)
                }
            } else {
                _eventFlow.emit(DocenteEvent.ShowSnackbar(res.message ?: "Error al cargar historial."))
            }
        }
    }

    /**
     * Escaneo y procesamiento de Código QR de Asistencia
     */
    fun onQrScanned(rawPayload: String) {
        if (_uiState.value.isScanningLocked) return

        _uiState.value = _uiState.value.copy(isScanningLocked = true, scanErrorMessage = null)
        triggerHapticFeedback()

        viewModelScope.launch {
            val res = docenteRepository.marcarAsistenciaQR(rawPayload)
            when (res) {
                is Resource.Success -> {
                    if (res.data?.tipo == "ENTRADA") {
                        NotificationHelper.markDocenteEntradaRegisteredToday(context)
                    }
                    _uiState.value = _uiState.value.copy(
                        lastMarcajeResult = res.data,
                        showResultDialog = true,
                        isScanningLocked = false
                    )
                    res.data?.let { _eventFlow.emit(DocenteEvent.MarcajeSuccess(it)) }
                    loadHistorial()
                }
                is Resource.Error -> {
                    _uiState.value = _uiState.value.copy(
                        scanErrorMessage = res.message ?: "Código QR inválido o expirado.",
                        isScanningLocked = false
                    )
                    _eventFlow.emit(DocenteEvent.ShowSnackbar(res.message ?: "Error al procesar asistencia."))
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isScanningLocked = false)
                }
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.value = _uiState.value.copy(showResultDialog = false, lastMarcajeResult = null)
    }

    /**
     * Abrir el modal opcional de justificación por retardo
     */
    fun openJustificacionDialog(asistenciaId: Int, materia: String, minutosRetraso: Int) {
        _uiState.value = _uiState.value.copy(
            showResultDialog = false,
            lastMarcajeResult = null,
            showJustificacionDialog = true,
            justificacionTargetId = asistenciaId,
            justificacionMateria = materia,
            justificacionMinutos = minutosRetraso
        )
    }

    fun dismissJustificacionDialog() {
        _uiState.value = _uiState.value.copy(
            showJustificacionDialog = false,
            justificacionTargetId = null,
            justificacionMateria = "",
            justificacionMinutos = 0,
            isSubmittingJustificacion = false
        )
    }

    fun submitJustificacionRetardo(motivo: String, photoUri: Uri?) {
        val asistenciaId = _uiState.value.justificacionTargetId ?: return
        if (motivo.isBlank() && photoUri == null) {
            viewModelScope.launch {
                _eventFlow.emit(DocenteEvent.ShowSnackbar("Ingrese el motivo del retardo o adjunte una imagen de respaldo."))
            }
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingJustificacion = true)
            val compressedFile = if (photoUri != null) {
                withContext(Dispatchers.IO) {
                    ImageUtils.compressUriToFile(context, photoUri, "justificativo")
                }
            } else {
                null
            }

            val res = docenteRepository.justificarRetardo(
                asistenciaId = asistenciaId,
                motivo = motivo.trim(),
                evidenciaFile = compressedFile
            )

            _uiState.value = _uiState.value.copy(isSubmittingJustificacion = false)

            when (res) {
                is Resource.Success -> {
                    dismissJustificacionDialog()
                    _eventFlow.emit(
                        DocenteEvent.ShowSnackbar(
                            res.data?.message ?: "Justificativo enviado exitosamente a la Coordinación."
                        )
                    )
                    loadHistorial()
                }
                is Resource.Error -> {
                    _eventFlow.emit(
                        DocenteEvent.ShowSnackbar(
                            res.message ?: "Error al enviar el justificativo."
                        )
                    )
                }
                else -> Unit
            }
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(150)
            }
        } catch (_: Exception) {}
    }

    fun logout() {
        viewModelScope.launch {
            NotificationHelper.setSessionRole(context, "")
            authRepository.logout()
            _eventFlow.emit(DocenteEvent.SessionTerminated)
        }
    }
}
