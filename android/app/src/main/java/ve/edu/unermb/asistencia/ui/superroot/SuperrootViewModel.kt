package ve.edu.unermb.asistencia.ui.superroot

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.core.Resource
import ve.edu.unermb.asistencia.data.local.DataStoreManager
import ve.edu.unermb.asistencia.data.remote.dto.*
import ve.edu.unermb.asistencia.data.repository.AuthRepository
import ve.edu.unermb.asistencia.data.repository.SuperrootRepository
import ve.edu.unermb.asistencia.service.NotificationHelper
import javax.inject.Inject

sealed class SuperrootEvent {
    data class ShowSnackbar(val message: String) : SuperrootEvent()
    object SessionTerminated : SuperrootEvent()
}

data class SuperrootUiState(
    val activeTab: Int = 0, // 0: Métricas, 1: Código QR Diario, 2: Solicitudes, 3: Horarios, 4: Auditoría
    val baseUrl: String = Constants.DEFAULT_BASE_URL,
    val stats: StatsDto? = null,
    val isLoadingStats: Boolean = false,
    val solicitudes: List<SolicitudDocenteDto> = emptyList(),
    val isLoadingSolicitudes: Boolean = false,
    val docentes: List<UserDto> = emptyList(),
    val isLoadingDocentes: Boolean = false,
    val qrTokenData: QRTokenResponse? = null,
    val isLoadingQR: Boolean = false,
    val asistenciasLogs: List<AsistenciaLogItemDto> = emptyList(),
    val isLoadingLogs: Boolean = false,
    val estructuraPensum: List<TrayectoDto> = emptyList(),
    val catalogoMaterias: List<MateriaDto> = emptyList(),
    // Filtros de Auditoría
    val filtroFecha: String = "",
    val filtroTipo: String = "",
    val filtroPuntualidad: String = "",
    // Modal de asignación por cuadrícula interactiva
    val selectedDocenteForHorario: UserDto? = null,
    val selectedDocenteHorarioData: DocenteHorarioResponse? = null,
    val isLoadingDocenteHorario: Boolean = false
)

@HiltViewModel
class SuperrootViewModel @Inject constructor(
    private val superrootRepository: SuperrootRepository,
    private val authRepository: AuthRepository,
    private val dataStoreManager: DataStoreManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SuperrootUiState())
    val uiState = _uiState.asStateFlow()

    private val _eventFlow = MutableSharedFlow<SuperrootEvent>()
    val eventFlow = _eventFlow.asSharedFlow()

    init {
        NotificationHelper.setSessionRole(context, Constants.ROL_SUPERROOT)
        _uiState.value = _uiState.value.copy(baseUrl = dataStoreManager.getCachedBaseUrl())
        loadPensumCatalog()
        loadDashboardStats()
        loadSolicitudesPendientes()
        loadDocentes()
        loadQR()
        loadAsistenciasLogs()
        startDailyQrAndNotificationLoop()
    }

    /**
     * Ciclo desatendido que:
     * 1. Verifica cambio de día en Venezuela (00:00:00 HLV) para rotar automáticamente el Código QR diario.
     * 2. Consulta el feed de eventos del Coordinador (solicitudes, marcajes, retardos/inasistencias y justificativos).
     */
    private fun startDailyQrAndNotificationLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(25_000)
                val todayVe = DateTimeUtils.todayIsoInVenezuela()
                val currentQrDate = _uiState.value.qrTokenData?.fecha_vigente
                if (!currentQrDate.isNullOrBlank() && currentQrDate != todayVe) {
                    loadQR()
                }
                pollCoordinatorNotifications()
            }
        }
    }

    private suspend fun pollCoordinatorNotifications() {
        val res = superrootRepository.getNotificacionesEventos()
        if (res is Resource.Success && res.data != null) {
            val data = res.data

            // 1. Solicitudes pendientes de aprobación
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

            // 3. Alertas por inasistencia o retardo activo hoy
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
    }

    private fun loadPensumCatalog() {
        viewModelScope.launch {
            val estructuraRes = authRepository.getPensumEstructura()
            if (estructuraRes is Resource.Success && !estructuraRes.data?.estructura.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(estructuraPensum = estructuraRes.data?.estructura.orEmpty())
            }
            val materiasRes = authRepository.getMateriasCatalog()
            if (materiasRes is Resource.Success && !materiasRes.data?.materias.isNullOrEmpty()) {
                _uiState.value = _uiState.value.copy(catalogoMaterias = materiasRes.data?.materias.orEmpty())
            }
        }
    }

    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(activeTab = index)
        when (index) {
            0 -> loadDashboardStats()
            1 -> loadQR()
            2 -> loadSolicitudesPendientes()
            3 -> loadDocentes()
            4 -> loadAsistenciasLogs()
        }
    }

    fun loadDashboardStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingStats = true)
            val res = superrootRepository.getDashboardStats()
            _uiState.value = _uiState.value.copy(isLoadingStats = false)
            if (res is Resource.Success && res.data?.stats != null) {
                _uiState.value = _uiState.value.copy(stats = res.data.stats)
            }
        }
    }

    fun loadSolicitudesPendientes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSolicitudes = true)
            val res = superrootRepository.getSolicitudesPendientes()
            _uiState.value = _uiState.value.copy(isLoadingSolicitudes = false)
            if (res is Resource.Success && res.data?.solicitudes != null) {
                _uiState.value = _uiState.value.copy(solicitudes = res.data.solicitudes)
            }
        }
    }

    fun aprobarSolicitud(docenteId: Int) {
        viewModelScope.launch {
            val res = superrootRepository.aprobarSolicitud(docenteId)
            if (res is Resource.Success) {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.data?.message ?: "Solicitud Aprobada."))
                loadSolicitudesPendientes()
                loadDashboardStats()
                loadDocentes()
            } else {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.message ?: "Error al aprobar solicitud."))
            }
        }
    }

    fun rechazarSolicitud(docenteId: Int) {
        viewModelScope.launch {
            val res = superrootRepository.rechazarSolicitud(docenteId)
            if (res is Resource.Success) {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.data?.message ?: "Solicitud Rechazada."))
                loadSolicitudesPendientes()
                loadDashboardStats()
            } else {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.message ?: "Error al rechazar solicitud."))
            }
        }
    }

    fun loadDocentes() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDocentes = true)
            val res = superrootRepository.getDocentes()
            _uiState.value = _uiState.value.copy(isLoadingDocentes = false)
            if (res is Resource.Success && res.data?.docentes != null) {
                _uiState.value = _uiState.value.copy(docentes = res.data.docentes)
            }
        }
    }

    fun loadQR() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingQR = true)
            val res = superrootRepository.generarQROficial()
            _uiState.value = _uiState.value.copy(isLoadingQR = false)
            if (res is Resource.Success && res.data != null) {
                _uiState.value = _uiState.value.copy(qrTokenData = res.data)
            }
        }
    }

    fun loadAsistenciasLogs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingLogs = true)
            val s = _uiState.value
            val res = superrootRepository.getAsistenciasLogs(
                fecha = s.filtroFecha.ifBlank { null },
                tipo = s.filtroTipo.ifBlank { null },
                puntualidad = s.filtroPuntualidad.ifBlank { null }
            )
            _uiState.value = _uiState.value.copy(isLoadingLogs = false)
            if (res is Resource.Success && res.data != null) {
                _uiState.value = _uiState.value.copy(asistenciasLogs = res.data.allItems)
            }
        }
    }

    fun setFiltros(fecha: String, tipo: String, puntualidad: String) {
        _uiState.value = _uiState.value.copy(
            filtroFecha = fecha,
            filtroTipo = tipo,
            filtroPuntualidad = puntualidad
        )
        loadAsistenciasLogs()
    }

    fun selectDocenteForHorario(docente: UserDto?) {
        _uiState.value = _uiState.value.copy(selectedDocenteForHorario = docente)
        if (docente != null) {
            loadDocenteHorario(docente.id)
        }
    }

    fun loadDocenteHorario(docenteId: Int) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingDocenteHorario = true)
            val res = superrootRepository.getDocenteHorario(docenteId)
            _uiState.value = _uiState.value.copy(isLoadingDocenteHorario = false)
            if (res is Resource.Success && res.data != null) {
                val data = res.data
                _uiState.value = _uiState.value.copy(
                    selectedDocenteHorarioData = data,
                    estructuraPensum = if (!data.estructuraPensum.isNullOrEmpty()) data.estructuraPensum else _uiState.value.estructuraPensum,
                    catalogoMaterias = if (!data.catalogoMaterias.isNullOrEmpty()) data.catalogoMaterias else _uiState.value.catalogoMaterias
                )
            }
        }
    }

    fun asignarHorario(materiaId: Int, diaSemana: String, bloqueNumero: Int) {
        val doc = _uiState.value.selectedDocenteForHorario ?: return
        viewModelScope.launch {
            val res = superrootRepository.asignarDocenteHorario(doc.id, materiaId, diaSemana, bloqueNumero)
            if (res is Resource.Success) {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.data?.message ?: "Horario asignado."))
                loadDocenteHorario(doc.id)
                loadDashboardStats()
                loadDocentes()
            } else {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.message ?: "Error al asignar horario."))
            }
        }
    }

    fun eliminarHorario(horarioId: Int) {
        val doc = _uiState.value.selectedDocenteForHorario ?: return
        viewModelScope.launch {
            val res = superrootRepository.eliminarDocenteHorario(horarioId)
            if (res is Resource.Success) {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.data?.message ?: "Bloque removido."))
                loadDocenteHorario(doc.id)
                loadDashboardStats()
                loadDocentes()
            } else {
                _eventFlow.emit(SuperrootEvent.ShowSnackbar(res.message ?: "Error al remover bloque."))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            NotificationHelper.setSessionRole(context, "")
            authRepository.logout()
            _eventFlow.emit(SuperrootEvent.SessionTerminated)
        }
    }
}
