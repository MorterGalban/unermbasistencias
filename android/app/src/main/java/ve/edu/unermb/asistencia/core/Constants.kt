package ve.edu.unermb.asistencia.core

import java.time.ZoneId

object Constants {
    // Zona Horaria Legal Estricta de Venezuela (UTC-4)
    val VENEZUELA_ZONE_ID: ZoneId = ZoneId.of("America/Caracas")

    // URLs del Backend
    const val DEFAULT_BASE_URL = "https://unermbasistencias-production.up.railway.app/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:3000/"

    // Notificaciones Emergentes (Heads-Up v2 sin caché de baja prioridad)
    const val CHANNEL_ID_DOCENTE = "unermb_alertas_emergentes_v2"
    const val CHANNEL_ID_SUPERROOT = "unermb_coordinacion_emergentes_v2"
    const val CHANNEL_ID_FOREGROUND_SYNC = "unermb_realtime_daemon_v2"

    // Claves de DataStore de sesión
    const val PREFS_NAME = "unermb_asistencias_prefs"
    const val KEY_JWT_TOKEN = "jwt_token"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NOMBRE = "user_nombre"
    const val KEY_USER_APELLIDO = "user_apellido"
    const val KEY_USER_CEDULA = "user_cedula"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_ROL = "user_rol"
    const val KEY_USER_ESTADO = "user_estado"
    const val KEY_USER_FOTO = "user_foto_url"
    const val KEY_CUSTOM_BASE_URL = "custom_base_url"

    // Roles
    const val ROL_DOCENTE = "DOCENTE"
    const val ROL_SUPERROOT = "SUPERROOT"

    // Estados
    const val ESTADO_ACTIVO = "ACTIVO"
    const val ESTADO_PENDIENTE = "PENDIENTE"
    const val ESTADO_RECHAZADO = "RECHAZADO"
    const val ESTADO_INACTIVO = "INACTIVO"

    // Recordatorio y alerta de clases (Rol DOCENTE: 10 min antes y 10 min después del inicio)
    const val MINUTOS_RECORDATORIO_PREVIO = 10
    const val MINUTOS_ALERTA_RETARDO = 10
    const val TOLERANCIA_A_TIEMPO_MINUTOS = 10
}

