package ve.edu.unermb.asistencia.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = UnerbPrimaryNavy,
    onPrimary = UnerbSurface,
    primaryContainer = UnerbElectricBlue,
    onPrimaryContainer = UnerbSurface,
    secondary = UnerbElectricBlue,
    onSecondary = UnerbSurface,
    secondaryContainer = UnerbPrimaryNavy,
    onSecondaryContainer = UnerbSurface,
    tertiary = UnerbSky,
    onTertiary = UnerbPrimaryNavy,
    background = UnerbBackground,
    onBackground = TextPrimary,
    surface = UnerbSurface,
    onSurface = TextPrimary,
    surfaceVariant = UnerbSurfaceVariant,
    onSurfaceVariant = TextPrimary,
    outline = BorderColor,
    error = StatusRed,
    onError = UnerbSurface
)

@Composable
fun UnermbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme // Siempre paleta institucional limpia
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = UnerbPrimaryNavy.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

