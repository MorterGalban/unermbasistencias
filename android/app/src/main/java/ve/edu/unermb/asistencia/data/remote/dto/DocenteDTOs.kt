package ve.edu.unermb.asistencia.data.remote.dto

data class MarcajeQRRequest(
    val qr_payload: String
)

data class MarcajeQRResponse(
    val success: Boolean,
    val tipo: String? = null,
    val mensaje: String? = null,
    val requiereJustificacionOpcional: Boolean? = false,
    val puntualidad: PuntualidadDto? = null,
    val registro: AsistenciaRegistroDto? = null,
    val docente: DocenteMinDto? = null,
    val error: String? = null
)

data class PuntualidadDto(
    val estado: String,
    val minutosRetraso: Int = 0,
    val bloqueHorario: String? = null,
    val materiaNombre: String? = null,
    val mensaje: String? = null
)

data class DocenteMinDto(
    val nombre: String,
    val apellido: String,
    val cedula: String
)

data class HistorialResponse(
    val success: Boolean,
    val resumen: ResumenHistorialDto? = null,
    val historial: List<AsistenciaRegistroDto>? = null,
    val error: String? = null
)

data class ResumenHistorialDto(
    val total: Int = 0,
    val entradas: Int = 0,
    val salidas: Int = 0,
    val a_tiempo: Int = 0,
    val tarde: Int = 0,
    val estadoActual: String? = "SIN_MARCAJE",
    val marcajesHoy: Int = 0
)

data class AsistenciaRegistroDto(
    val id: Int,
    val usuario_id: Int? = null,
    val fecha_hora: String,
    val tipo: String, // 'ENTRADA' o 'SALIDA'
    val metodo: String? = "QR_SCANNER",
    val ip_address: String? = null,
    val puntualidad_estado: String? = "A_TIEMPO", // 'A_TIEMPO', 'TARDE', 'FUERA_DE_HORARIO', 'SIN_HORARIO'
    val minutos_retraso: Int? = 0,
    val bloque_horario_detectado: String? = null,
    val materia_detectada: String? = null,
    val justificacion_texto: String? = null,
    val justificacion_imagen_url: String? = null,
    val justificacion_fecha: String? = null,
    val justificacion_estado: String? = "SIN_JUSTIFICAR"
)

data class JustificarRetardoResponse(
    val success: Boolean,
    val message: String? = null,
    val registro: AsistenciaRegistroDto? = null,
    val error: String? = null
)

data class HorarioDocenteResponse(
    val success: Boolean,
    val diaActual: String? = null,
    val clasesHoy: List<HorarioBloqueDto>? = emptyList(),
    val totalBloques: Int = 0,
    val horarios: List<HorarioBloqueDto>? = emptyList(),
    val bloquesHorarios: List<BloqueConfigDto>? = emptyList(),
    val diasSemana: List<String>? = emptyList(),
    val error: String? = null
)

data class HorarioBloqueDto(
    val id: Int,
    val materia_id: Int,
    val dia_semana: String,
    val bloque_numero: Int,
    val bloque_horario: String,
    val hora_inicio: String,
    val hora_fin: String,
    val pnf: String? = null,
    val trayecto: String,
    val trimestre: String,
    val materia_nombre: String,
    val materia_codigo: String? = null
)

data class BloqueConfigDto(
    val numero: Int,
    val etiqueta: String,
    val hora_inicio: String,
    val hora_fin: String
)

