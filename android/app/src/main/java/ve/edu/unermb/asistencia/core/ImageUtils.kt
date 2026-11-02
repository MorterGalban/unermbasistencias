package ve.edu.unermb.asistencia.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import ve.edu.unermb.asistencia.ui.theme.UnerbElectricBlue
import ve.edu.unermb.asistencia.ui.theme.UnerbPrimaryNavy
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /**
     * Decodifica un Data URL Base64 ("data:image/png;base64,...") directamente a un ImageBitmap
     * para garantizar el renderizado nativo 100% confiable del Código QR en Jetpack Compose.
     */
    fun decodeBase64DataUrlToBitmap(dataUrl: String?): ImageBitmap? {
        if (dataUrl.isNullOrBlank()) return null
        return try {
            val base64Part = if (dataUrl.contains("base64,")) {
                dataUrl.substringAfter("base64,").trim()
            } else {
                dataUrl.trim()
            }
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bitmap?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normaliza rutas relativas de recursos estáticos ("/uploads/carnet/...", "/uploads/justificativos/...")
     * concatenándolas con el Base URL activo del servidor.
     */
    fun resolveFullMediaUrl(rawUrl: String?, baseUrl: String = Constants.DEFAULT_BASE_URL): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        val cleanBase = baseUrl.trimEnd('/')
        val cleanPath = trimmed.trimStart('/')
        return "$cleanBase/$cleanPath"
    }

    /**
     * Comprime y prepara un archivo de imagen desde un Uri (Galería/Cámara) para subir justificativos o carnets (< 1MB)
     */
    fun compressUriToFile(context: Context, uri: Uri, prefix: String = "evidencia"): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val maxDim = 1280
            val ratio = minOf(
                maxDim.toFloat() / originalBitmap.width.toFloat(),
                maxDim.toFloat() / originalBitmap.height.toFloat(),
                1.0f
            )
            val targetWidth = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetHeight = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
            val tempFile = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { fos ->
                fos.write(outputStream.toByteArray())
            }
            tempFile
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Componente reutilizable de Fotografía de Docente con normalización automática de URL
 * y Avatar por defecto de alto contraste si el archivo no existe o falla la conexión.
 */
@Composable
fun DocenteAvatar(
    fotoUrl: String?,
    nombre: String,
    apellido: String,
    modifier: Modifier = Modifier,
    baseUrl: String = Constants.DEFAULT_BASE_URL,
    isCircle: Boolean = true
) {
    val shape = if (isCircle) CircleShape else RoundedCornerShape(10.dp)
    val fullUrl = remember(fotoUrl, baseUrl) { ImageUtils.resolveFullMediaUrl(fotoUrl, baseUrl) }
    val initials = remember(nombre, apellido) {
        val n = nombre.trim().firstOrNull()?.uppercaseChar()?.toString() ?: ""
        val a = apellido.trim().firstOrNull()?.uppercaseChar()?.toString() ?: ""
        (n + a).ifBlank { "D" }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(UnerbElectricBlue),
        contentAlignment = Alignment.Center
    ) {
        if (!fullUrl.isNullOrBlank()) {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(fullUrl)
                    .crossfade(true)
                    .build()
            )
            val state = painter.state
            if (state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Image(
                    painter = painter,
                    contentDescription = "Foto de $nombre $apellido",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            if (initials.isNotBlank()) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

