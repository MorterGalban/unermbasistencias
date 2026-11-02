package ve.edu.unermb.asistencia.ui.docente

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ve.edu.unermb.asistencia.data.remote.dto.MarcajeQRResponse
import ve.edu.unermb.asistencia.ui.theme.*
import java.util.concurrent.Executors

@Composable
fun QRScannerTab(
    isScanningLocked: Boolean,
    lastResult: MarcajeQRResponse?,
    showResultDialog: Boolean,
    errorMessage: String?,
    onQrScanned: (String) -> Unit,
    onDismissDialog: () -> Unit,
    onRequestJustificacion: (asistenciaId: Int, materia: String, minutosRetraso: Int) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewWithMLKit(
                isLocked = isScanningLocked,
                onBarcodeDetected = onQrScanned
            )

            // Overlay con mira/marco visual institucional UNERMB
            ScannerOverlay(
                modifier = Modifier.fillMaxSize()
            )

            // Indicador flotante superior
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp, start = 20.dp, end = 20.dp),
                shape = RoundedCornerShape(24.dp),
                color = UnerbPrimaryNavy.copy(alpha = 0.9f),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = UnerbSky,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Apunte al Código QR del punto de control",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Mensaje de error inferior si hubo fallo
            if (errorMessage != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = StatusRed.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        } else {
            // Pantalla de solicitud de permiso de cámara
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = UnerbElectricBlue,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Permiso de Cámara Requerido",
                    style = MaterialTheme.typography.titleLarge,
                    color = UnerbPrimaryNavy,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Para escanear el Código QR oficial de asistencia de la UNERMB, la aplicación necesita acceso a la cámara.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    Text("Conceder Permiso", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Modal de Resultado con Badge de Puntualidad y Opción No Bloqueante de Justificación por Retardo
        if (showResultDialog && lastResult != null) {
            MarcajeResultDialog(
                result = lastResult,
                onDismiss = onDismissDialog,
                onJustificarRetardo = {
                    val regId = lastResult.registro?.id
                    if (regId != null) {
                        onRequestJustificacion(
                            regId,
                            lastResult.puntualidad?.materiaNombre ?: "Clase Programada",
                            lastResult.puntualidad?.minutosRetraso ?: 0
                        )
                    } else {
                        onDismissDialog()
                    }
                }
            )
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraPreviewWithMLKit(
    isLocked: Boolean,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !isLocked) {
                        val inputImage = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(inputImage)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val raw = barcode.rawValue
                                    if (!raw.isNullOrBlank()) {
                                        onBarcodeDetected(raw)
                                        break
                                    }
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {}
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scanAreaSize = size.width * 0.72f
        val left = (size.width - scanAreaSize) / 2
        val top = (size.height - scanAreaSize) / 2.2f

        // Fondo semi-transparente oscuro
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            size = size
        )

        // Hueco transparente central para el escaneo
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(scanAreaSize, scanAreaSize),
            cornerRadius = CornerRadius(20.dp.toPx()),
            blendMode = BlendMode.Clear
        )

        // Borde estilizado con Azul Eléctrico UNERMB
        drawRoundRect(
            color = UnerbElectricBlue,
            topLeft = Offset(left, top),
            size = Size(scanAreaSize, scanAreaSize),
            cornerRadius = CornerRadius(20.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Esquinas resaltadas
        val cornerLength = 36.dp.toPx()
        val cornerStroke = 6.dp.toPx()
        val cornerColor = UnerbSky

        // Superior Izquierda
        drawLine(cornerColor, Offset(left - 2, top + cornerLength), Offset(left - 2, top), cornerStroke)
        drawLine(cornerColor, Offset(left, top - 2), Offset(left + cornerLength, top - 2), cornerStroke)

        // Superior Derecha
        drawLine(cornerColor, Offset(left + scanAreaSize + 2, top + cornerLength), Offset(left + scanAreaSize + 2, top), cornerStroke)
        drawLine(cornerColor, Offset(left + scanAreaSize, top - 2), Offset(left + scanAreaSize - cornerLength, top - 2), cornerStroke)

        // Inferior Izquierda
        drawLine(cornerColor, Offset(left - 2, top + scanAreaSize - cornerLength), Offset(left - 2, top + scanAreaSize), cornerStroke)
        drawLine(cornerColor, Offset(left, top + scanAreaSize + 2), Offset(left + cornerLength, top + scanAreaSize + 2), cornerStroke)

        // Inferior Derecha
        drawLine(cornerColor, Offset(left + scanAreaSize + 2, top + scanAreaSize - cornerLength), Offset(left + scanAreaSize + 2, top + scanAreaSize), cornerStroke)
        drawLine(cornerColor, Offset(left + scanAreaSize, top + scanAreaSize + 2), Offset(left + scanAreaSize - cornerLength, top + scanAreaSize + 2), cornerStroke)
    }
}

@Composable
fun MarcajeResultDialog(
    result: MarcajeQRResponse,
    onDismiss: () -> Unit,
    onJustificarRetardo: () -> Unit = {}
) {
    val tipo = result.tipo ?: "ENTRADA"
    val punt = result.puntualidad
    val estadoPunt = punt?.estado ?: "A_TIEMPO"
    val retrasoMin = punt?.minutosRetraso ?: 0
    val esRetardoEntrada = tipo == "ENTRADA" && estadoPunt == "TARDE" && result.registro?.id != null

    val (badgeColor, badgeText) = when (estadoPunt) {
        "A_TIEMPO" -> StatusGreen to "A TIEMPO"
        "TARDE" -> StatusAmber to "CON RETARDO (+$retrasoMin min)"
        "FUERA_DE_HORARIO" -> StatusRed to "FUERA DE HORARIO"
        else -> StatusBlue to estadoPunt
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (esRetardoEntrada) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (esRetardoEntrada) StatusAmber else StatusGreen,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Marcaje de $tipo",
                    fontWeight = FontWeight.Bold,
                    color = UnerbPrimaryNavy
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Badge de puntualidad
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.titleMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                punt?.materiaNombre?.let { materia ->
                    Column {
                        Text(text = "Materia Detectada:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(text = materia, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                punt?.bloqueHorario?.let { bloque ->
                    Column {
                        Text(text = "Bloque Horario:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(text = bloque, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    }
                }

                result.mensaje?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                if (esRetardoEntrada) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UnerbPrimaryNavy.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "¿Desea adjuntar una justificación (texto o foto de récipe/constancia) para este retardo o continuar sin justificar?",
                            style = MaterialTheme.typography.bodySmall,
                            color = UnerbPrimaryNavy,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (esRetardoEntrada) {
                Button(
                    onClick = onJustificarRetardo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    Text("Adjuntar Justificativo (Opcional)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    Text("Aceptar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (esRetardoEntrada) {
                TextButton(onClick = onDismiss) {
                    Text("Continuar sin justificar", color = TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}

