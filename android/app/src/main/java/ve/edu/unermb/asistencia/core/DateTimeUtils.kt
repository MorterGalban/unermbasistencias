package ve.edu.unermb.asistencia.core

import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

    fun nowInVenezuela(): ZonedDateTime {
        return ZonedDateTime.now(Constants.VENEZUELA_ZONE_ID)
    }

    fun todayInVenezuela(): LocalDate {
        return LocalDate.now(Constants.VENEZUELA_ZONE_ID)
    }

    fun todayIsoInVenezuela(): String {
        return todayInVenezuela().format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Retorna el día de la semana actual en español con primera letra mayúscula:
     * "Lunes", "Martes", "Miércoles", "Jueves", "Viernes"
     */
    fun todayDayOfWeekSpanish(): String {
        val dayOfWeek = nowInVenezuela().dayOfWeek
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "Lunes"
            DayOfWeek.TUESDAY -> "Martes"
            DayOfWeek.WEDNESDAY -> "Miércoles"
            DayOfWeek.THURSDAY -> "Jueves"
            DayOfWeek.FRIDAY -> "Viernes"
            DayOfWeek.SATURDAY -> "Sábado"
            DayOfWeek.SUNDAY -> "Domingo"
        }
    }

    fun formatVenezuelaClock(zdt: ZonedDateTime = nowInVenezuela()): String {
        val formatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale("es", "VE"))
        return zdt.format(formatter).uppercase()
    }

    fun formatVenezuelaDate(zdt: ZonedDateTime = nowInVenezuela()): String {
        val formatter = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "VE"))
        val text = zdt.format(formatter)
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "VE")) else it.toString() }
    }

    /**
     * Convierte "HH:mm:ss" o "HH:mm" a minutos desde medianoche
     */
    fun timeToMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return 0
        return try {
            val parts = timeStr.trim().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            h * 60 + m
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Calcula la estampa de tiempo en milisegundos para el recordatorio preventivo exacto
     * (10 minutos antes de horaInicio en hora de Venezuela).
     * Si la hora ya pasó hoy, devuelve null.
     */
    fun calculateReminderEpochMillis(horaInicioStr: String): Long? {
        val venezuelaNow = nowInVenezuela()
        val parts = horaInicioStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null

        val classLocalTime = LocalTime.of(h, m)
        val reminderLocalTime = classLocalTime.minusMinutes(Constants.MINUTOS_RECORDATORIO_PREVIO.toLong())

        val reminderZdt = venezuelaNow.toLocalDate().atTime(reminderLocalTime).atZone(Constants.VENEZUELA_ZONE_ID)

        val targetInstant = reminderZdt.toInstant()
        val nowInstant = venezuelaNow.toInstant()

        return if (targetInstant.isAfter(nowInstant)) {
            targetInstant.toEpochMilli()
        } else {
            null
        }
    }

    /**
     * Calcula la estampa de tiempo en milisegundos para la alerta de retardo
     * (exactamente 10 minutos después de horaInicio en hora de Venezuela).
     * Si la hora ya pasó hoy, devuelve null.
     */
    fun calculateDelayCheckEpochMillis(horaInicioStr: String): Long? {
        val venezuelaNow = nowInVenezuela()
        val parts = horaInicioStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null

        val classLocalTime = LocalTime.of(h, m)
        val delayCheckLocalTime = classLocalTime.plusMinutes(Constants.MINUTOS_ALERTA_RETARDO.toLong())

        val delayCheckZdt = venezuelaNow.toLocalDate().atTime(delayCheckLocalTime).atZone(Constants.VENEZUELA_ZONE_ID)

        val targetInstant = delayCheckZdt.toInstant()
        val nowInstant = venezuelaNow.toInstant()

        return if (targetInstant.isAfter(nowInstant)) {
            targetInstant.toEpochMilli()
        } else {
            null
        }
    }

    /**
     * Formatea fecha ISO o MySQL (YYYY-MM-DD HH:MM:SS) a formato amigable venezolano
     */
    fun formatDisplayDateTime(rawDateStr: String?): String {
        if (rawDateStr.isNullOrBlank()) return "Fecha no disponible"
        return try {
            // Limpiar separadores si vienen en ISO
            val cleaned = rawDateStr.replace("T", " ").replace("Z", "").substringBefore(".")
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val localDt = LocalDateTime.parse(cleaned, formatter)
            val venezuelaZdt = localDt.atZone(ZoneId.of("UTC")).withZoneSameInstant(Constants.VENEZUELA_ZONE_ID)
            val outFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale("es", "VE"))
            venezuelaZdt.format(outFormatter)
        } catch (_: Exception) {
            rawDateStr
        }
    }
}

