package ve.edu.unermb.asistencia.data.remote.dto

data class LoginRequest(
    val identificador: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null,
    val usuario: UserDto? = null,
    val codigo_estado: String? = null,
    val error: String? = null
)

data class UserDto(
    val id: Int,
    val nombre: String,
    val apellido: String,
    val cedula: String,
    val email: String,
    val foto_carnet_url: String? = null,
    val rol: String,
    val estado: String,
    val materias: List<MateriaDto>? = emptyList()
)

data class MateriaDto(
    val id: Int,
    val codigo: String,
    val nombre_materia: String? = null,
    val nombre: String? = null,
    val trayecto: String? = null,
    val trimestre: String? = null,
    val pnf: String? = null,
    val horas_semanales: Int? = null
) {
    val displayNombre: String
        get() = nombre_materia ?: nombre ?: codigo
}

data class ResetPasswordRequest(
    val cedula: String,
    val tipo_cedula: String,
    val email: String,
    val new_password: String,
    val confirm_password: String
)

data class SimpleResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

data class MeResponse(
    val success: Boolean,
    val usuario: UserDto? = null,
    val error: String? = null
)

data class MateriasCatalogResponse(
    val success: Boolean,
    val materias: List<MateriaDto>? = null,
    val error: String? = null
)

data class PensumEstructuraResponse(
    val success: Boolean,
    val estructura: List<TrayectoDto>? = null,
    val bloquesHorarios: List<BloqueConfigDto>? = null,
    val diasSemana: List<String>? = null,
    val error: String? = null
)

data class TrayectoDto(
    val nombre: String,
    val trimestres: List<TrimestreDto>
)

data class TrimestreDto(
    val nombre: String,
    val materias: List<MateriaItemDto>
)

data class MateriaItemDto(
    val id: Int,
    val codigo: String,
    val nombre: String,
    val pnf: String? = null
)

