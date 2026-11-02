package ve.edu.unermb.asistencia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ve.edu.unermb.asistencia.service.NotificationHelper
import ve.edu.unermb.asistencia.service.RealtimeNotificationService
import ve.edu.unermb.asistencia.ui.navigation.AppNavGraph
import ve.edu.unermb.asistencia.ui.theme.UnermbTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        RealtimeNotificationService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannels(this)

        // Solicitar permiso de Notificaciones en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Iniciar motor persistente de notificaciones en tiempo real (Heads-Up + Alarmas Exactas)
        RealtimeNotificationService.start(this)

        setContent {
            UnermbTheme {
                AppNavGraph()
            }
        }
    }
}

