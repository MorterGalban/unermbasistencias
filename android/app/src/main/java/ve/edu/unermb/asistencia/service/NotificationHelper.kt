package ve.edu.unermb.asistencia.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import ve.edu.unermb.asistencia.MainActivity
import ve.edu.unermb.asistencia.R
import ve.edu.unermb.asistencia.core.Constants
import ve.edu.unermb.asistencia.core.DateTimeUtils

object NotificationHelper {

    private const val TRACKING_PREFS = "unermb_notification_tracking"
    private const val KEY_LAST_ENTRADA_DATE = "last_entrada_date_ve"
    private const val KEY_ACTIVE_ROLE = "active_session_role"

    private val VIBRATION_PATTERN_HEADS_UP = longArrayOf(0, 600, 150, 600, 150, 800)
    private var cachedCircularLogo: Bitmap? = null

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

    /**
     * Genera y almacena en caché el avatar circular grande (largeIcon) con el logo oficial de la UNERMB
     */
    fun getUnermbCircularLargeIcon(context: Context): Bitmap? {
        if (cachedCircularLogo != null) return cachedCircularLogo
        return try {
            val raw = BitmapFactory.decodeResource(context.resources, R.drawable.ic_unermb_logo) ?: return null
            val size = 192
            val scaled = Bitmap.createScaledBitmap(raw, size, size, true)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, size, size)

            // Fondo circular blanco puro para resaltar el emblema oficial UNERMB
            paint.color = Color.WHITE
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            canvas.drawBitmap(scaled, rect, rect, paint)
            cachedCircularLogo = output
            output
        } catch (_: Exception) {
            null
        }
    }

    /**
     *Amplificación sonora a 0 dB (máxima ganancia en canal de Alarma/Notificación prioritaria) + vibración inmediata
     */
    private fun triggerHighGainAudioAndVibrationBoost(context: Context) {
        try {
            // 1. Vibración inmediata de alta intensidad para forzar despliegue visual y físico
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN_HEADS_UP, amplitudes, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(VIBRATION_PATTERN_HEADS_UP, -1)
                }
            }
        } catch (_: Exception) {}

        try {
            // 2. Pulso acústico normalizado a 0 dB (MAX_VOLUME = 100) sobre STREAM_ALARM
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
        } catch (_: Exception) {}
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Purgar canales antiguos en caché para evitar bloqueo de Heads-Up por configuraciones previas
            try {
                notificationManager.deleteNotificationChannel("unermb_canal_docente")
                notificationManager.deleteNotificationChannel("unermb_canal_superroot")
            } catch (_: Exception) {}

            val alarmSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val highGainAudioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                .build()

            // Canal 1 (v2): Alertas Emergentes Heads-Up de Clases (Exclusivo Rol DOCENTE)
            val docenteChannel = NotificationChannel(
                Constants.CHANNEL_ID_DOCENTE,
                context.getString(R.string.channel_docente_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_docente_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_HEADS_UP
                setBypassDnd(true)
                setShowBadge(true)
                setSound(alarmSoundUri, highGainAudioAttributes)
            }

            // Canal 2 (v2): Alertas Emergentes Heads-Up de Coordinación (Exclusivo Rol SUPERROOT)
            val superrootChannel = NotificationChannel(
                Constants.CHANNEL_ID_SUPERROOT,
                context.getString(R.string.channel_superroot_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_superroot_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN_HEADS_UP
                setBypassDnd(true)
                setShowBadge(true)
                setSound(alarmSoundUri, highGainAudioAttributes)
            }

            // Canal 3: Demonio Silencioso de Sincronización en Tiempo Real 24/7
            val syncDaemonChannel = NotificationChannel(
                Constants.CHANNEL_ID_FOREGROUND_SYNC,
                "Sincronización en Tiempo Real UNERMB",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Mantiene activa la recepción instantánea de notificaciones desde Railway."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            notificationManager.createNotificationChannel(docenteChannel)
            notificationManager.createNotificationChannel(superrootChannel)
            notificationManager.createNotificationChannel(syncDaemonChannel)
        }
    }

    /**
     * Constructor centralizado de Banner Flotante Superior (Heads-Up Notification)
     * con estilo de tarjeta desplegada de mensajería (MessagingStyle + BigTextStyle),
     * logo oficial de la UNERMB en smallIcon y largeIcon circular, hora actual y botón "Marcar como leído".
     */
    private fun dispatchHeadsUpBanner(
        context: Context,
        channelId: String,
        notificationId: Int,
        senderTitle: String,
        shortText: String,
        expandedBody: String,
        accentColor: Int = Color.parseColor("#002147")
    ) {
        createNotificationChannels(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 70000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_AS_READ
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 90000,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val largeLogoBitmap = getUnermbCircularLargeIcon(context)
        val personBuilder = Person.Builder()
            .setName(senderTitle)
            .setImportant(true)
        if (largeLogoBitmap != null) {
            personBuilder.setIcon(IconCompat.createWithBitmap(largeLogoBitmap))
        }
        val senderPerson = personBuilder.build()

        val nowMillis = System.currentTimeMillis()
        val messagingStyle = NotificationCompat.MessagingStyle(senderPerson)
            .setConversationTitle("UNERMB • Control de Asistencia")
            .addMessage(expandedBody, nowMillis, senderPerson)

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_unermb)
            .setLargeIcon(largeLogoBitmap)
            .setContentTitle(senderTitle)
            .setContentText(shortText)
            .setSubText("UNERMB PNF Informática")
            .setStyle(messagingStyle)
            .setWhen(nowMillis)
            .setShowWhen(true)
            .setColor(accentColor)
            .setColorized(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri, AudioManager.STREAM_ALARM)
            .setVibrate(VIBRATION_PATTERN_HEADS_UP)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(
                R.drawable.ic_stat_unermb,
                "Marcar como leído",
                markReadPendingIntent
            )

        triggerHighGainAudioAndVibrationBoost(context)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {}
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

        val notifId = (materiaNombre + "_prev10").hashCode()
        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_DOCENTE,
            notificationId = notifId,
            senderTitle = "Próxima Clase en 10 Minutos",
            shortText = "Tu clase de $materiaNombre inicia a las $horaInicio ($bloqueHorario).",
            expandedBody = "Recordatorio preventivo UNERMB: Tu clase de '$materiaNombre' comenzará en 10 minutos ($bloqueHorario). Recuerda escanear el Código QR Oficial del día al llegar.",
            accentColor = Color.parseColor("#0266CC")
        )
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

        val notifId = (materiaNombre + "_delay10").hashCode()
        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_DOCENTE,
            notificationId = notifId,
            senderTitle = "Alerta de Retardo (+$minutosRetraso min)",
            shortText = "No has registrado asistencia para $materiaNombre.",
            expandedBody = "Han transcurrido $minutosRetraso minutos desde el inicio del bloque $bloqueHorario para '$materiaNombre' y aún no registras entrada. Te encuentras en estado de RETARDO. Escanea el QR Oficial y adjunta justificativo si corresponde.",
            accentColor = Color.RED
        )
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

        val notifId = ("sol_$cedula").hashCode()
        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_SUPERROOT,
            notificationId = notifId,
            senderTitle = "Nueva Solicitud Docente",
            shortText = "$docenteNombre (C.I. $cedula) solicita aprobación.",
            expandedBody = "El docente $docenteNombre (C.I. $cedula) ha enviado una solicitud de registro y se encuentra PENDIENTE de revisión en el Panel de Coordinación PNF Informática.",
            accentColor = Color.parseColor("#0266CC")
        )
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

        val notifId = ("marcaje_${docenteNombre}_${tipo}_${System.currentTimeMillis() / 1000}").hashCode()
        val statusLabel = if (puntualidad == "A_TIEMPO") "A TIEMPO" else "TARDE (+$minutosRetraso min)"
        val title = if (puntualidad == "TARDE") "Marcaje con Retardo Detectado" else "Nuevo Marcaje de $tipo"

        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_SUPERROOT,
            notificationId = notifId,
            senderTitle = title,
            shortText = "$docenteNombre marcó $tipo ($materia) • $statusLabel",
            expandedBody = "$docenteNombre ha registrado su $tipo para '$materia'. Estatus de Puntualidad: $statusLabel.",
            accentColor = if (puntualidad == "TARDE") Color.parseColor("#D97706") else Color.parseColor("#059669")
        )
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

        val notifId = ("inasist_${docenteNombre}_$materia").hashCode()
        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_SUPERROOT,
            notificationId = notifId,
            senderTitle = "Alerta de Inasistencia / Retardo",
            shortText = "$docenteNombre sin entrada en $materia (+$minutosTranscurridos min).",
            expandedBody = "El bloque $bloqueHorario de '$materia' asignado a $docenteNombre inició hace $minutosTranscurridos minutos y aún no registra marcaje de entrada en sede.",
            accentColor = Color.RED
        )
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

        val notifId = ("justif_${docenteNombre}_$materia").hashCode()
        dispatchHeadsUpBanner(
            context = context,
            channelId = Constants.CHANNEL_ID_SUPERROOT,
            notificationId = notifId,
            senderTitle = "Justificativo de Retardo Recibido",
            shortText = "$docenteNombre adjuntó justificativo para $materia.",
            expandedBody = "El docente $docenteNombre envió un justificativo por retardo en '$materia': \"$motivo\". Revíselo en la pestaña de Auditoría.",
            accentColor = Color.parseColor("#0266CC")
        )
    }
}
