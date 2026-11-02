package ve.edu.unermb.asistencia.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*
import ve.edu.unermb.asistencia.data.remote.dto.*

interface ApiService {

    // =========================================================================
    // AUTENTICACIÓN Y CATÁLOGOS PÚBLICOS
    // =========================================================================

    @GET("api/auth/materias")
    suspend fun getMateriasCatalog(): Response<MateriasCatalogResponse>

    @GET("api/auth/pensum-estructura")
    suspend fun getPensumEstructura(): Response<PensumEstructuraResponse>

    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    @Multipart
    @POST("api/auth/registro")
    suspend fun registerDocente(
        @Part("nombre") nombre: RequestBody,
        @Part("apellido") apellido: RequestBody,
        @Part("tipo_cedula") tipoCedula: RequestBody,
        @Part("cedula") cedula: RequestBody,
        @Part("email") email: RequestBody,
        @Part("password") password: RequestBody,
        @Part("confirm_password") confirmPassword: RequestBody,
        @Part("materias") materias: RequestBody,
        @Part fotoCarnet: MultipartBody.Part
    ): Response<SimpleResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<SimpleResponse>

    @GET("api/auth/me")
    suspend fun getMe(): Response<MeResponse>

    // =========================================================================
    // ENDPOINTS ROL DOCENTE
    // =========================================================================

    @GET("api/docente/mi-horario")
    suspend fun getMiHorario(): Response<HorarioDocenteResponse>

    @GET("api/docente/mis-materias")
    suspend fun getMisMaterias(): Response<MateriasCatalogResponse>

    @GET("api/docente/historial")
    suspend fun getHistorialDocente(): Response<HistorialResponse>

    @POST("api/docente/marcar-asistencia")
    suspend fun registrarMarcajeQR(
        @Body request: MarcajeQRRequest
    ): Response<MarcajeQRResponse>

    @Multipart
    @POST("api/docente/asistencias/{id}/justificar")
    suspend fun justificarAsistencia(
        @Path("id") asistenciaId: Int,
        @Part("motivo") motivo: RequestBody,
        @Part evidencia: MultipartBody.Part? = null
    ): Response<JustificarRetardoResponse>

    // =========================================================================
    // ENDPOINTS ROL SUPERROOT (ADMINISTRADOR / COORDINACIÓN)
    // =========================================================================

    @GET("api/superroot/dashboard-stats")
    suspend fun getDashboardStats(): Response<DashboardStatsResponse>

    @GET("api/superroot/solicitudes")
    suspend fun getSolicitudesPendientes(): Response<SolicitudesResponse>

    @PATCH("api/superroot/solicitudes/{id}/aprobar")
    suspend fun aprobarSolicitud(
        @Path("id") docenteId: Int
    ): Response<SimpleResponse>

    @PATCH("api/superroot/solicitudes/{id}/rechazar")
    suspend fun rechazarSolicitud(
        @Path("id") docenteId: Int
    ): Response<SimpleResponse>

    @GET("api/superroot/docentes")
    suspend fun getDocentes(): Response<DocentesListResponse>

    @GET("api/superroot/docentes/{id}/horario")
    suspend fun getDocenteHorario(
        @Path("id") docenteId: Int
    ): Response<DocenteHorarioResponse>

    @POST("api/superroot/docentes/{id}/horario")
    suspend fun asignarDocenteHorario(
        @Path("id") docenteId: Int,
        @Body request: AsignarHorarioRequest
    ): Response<SimpleResponse>

    @DELETE("api/superroot/horarios/{horario_id}")
    suspend fun eliminarDocenteHorario(
        @Path("horario_id") horarioId: Int
    ): Response<SimpleResponse>

    @POST("api/superroot/docentes/{id}/materias")
    suspend fun asignarMateriaDocente(
        @Path("id") docenteId: Int,
        @Body request: AsignarMateriaRequest
    ): Response<SimpleResponse>

    @DELETE("api/superroot/docentes/{id}/materias/{materia_id}")
    suspend fun eliminarMateriaDocente(
        @Path("id") docenteId: Int,
        @Path("materia_id") materiaId: Int
    ): Response<SimpleResponse>

    @GET("api/superroot/generar-qr")
    suspend fun generarQROficial(): Response<QRTokenResponse>

    @POST("api/superroot/renovar-qr")
    suspend fun renovarQROficial(): Response<QRTokenResponse>

    @GET("api/superroot/asistencias")
    suspend fun getAsistenciasLogs(
        @Query("fecha") fecha: String? = null,
        @Query("docente_id") docenteId: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("puntualidad") puntualidad: String? = null
    ): Response<AsistenciasLogsResponse>

    @GET("api/superroot/notificaciones-eventos")
    suspend fun getNotificacionesEventos(): Response<NotificacionesEventosResponse>
}

