package ve.edu.unermb.asistencia.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ve.edu.unermb.asistencia.MainActivity
import ve.edu.unermb.asistencia.R
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils

object NotificationHelper {

    private const val TRACKING_PREFS = "unermb_notification_tracking"
    private const val KEY_LAST_ENTRADA_DATE = "last_entrada_date_ve"
    private const val KEY_ACTIVE_ROLE = "active_session_role"

    fun setSessionRole(context: Context, role: String?) {
        context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_ROLE, role ?: "")
            .apply()
    }

    fun getSessionRole(context: Context): String {
        return context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_ROLE, "") ?: ""
    }

    fun markDocenteEntradaRegisteredToday(context: Context) {
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ENTRADA_DATE, todayIso)
            .apply()
    }

    fun hasDocenteRegisteredEntradaToday(context: Context): Boolean {
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        val saved = context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ENTRADA_DATE, "")
        return saved == todayIso
    }

    fun wasEventNotifiedToday(context: Context, eventKey: String): Boolean {
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        val prefs = context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("notified_$eventKey", "") == todayIso
    }

    fun markEventNotifiedToday(context: Context, eventKey: String) {
        val todayIso = DateTimeUtils.todayIsoInVenezuela()
        context.getSharedPreferences(TRACKING_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("notified_$eventKey", todayIso)
            .apply()
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Canal 1: Avisos Temporizados de Clases (Exclusivo Rol DOCENTE)
            val docenteChannel = NotificationChannel(
                Constants.CHANNEL_ID_DOCENTE,
                context.getString(R.string.channel_docente_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_docente_desc)
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }

            // Canal 2: Eventos Operativos y Administrativos (Exclusivo Rol SUPERROOT)
            val superrootChannel = NotificationChannel(
                Constants.CHANNEL_ID_SUPERROOT,
                context.getString(R.string.channel_superroot_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_superroot_desc)
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
            }

            notificationManager.createNotificationChannel(docenteChannel)
            notificationManager.createNotificationChannel(superrootChannel)
        }
    }

    // =========================================================================
    // NOTIFICACIONES EXCLUSIVAS ROL DOCENTE (2 Eventos Temporizados)
    // =========================================================================

    /**
     * Evento 1 Docente: Notificación preventiva enviada exactamente 10 minutos antes del inicio de su próxima clase
     */
    fun showDocenteClassReminder(
        context: Context,
        materiaNombre: String,
        bloqueHorario: String,
        horaInicio: String
    ) {
        if (getSessionRole(context) == Constants.ROL_SUPERROOT) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (materiaNombre + "_prev10").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_DOCENTE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Próxima Clase en 10 Minutos - UNERMB")
            .setContentText("Tu clase de $materiaNombre inicia a las $horaInicio ($bloqueHorario).")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Recordatorio preventivo: Tu clase de '$materiaNombre' comenzará en exactamente 10 minutos ($bloqueHorario).\nRecuerda escanear el Código QR Oficial del día al llegar.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify((materiaNombre + "_prev10").hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * Evento 2 Docente: Notificación de alerta enviada exactamente 10 minutos después de la hora de inicio
     * si aún no ha registrado su asistencia, advirtiéndole que se encuentra en estado de retardo.
     */
    fun showDocenteRetrasoAlert(
        context: Context,
        materiaNombre: String,
        bloqueHorario: String,
        minutosRetraso: Int = 10
    ) {
        if (getSessionRole(context) == Constants.ROL_SUPERROOT) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (materiaNombre + "_delay10").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_DOCENTE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Alerta de Retardo (+10 min) - UNERMB")
            .setContentText("No has registrado asistencia para $materiaNombre. Te encuentras en estado de retardo.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Han transcurrido $minutosRetraso minutos desde el inicio del bloque $bloqueHorario para '$materiaNombre' y aún no has registrado tu entrada.\nTe encuentras en estado de RETARDO. Escanea el Código QR Oficial y adjunta un justificativo si corresponde.")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setColor(Color.RED)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify((materiaNombre + "_delay10").hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }

    // =========================================================================
    // NOTIFICACIONES EXCLUSIVAS ROL SUPERROOT (Coordinador)
    // =========================================================================

    /**
     * 1. Solicitudes de registro de nuevos usuarios
     */
    fun showSuperrootSolicitudAlert(
        context: Context,
        docenteNombre: String,
        cedula: String
    ) {
        if (getSessionRole(context) == Constants.ROL_DOCENTE) return

        val intent = Intent(context, MainActivity::class.java)
        val notifId = ("sol_$cedula").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_SUPERROOT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nueva Solicitud de Registro Docente")
            .setContentText("$docenteNombre (C.I. $cedula) solicita aprobación en el PNF Informática.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El docente $docenteNombre (C.I. $cedula) ha enviado una solicitud de registro y se encuentra PENDIENTE de revisión en el Panel de Coordinación.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * 2. Registros de marcaje de entrada y salida (incluyendo alertas de retardo)
     */
    fun showSuperrootMarcajeAlert(
        context: Context,
        docenteNombre: String,
        tipo: String,
        materia: String,
        puntualidad: String,
        minutosRetraso: Int
    ) {
        if (getSessionRole(context) == Constants.ROL_DOCENTE) return

        val intent = Intent(context, MainActivity::class.java)
        val notifId = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLabel = if (puntualidad == "A_TIEMPO") "A TIEMPO" else "TARDE (+$minutosRetraso min)"
        val title = if (puntualidad == "TARDE") "Alerta de Marcaje con Retardo - UNERMB" else "Nuevo Marcaje de $tipo - UNERMB"

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_SUPERROOT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$docenteNombre marcó $tipo ($materia) • Estatus: $statusLabel")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$docenteNombre ha registrado $tipo para '$materia'.\nEstatus de Puntualidad: $statusLabel.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * 3. Alertas por inasistencia o retardo cuando el docente no ha marcado pasados 10 min de su bloque
     */
    fun showSuperrootInasistenciaAlert(
        context: Context,
        docenteNombre: String,
        materia: String,
        bloqueHorario: String,
        minutosTranscurridos: Int
    ) {
        if (getSessionRole(context) == Constants.ROL_DOCENTE) return

        val intent = Intent(context, MainActivity::class.java)
        val notifId = ("inasist_${docenteNombre}_$materia").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_SUPERROOT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Alerta de Inasistencia / Retardo Docente")
            .setContentText("$docenteNombre no ha registrado entrada en $materia (+$minutosTranscurridos min).")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El bloque $bloqueHorario de '$materia' asignado a $docenteNombre inició hace $minutosTranscurridos minutos y aún no registra marcaje de entrada.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(Color.RED)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * 4. Recepción de justificativos enviados por los docentes
     */
    fun showSuperrootJustificativoAlert(
        context: Context,
        docenteNombre: String,
        materia: String,
        motivo: String
    ) {
        if (getSessionRole(context) == Constants.ROL_DOCENTE) return

        val intent = Intent(context, MainActivity::class.java)
        val notifId = ("justif_${docenteNombre}_${System.currentTimeMillis()}").hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, Constants.CHANNEL_ID_SUPERROOT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Justificativo de Retardo Recibido")
            .setContentText("$docenteNombre adjuntó un justificativo para $materia.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("El docente $docenteNombre envió un justificativo por retardo en '$materia':\n\"$motivo\"\nRevíselo en la pestaña de Auditoría.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: SecurityException) {}
    }
}
