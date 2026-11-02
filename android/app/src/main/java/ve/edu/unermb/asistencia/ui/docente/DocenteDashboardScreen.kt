package ve.edu.unermb.asistencia.ui.docente

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import ve.edu.unermb.asistencia.core.DocenteAvatar
import ve.edu.unermb.asistencia.ui.theme.*

@Composable
fun DocenteDashboardScreen(
    viewModel: DocenteViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLogoutConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is DocenteEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is DocenteEvent.SessionTerminated -> onLogout()
                is DocenteEvent.MarcajeSuccess -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = UnerbSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Horario") },
                    label = { Text("Mi Horario", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UnerbPrimaryNavy,
                        indicatorColor = UnerbPrimaryNavy,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear QR") },
                    label = { Text("Escanear QR", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UnerbPrimaryNavy,
                        indicatorColor = UnerbPrimaryNavy,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Default.History, contentDescription = "Historial") },
                    label = { Text("Historial", fontWeight = FontWeight.SemiBold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UnerbPrimaryNavy,
                        indicatorColor = UnerbPrimaryNavy,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        },
        containerColor = UnerbBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Institucional con Perfil Docente y Reloj en Vivo (UTC-4)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = UnerbPrimaryNavy),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Foto de Perfil Tipo Carnet con URL normalizada y Avatar por defecto
                        DocenteAvatar(
                            fotoUrl = uiState.userProfile?.foto_carnet_url,
                            nombre = uiState.userProfile?.nombre ?: "Docente",
                            apellido = uiState.userProfile?.apellido ?: "",
                            baseUrl = uiState.baseUrl,
                            modifier = Modifier
                                .size(56.dp)
                                .border(2.dp, UnerbSky, CircleShape),
                            isCircle = true
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${uiState.userProfile?.nombre ?: "Docente"} ${uiState.userProfile?.apellido ?: ""}".trim(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            val cedula = uiState.userProfile?.cedula ?: ""
                            Text(
                                text = if (cedula.startsWith("V-") || cedula.startsWith("E-")) cedula else "V-$cedula",
                                style = MaterialTheme.typography.labelSmall,
                                color = UnerbSky,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Docente PNF en Informática",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        IconButton(
                            onClick = { showLogoutConfirmation = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Cerrar Sesión",
                                tint = Color.White
                            )
                        }
                    }

                    // Barra de Reloj en Vivo (Venezuela UTC-4)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = UnerbSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = uiState.liveClockVenezuela.ifBlank { "00:00:00 --" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                text = "HLV (UTC-4)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Contenido de la Pestaña Activa
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    0 -> HorarioTab(
                        horarioData = uiState.horarioData,
                        isLoading = uiState.isLoadingHorario,
                        onRefresh = { viewModel.loadHorario() }
                    )
                    1 -> QRScannerTab(
                        isScanningLocked = uiState.isScanningLocked,
                        lastResult = uiState.lastMarcajeResult,
                        showResultDialog = uiState.showResultDialog,
                        errorMessage = uiState.scanErrorMessage,
                        onQrScanned = { viewModel.onQrScanned(it) },
                        onDismissDialog = { viewModel.dismissResultDialog() },
                        onRequestJustificacion = { id, materia, minutos ->
                            viewModel.openJustificacionDialog(id, materia, minutos)
                        }
                    )
                    2 -> HistorialTab(
                        historialData = uiState.historialData,
                        isLoading = uiState.isLoadingHistorial,
                        onRefresh = { viewModel.loadHistorial() },
                        onJustificarClick = { reg ->
                            viewModel.openJustificacionDialog(
                                asistenciaId = reg.id,
                                materia = reg.materia_detectada ?: "Clase Programada",
                                minutosRetraso = reg.minutos_retraso ?: 0
                            )
                        }
                    )
                }
            }
        }

        // Modal Opcional de Justificación por Retardo Docente
        if (uiState.showJustificacionDialog) {
            JustificacionRetardoDialog(
                materiaNombre = uiState.justificacionMateria,
                minutosRetraso = uiState.justificacionMinutos,
                isSubmitting = uiState.isSubmittingJustificacion,
                onDismiss = { viewModel.dismissJustificacionDialog() },
                onSubmit = { motivo, uri -> viewModel.submitJustificacionRetardo(motivo, uri) }
            )
        }

        // Dialog Confirmación de Cerrar Sesión
        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmation = false },
                title = { Text("Cerrar Sesión", fontWeight = FontWeight.Bold, color = UnerbPrimaryNavy) },
                text = { Text("¿Está seguro de que desea cerrar su sesión persistente en este dispositivo?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmation = false
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirmation = false }) {
                        Text("Cancelar", color = UnerbPrimaryNavy, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}

@Composable
fun JustificacionRetardoDialog(
    materiaNombre: String,
    minutosRetraso: Int,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (motivo: String, photoUri: Uri?) -> Unit
) {
    var motivoText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) selectedImageUri = uri
        }
    )

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AssignmentLate,
                    contentDescription = null,
                    tint = StatusAmber
                )
                Text(
                    text = "Justificar Retardo (Opcional)",
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusAmber.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Retardo registrado: +$minutosRetraso min en $materiaNombre",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF92400E),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Su asistencia ya fue registrada. Opcionalmente puede redactar el motivo del retraso y/o adjuntar una fotografía de respaldo (ej. récipe o constancia médica) para la Coordinación.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                OutlinedTextField(
                    value = motivoText,
                    onValueChange = { motivoText = it },
                    label = { Text("Motivo o explicación del retardo") },
                    placeholder = { Text("Ej. Retraso en transporte, consulta médica...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selector de imagen opcional
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .background(UnerbSurfaceVariant)
                        .clickable {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedImageUri != null) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Evidencia adjunta",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Imagen adjunta lista",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                            Text(
                                text = "Toque para cambiar imagen",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = UnerbElectricBlue
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Adjuntar Récipe / Evidencia (Opcional)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = UnerbPrimaryNavy
                            )
                            Text(
                                text = "Seleccionar imagen de galería o cámara",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(motivoText, selectedImageUri) },
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UnerbPrimaryNavy,
                    contentColor = Color.White
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Enviar Justificativo", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Continuar sin justificar", color = TextSecondary, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
