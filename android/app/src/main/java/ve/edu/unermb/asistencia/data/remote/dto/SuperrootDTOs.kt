package ve.edu.unermb.asistencia.data.remote.dto

data class DashboardStatsResponse(
    val success: Boolean,
    val stats: StatsDto? = null,
    val error: String? = null
)

data class StatsDto(
    val totalDocentes: Int = 0,
    val pendientes: Int = 0,
    val activos: Int = 0,
    val horariosAsignados: Int = 0,
    val marcajesHoy: MarcajesHoyDto = MarcajesHoyDto()
)

data class MarcajesHoyDto(
    val total: Int = 0,
    val entradas: Int = 0,
    val salidas: Int = 0,
    val a_tiempo: Int = 0,
    val tarde: Int = 0
)

data class SolicitudesResponse(
    val success: Boolean,
    val solicitudes: List<SolicitudDocenteDto>? = emptyList(),
    val error: String? = null
)

data class SolicitudDocenteDto(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val email: String,
    val foto_carnet_url: String? = null,
    val estado: String,
    val created_at: String? = null,
    val materias: List<MateriaDto>? = emptyList()
)

data class DocentesListResponse(
    val success: Boolean,
    val docentes: List<UserDto>? = emptyList(),
    val total: Int = 0,
    val error: String? = null
)

data class DocenteHorarioResponse(
    val success: Boolean,
    val docente: UserDto? = null,
    val materiasGenerales: List<MateriaDto>? = emptyList(),
    val catalogoMaterias: List<MateriaDto>? = emptyList(),
    val estructuraPensum: List<TrayectoDto>? = emptyList(),
    val horarios: List<HorarioBloqueDto>? = emptyList(),
    val bloquesHorariosDisponibles: List<BloqueConfigDto>? = emptyList(),
    val diasSemanaDisponibles: List<String>? = emptyList(),
    val error: String? = null
)

data class AsignarHorarioRequest(
    val materia_id: Int,
    val dia_semana: String,
    val bloque_numero: Int
)

data class AsignarMateriaRequest(
    val materia_id: Int
)

data class QRTokenResponse(
    val success: Boolean,
    val qrDataUrl: String? = null,
    val qrDataURL: String? = null,
    val token: String? = null,
    val fecha_vigente: String? = null,
    val valido_desde: String? = null,
    val valido_hasta: String? = null,
    val qrPayload: String? = null,
    val info: QRInfoMinDto? = null,
    val error: String? = null
)

data class QRInfoMinDto(
    val institucion: String? = null,
    val programa: String? = null,
    val ubicacion: String? = null,
    val fecha_vigente: String? = null,
    val valido_desde: String? = null,
    val valido_hasta: String? = null
)

data class AsistenciasLogsResponse(
    val success: Boolean,
    val total: Int = 0,
    val asistencias: List<AsistenciaLogItemDto>? = emptyList(),
    val logs: List<AsistenciaLogItemDto>? = emptyList(),
    val error: String? = null
) {
    val allItems: List<AsistenciaLogItemDto>
        get() = if (!asistencias.isNullOrEmpty()) asistencias else (logs ?: emptyList())
}

data class AsistenciaLogItemDto(
    val id: Int,
    val usuario_id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val email: String? = null,
    val foto_carnet_url: String? = null,
    val fecha_hora: String,
    val tipo: String,
    val metodo: String? = "QR_SCANNER",
    val ip_address: String? = null,
    val puntualidad_estado: String? = null,
    val minutos_retraso: Int? = 0,
    val bloque_horario_detectado: String? = null,
    val materia_detectada: String? = null,
    val justificacion_texto: String? = null,
    val justificacion_imagen_url: String? = null,
    val justificacion_fecha: String? = null,
    val justificacion_estado: String? = "SIN_JUSTIFICAR"
)

data class NotificacionesEventosResponse(
    val success: Boolean,
    val fecha_vigente: String? = null,
    val solicitudesPendientes: List<SolicitudDocenteDto>? = emptyList(),
    val marcajesRecientes: List<AsistenciaLogItemDto>? = emptyList(),
    val justificacionesRecientes: List<AsistenciaLogItemDto>? = emptyList(),
    val alertasInasistencia: List<AlertaInasistenciaDto>? = emptyList(),
    val error: String? = null
)

data class AlertaInasistenciaDto(
    val horario_id: Int,
    val docente_id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val materia_nombre: String,
    val bloque_horario: String,
    val minutos_transcurridos: Int,
    val fecha: String
)

