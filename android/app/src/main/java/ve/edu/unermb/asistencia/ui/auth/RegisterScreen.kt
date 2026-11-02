package ve.edu.unermb.asistencia.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import ve.edu.unermb.asistencia.ui.theme.*

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.onPhotoSelected(uri)
            }
        }
    )

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is RegisterEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is RegisterEvent.RegistrationSuccessPending -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Registro Docente UNERMB",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UnerbPrimaryNavy
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = UnerbBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sección Foto Tipo Carnet
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Fotografía Tipo Carnet (Obligatoria)",
                            style = MaterialTheme.typography.titleMedium,
                            color = UnerbPrimaryNavy,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Rostro visible, fondo claro, formato JPG o PNG (< 5MB)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(2.dp, UnerbElectricBlue, RoundedCornerShape(16.dp))
                                .background(UnerbSurfaceVariant)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.selectedPhotoUri != null) {
                                AsyncImage(
                                    model = uiState.selectedPhotoUri,
                                    contentDescription = "Foto carnet seleccionada",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "Subir foto",
                                        tint = UnerbElectricBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Subir Foto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = UnerbElectricBlue
                                    )
                                }
                            }
                        }

                        if (uiState.compressedPhotoFile != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "✓ Foto cargada y optimizada",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusGreen,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Datos Personales
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Datos Personales e Institucionales",
                            style = MaterialTheme.typography.titleMedium,
                            color = UnerbPrimaryNavy,
                            fontWeight = FontWeight.Bold
                        )

                        // Nombres y Apellidos
                        OutlinedTextField(
                            value = uiState.nombre,
                            onValueChange = { viewModel.onNombreChange(it) },
                            label = { Text("Nombres") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.apellido,
                            onValueChange = { viewModel.onApellidoChange(it) },
                            label = { Text("Apellidos") },
                            leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Selector V / E + Cédula con filtro estricto de hasta 8 dígitos
                        Text(
                            text = "Cédula de Identidad (Límite: 8 dígitos)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = uiState.tipoCedula == "V",
                                onClick = { viewModel.onTipoCedulaChange("V") },
                                label = { Text("V") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = UnerbElectricBlue,
                                    selectedLabelColor = Color.White
                                )
                            )
                            FilterChip(
                                selected = uiState.tipoCedula == "E",
                                onClick = { viewModel.onTipoCedulaChange("E") },
                                label = { Text("E") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = UnerbElectricBlue,
                                    selectedLabelColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = uiState.cedulaDigits,
                                onValueChange = { viewModel.onCedulaDigitsChange(it) },
                                label = { Text("Número de Cédula") },
                                placeholder = { Text("Ej. 12345678") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Correo Institucional
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = { viewModel.onEmailChange(it) },
                            label = { Text("Correo Electrónico") },
                            placeholder = { Text("usuario@unermb.edu.ve") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Contraseña y Confirmación
                        OutlinedTextField(
                            value = uiState.password,
                            onValueChange = { viewModel.onPasswordChange(it) },
                            label = { Text("Contraseña (mínimo 8 caracteres)") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = uiState.confirmPassword,
                            onValueChange = { viewModel.onConfirmPasswordChange(it) },
                            label = { Text("Confirmar Contraseña") },
                            leadingIcon = { Icon(Icons.Default.LockClock, contentDescription = null) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Selección de Materias (Opcional en registro, hasta 5 del Pensum Oficial)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Materias a Dictar (Máximo 5)",
                            style = MaterialTheme.typography.titleMedium,
                            color = UnerbPrimaryNavy,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Seleccionadas: ${uiState.selectedMateriasIds.size}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.selectedMateriasIds.size == 5) StatusAmber else TextSecondary
                        )

                        uiState.estructuraPensum.forEach { trayecto ->
                            Text(
                                text = trayecto.nombre,
                                style = MaterialTheme.typography.labelLarge,
                                color = UnerbElectricBlue,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            trayecto.trimestres.forEach { trimestre ->
                                Text(
                                    text = trimestre.nombre,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    trimestre.materias.forEach { materia ->
                                        val isSelected = uiState.selectedMateriasIds.contains(materia.id)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) UnerbSky.copy(alpha = 0.15f) else Color.Transparent)
                                                .clickable { viewModel.toggleMateriaSelection(materia.id) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { viewModel.toggleMateriaSelection(materia.id) }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = materia.nombre,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) UnerbPrimaryNavy else TextPrimary
                                                )
                                                Text(
                                                    text = materia.codigo,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Botón Enviar Registro
                Button(
                    onClick = { viewModel.submitRegister() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ENVIAR SOLICITUD DE REGISTRO",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Modal Indicador Nativo de Estado: PENDIENTE DE APROBACIÓN POR COORDINACIÓN
        if (uiState.showSuccessDialog) {
            AlertDialog(
                onDismissRequest = { /* Forzar botón para volver */ },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(StatusAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "SOLICITUD PENDIENTE",
                        fontWeight = FontWeight.Bold,
                        color = UnerbPrimaryNavy,
                        textAlign = TextAlign.Center
                    )
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusAmber.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "ESTADO: PENDIENTE DE APROBACIÓN POR COORDINACIÓN",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusAmber,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "¡Su registro ha sido transmitido con éxito!\n\nNo podrá iniciar sesión hasta que el Superroot (Coordinación del PNF en Informática) verifique sus recaudos, foto tipo carnet y materias seleccionadas.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onNavigateBack,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UnerbPrimaryNavy,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Entendido, volver al inicio", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

