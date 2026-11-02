package ve.edu.unermb.asistencia.ui.superroot

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import ve.edu.unermb.asistencia.core.AdaptiveEvidenceImage
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.core.DocenteAvatar
import ve.edu.unermb.asistencia.core.ImageUtils
import ve.edu.unermb.asistencia.data.remote.dto.*
import ve.edu.unermb.asistencia.ui.docente.StatCard
import ve.edu.unermb.asistencia.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperrootDashboardScreen(
    viewModel: SuperrootViewModel,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is SuperrootEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is SuperrootEvent.SessionTerminated -> onLogout()
            }
        }
    }

    val navItemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White,
        selectedTextColor = UnerbPrimaryNavy,
        indicatorColor = UnerbPrimaryNavy,
        unselectedIconColor = TextSecondary,
        unselectedTextColor = TextSecondary
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Panel de Coordinación UNERMB",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Superroot • PNF en Informática",
                            style = MaterialTheme.typography.labelSmall,
                            color = UnerbSky
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Cerrar Sesión",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = UnerbPrimaryNavy)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = UnerbSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = uiState.activeTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Métricas", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = "Código QR") },
                    label = { Text("Código QR", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.solicitudes.isNotEmpty()) {
                                    Badge(
                                        containerColor = StatusRed,
                                        contentColor = Color.White
                                    ) {
                                        Text("${uiState.solicitudes.size}", color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = "Solicitudes")
                        }
                    },
                    label = { Text("Solicitudes", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    icon = { Icon(Icons.Default.ManageAccounts, contentDescription = "Horarios") },
                    label = { Text("Horarios", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navItemColors
                )
                NavigationBarItem(
                    selected = uiState.activeTab == 4,
                    onClick = { viewModel.selectTab(4) },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Auditoría") },
                    label = { Text("Auditoría", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                    colors = navItemColors
                )
            }
        },
        containerColor = UnerbBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.activeTab) {
                0 -> MetricasTab(
                    stats = uiState.stats,
                    isLoading = uiState.isLoadingStats,
                    onRefresh = { viewModel.loadDashboardStats() }
                )
                1 -> QRGeneratorTab(
                    qrData = uiState.qrTokenData,
                    isLoading = uiState.isLoadingQR
                )
                2 -> SolicitudesTab(
                    solicitudes = uiState.solicitudes,
                    baseUrl = uiState.baseUrl,
                    isLoading = uiState.isLoadingSolicitudes,
                    onAprobar = { viewModel.aprobarSolicitud(it) },
                    onRechazar = { viewModel.rechazarSolicitud(it) }
                )
                3 -> DocentesHorariosTab(
                    docentes = uiState.docentes,
                    baseUrl = uiState.baseUrl,
                    isLoading = uiState.isLoadingDocentes,
                    onSelectDocente = { viewModel.selectDocenteForHorario(it) }
                )
                4 -> AuditoriaTab(
                    logs = uiState.asistenciasLogs,
                    baseUrl = uiState.baseUrl,
                    isLoading = uiState.isLoadingLogs,
                    onFilter = { fecha, tipo, punt -> viewModel.setFiltros(fecha, tipo, punt) }
                )
            }

            // Modal de Asignación de Horarios mediante Cuadrícula Interactiva (Réplica Web)
            if (uiState.selectedDocenteForHorario != null) {
                AsignarHorarioGridModal(
                    docente = uiState.selectedDocenteForHorario!!,
                    baseUrl = uiState.baseUrl,
                    horarioData = uiState.selectedDocenteHorarioData,
                    estructuraPensum = uiState.estructuraPensum,
                    catalogoMaterias = uiState.catalogoMaterias,
                    isLoading = uiState.isLoadingDocenteHorario,
                    onDismiss = { viewModel.selectDocenteForHorario(null) },
                    onAsignar = { matId, dia, bloqueNum -> viewModel.asignarHorario(matId, dia, bloqueNum) },
                    onEliminar = { hId -> viewModel.eliminarHorario(hId) }
                )
            }
        }
    }
}

@Composable
fun MetricasTab(
    stats: StatsDto?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    if (isLoading && stats == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = UnerbElectricBlue)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Resumen General del Personal Docente",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = UnerbPrimaryNavy
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = UnerbElectricBlue)
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(title = "Total Docentes", value = stats?.totalDocentes?.toString() ?: "0", color = UnerbPrimaryNavy, modifier = Modifier.weight(1f))
                StatCard(title = "Activos", value = stats?.activos?.toString() ?: "0", color = StatusGreen, modifier = Modifier.weight(1f))
                StatCard(title = "Pendientes", value = stats?.pendientes?.toString() ?: "0", color = StatusAmber, modifier = Modifier.weight(1f))
            }
        }

        item {
            Text(
                text = "Marcajes de Asistencia de Hoy",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UnerbPrimaryNavy,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        item {
            val hoy = stats?.marcajesHoy
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(title = "Marcajes Hoy", value = hoy?.total?.toString() ?: "0", color = UnerbElectricBlue, modifier = Modifier.weight(1f))
                    StatCard(title = "Entradas", value = hoy?.entradas?.toString() ?: "0", color = StatusBlue, modifier = Modifier.weight(1f))
                    StatCard(title = "Salidas", value = hoy?.salidas?.toString() ?: "0", color = StatusBlue, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(title = "A Tiempo", value = hoy?.a_tiempo?.toString() ?: "0", color = StatusGreen, modifier = Modifier.weight(1f))
                    StatCard(title = "Retardos", value = hoy?.tarde?.toString() ?: "0", color = StatusAmber, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Requerimientos 1 y 3:
 * - Generación y Rotación Automatizada del Código QR Diario (sin botón manual de regeneración).
 * - Decodificación nativa Base64 -> ImageBitmap para renderizado 100% confiable en Android.
 */
@Composable
fun QRGeneratorTab(
    qrData: QRTokenResponse?,
    isLoading: Boolean
) {
    val rawDataUrl = qrData?.qrDataUrl ?: qrData?.qrDataURL
    val qrBitmap = remember(rawDataUrl) {
        ImageUtils.decodeBase64DataUrlToBitmap(rawDataUrl)
    }
    val fechaVigente = qrData?.fecha_vigente ?: DateTimeUtils.todayIsoInVenezuela()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = UnerbSurface),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = StatusGreen.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = StatusGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CICLO DIARIO AUTOMÁTICO 24H",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusGreen,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = "Código QR Institucional del Día",
                    style = MaterialTheme.typography.titleLarge,
                    color = UnerbPrimaryNavy,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Punto Central de Marcaje PNF en Informática - UNERMB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                // Lienzo del Código QR decodificado nativamente
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(3.dp, UnerbPrimaryNavy, RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading && qrBitmap == null) {
                        CircularProgressIndicator(color = UnerbElectricBlue)
                    } else if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = "Código QR Oficial Diario UNERMB",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = UnerbPrimaryNavy,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Sincronizando QR diario...",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Información del Token Diario Criptográfico
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = UnerbPrimaryNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = qrData?.token ?: "UNERMB-PNF-INFO-QR-$fechaVigente",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Vigencia: $fechaVigente (00:00:00 a 23:59:59 HLV)",
                            style = MaterialTheme.typography.labelSmall,
                            color = UnerbSky,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = "Este código se renueva automáticamente cada día a las 00:00:00 (Hora de Venezuela). Cualquier QR de días anteriores es rechazado por el servidor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SolicitudesTab(
    solicitudes: List<SolicitudDocenteDto>,
    baseUrl: String,
    isLoading: Boolean,
    onAprobar: (Int) -> Unit,
    onRechazar: (Int) -> Unit
) {
    if (isLoading && solicitudes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = UnerbElectricBlue)
        }
        return
    }

    if (solicitudes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay solicitudes pendientes de aprobación.", color = TextSecondary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(solicitudes) { sol ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocenteAvatar(
                            fotoUrl = sol.foto_carnet_url,
                            nombre = sol.nombre,
                            apellido = sol.apellido,
                            baseUrl = baseUrl,
                            modifier = Modifier
                                .size(64.dp)
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                            isCircle = false
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${sol.nombre} ${sol.apellido}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = UnerbPrimaryNavy
                            )
                            Text(
                                text = "C.I. ${sol.cedula}",
                                style = MaterialTheme.typography.labelSmall,
                                color = UnerbElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = sol.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    if (!sol.materias.isNullOrEmpty()) {
                        Text(
                            text = "Materias Declaradas:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        sol.materias.forEach { m ->
                            Text(
                                text = "• ${m.displayNombre}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onAprobar(sol.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Aprobar", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onRechazar(sol.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rechazar", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DocentesHorariosTab(
    docentes: List<UserDto>,
    baseUrl: String,
    isLoading: Boolean,
    onSelectDocente: (UserDto) -> Unit
) {
    if (isLoading && docentes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = UnerbElectricBlue)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Seleccione un Docente para Gestionar su Horario",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = UnerbPrimaryNavy
            )
            Text(
                text = "Toque sobre un docente para abrir la cuadrícula interactiva de bloques y días.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        items(docentes) { doc ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectDocente(doc) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DocenteAvatar(
                        fotoUrl = doc.foto_carnet_url,
                        nombre = doc.nombre,
                        apellido = doc.apellido,
                        baseUrl = baseUrl,
                        modifier = Modifier.size(48.dp),
                        isCircle = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${doc.nombre} ${doc.apellido}",
                            fontWeight = FontWeight.Bold,
                            color = UnerbPrimaryNavy
                        )
                        Text(
                            text = "C.I. ${doc.cedula} • ${doc.estado}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UnerbPrimaryNavy
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Horario",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requerimiento 4 y 5:
 * Réplica del Módulo de Selección de Horarios en la Aplicación Móvil mediante
 * una Cuadrícula Interactiva (5 Días x 5 Bloques Horarios), idéntica en usabilidad a la tabla Web.
 * Al presionar sobre cualquier bloque disponible en la matriz, abre una ventana modal donde el
 * coordinador selecciona directamente qué Trayecto (y Trimestre) y qué Materia desea asignar.
 */
@Composable
fun AsignarHorarioGridModal(
    docente: UserDto,
    baseUrl: String,
    horarioData: DocenteHorarioResponse?,
    estructuraPensum: List<TrayectoDto>,
    catalogoMaterias: List<MateriaDto>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAsignar: (materiaId: Int, dia: String, bloqueNum: Int) -> Unit,
    onEliminar: (horarioId: Int) -> Unit
) {
    val dias = horarioData?.diasSemanaDisponibles?.takeIf { it.isNotEmpty() }
        ?: listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")

    val bloques = horarioData?.bloquesHorariosDisponibles?.takeIf { it.isNotEmpty() }
        ?: listOf(
            BloqueConfigDto(1, "7:30 am - 9:00 am", "07:30:00", "09:00:00"),
            BloqueConfigDto(2, "9:00 am - 10:30 am", "09:00:00", "10:30:00"),
            BloqueConfigDto(3, "10:30 am - 12:00 pm", "10:30:00", "12:00:00"),
            BloqueConfigDto(4, "12:00 pm - 1:30 pm", "12:00:00", "13:30:00"),
            BloqueConfigDto(5, "1:30 pm - 3:00 pm", "13:30:00", "15:00:00")
        )

    val horariosAsignados = horarioData?.horarios ?: emptyList()

    // Estado para la celda seleccionada al presionar "+ Disponible" en la matriz
    var activeSlotForModal by remember { mutableStateOf<Pair<String, BloqueConfigDto>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = UnerbBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Cabecera del Modal con Perfil del Docente
                Surface(
                    color = UnerbPrimaryNavy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DocenteAvatar(
                            fotoUrl = docente.foto_carnet_url,
                            nombre = docente.nombre,
                            apellido = docente.apellido,
                            baseUrl = baseUrl,
                            modifier = Modifier
                                .size(46.dp)
                                .border(1.5.dp, UnerbSky, CircleShape),
                            isCircle = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${docente.nombre} ${docente.apellido}",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "C.I. ${docente.cedula} • ${horariosAsignados.size} bloques asignados",
                                style = MaterialTheme.typography.labelSmall,
                                color = UnerbSky
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Instrucciones de la Cuadrícula Interactiva
                Surface(
                    color = UnerbElectricBlue.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = UnerbElectricBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Presione sobre cualquier bloque '+ Disponible' en la cuadrícula para elegir Trayecto y Materia.",
                            style = MaterialTheme.typography.bodySmall,
                            color = UnerbPrimaryNavy,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (isLoading && horarioData == null) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = UnerbElectricBlue)
                    }
                } else {
                    // Matriz Interactiva 5x5 con desplazamiento bidireccional
                    val horizontalScroll = rememberScrollState()
                    val verticalScroll = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll)
                            .padding(12.dp)
                    ) {
                        // Fila de Encabezados (Franja Horaria + Lunes a Viernes)
                        Row(
                            modifier = Modifier.background(UnerbPrimaryNavy, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Franja Horaria",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            dias.forEach { dia ->
                                Box(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dia.uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Filas de Bloques Horarios (Bloque 1 a Bloque 5)
                        bloques.forEach { bloque ->
                            Row(
                                modifier = Modifier
                                    .background(UnerbSurface)
                                    .border(0.5.dp, BorderColor)
                            ) {
                                // Columna Izquierda: Etiqueta del Bloque
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(112.dp)
                                        .background(UnerbSurfaceVariant)
                                        .border(0.5.dp, BorderColor)
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = UnerbPrimaryNavy
                                    ) {
                                        Text(
                                            text = "Bloque ${bloque.numero}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = bloque.etiqueta,
                                        color = UnerbPrimaryNavy,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Celdas por Día (Lunes a Viernes)
                                dias.forEach { dia ->
                                    val asignado = horariosAsignados.find {
                                        it.dia_semana.equals(dia, ignoreCase = true) && it.bloque_numero == bloque.numero
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(148.dp)
                                            .height(112.dp)
                                            .border(0.5.dp, BorderColor)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (asignado != null) {
                                            // Celda Ocupada
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UnerbElectricBlue.copy(alpha = 0.1f),
                                                border = BorderStroke(1.dp, UnerbElectricBlue),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(6.dp),
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column {
                                                        Text(
                                                            text = asignado.materia_nombre,
                                                            color = UnerbPrimaryNavy,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${asignado.trayecto} • ${asignado.trimestre}",
                                                            color = TextSecondary,
                                                            fontSize = 9.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = StatusRed,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { onEliminar(asignado.id) }
                                                    ) {
                                                        Text(
                                                            text = "× Quitar",
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp,
                                                            textAlign = TextAlign.Center,
                                                            modifier = Modifier.padding(vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            // Celda Disponible (Interactiva al presionar)
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = UnerbSurfaceVariant,
                                                border = BorderStroke(1.dp, BorderColor),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable {
                                                        activeSlotForModal = dia to bloque
                                                    }
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.AddCircleOutline,
                                                        contentDescription = "Asignar en $dia Bloque ${bloque.numero}",
                                                        tint = UnerbElectricBlue,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Disponible",
                                                        color = UnerbElectricBlue,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pie del modal
                Surface(
                    color = UnerbSurface,
                    tonalElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UnerbPrimaryNavy,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cerrar Cuadrícula", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Sub-Modal al presionar sobre cualquier bloque disponible en la matriz:
    // Permite seleccionar directamente qué Trayecto (y Trimestre) y qué Materia desea asignar a ese espacio
    if (activeSlotForModal != null) {
        val (diaSeleccionado, bloqueSeleccionado) = activeSlotForModal!!
        SeleccionarMateriaCeldaDialog(
            dia = diaSeleccionado,
            bloque = bloqueSeleccionado,
            estructuraPensum = estructuraPensum,
            catalogoMaterias = catalogoMaterias,
            onDismiss = { activeSlotForModal = null },
            onConfirmar = { materiaId ->
                activeSlotForModal = null
                onAsignar(materiaId, diaSeleccionado, bloqueSeleccionado.numero)
            }
        )
    }
}

@Composable
fun SeleccionarMateriaCeldaDialog(
    dia: String,
    bloque: BloqueConfigDto,
    estructuraPensum: List<TrayectoDto>,
    catalogoMaterias: List<MateriaDto>,
    onDismiss: () -> Unit,
    onConfirmar: (materiaId: Int) -> Unit
) {
    // Construir lista de Trayectos desde estructuraPensum o desde catalogoMaterias
    val trayectosDisponibles = remember(estructuraPensum, catalogoMaterias) {
        if (estructuraPensum.isNotEmpty()) {
            estructuraPensum.map { it.nombre }
        } else {
            catalogoMaterias.mapNotNull { it.trayecto }.distinct()
        }
    }

    var selectedTrayecto by remember(trayectosDisponibles) {
        mutableStateOf(trayectosDisponibles.firstOrNull() ?: "")
    }

    val trimestresDisponibles = remember(selectedTrayecto, estructuraPensum, catalogoMaterias) {
        if (estructuraPensum.isNotEmpty()) {
            estructuraPensum.find { it.nombre == selectedTrayecto }?.trimestres?.map { it.nombre } ?: emptyList()
        } else {
            catalogoMaterias.filter { it.trayecto == selectedTrayecto }.mapNotNull { it.trimestre }.distinct()
        }
    }

    var selectedTrimestre by remember(selectedTrayecto, trimestresDisponibles) {
        mutableStateOf(trimestresDisponibles.firstOrNull() ?: "")
    }

    val materiasFiltradas = remember(selectedTrayecto, selectedTrimestre, estructuraPensum, catalogoMaterias) {
        if (estructuraPensum.isNotEmpty()) {
            val trimObj = estructuraPensum
                .find { it.nombre == selectedTrayecto }
                ?.trimestres
                ?.find { it.nombre == selectedTrimestre }
            trimObj?.materias?.map {
                MateriaDto(
                    id = it.id,
                    codigo = it.codigo,
                    nombre_materia = it.nombre,
                    trayecto = selectedTrayecto,
                    trimestre = selectedTrimestre,
                    pnf = it.pnf
                )
            } ?: emptyList()
        } else {
            catalogoMaterias.filter {
                it.trayecto == selectedTrayecto && (selectedTrimestre.isBlank() || it.trimestre == selectedTrimestre)
            }
        }
    }

    var selectedMateriaId by remember(materiasFiltradas) {
        mutableStateOf(materiasFiltradas.firstOrNull()?.id ?: 0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Asignar Materia al Bloque",
                    fontWeight = FontWeight.Bold,
                    color = UnerbPrimaryNavy
                )
                Text(
                    text = "$dia • Bloque ${bloque.numero} (${bloque.etiqueta})",
                    style = MaterialTheme.typography.labelMedium,
                    color = UnerbElectricBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Paso 1: Seleccionar Trayecto
                Text(
                    text = "1. Seleccione el Trayecto:",
                    style = MaterialTheme.typography.labelMedium,
                    color = UnerbPrimaryNavy,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    trayectosDisponibles.forEach { tray ->
                        val isSelected = selectedTrayecto == tray
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) UnerbPrimaryNavy else UnerbSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTrayecto = tray }
                        ) {
                            Text(
                                text = tray,
                                color = if (isSelected) Color.White else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Paso 2: Seleccionar Trimestre
                if (trimestresDisponibles.isNotEmpty()) {
                    Text(
                        text = "2. Seleccione el Trimestre:",
                        style = MaterialTheme.typography.labelMedium,
                        color = UnerbPrimaryNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        trimestresDisponibles.forEach { trim ->
                            FilterChip(
                                selected = selectedTrimestre == trim,
                                onClick = { selectedTrimestre = trim },
                                label = {
                                    Text(
                                        text = trim,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = UnerbPrimaryNavy,
                                    selectedLabelColor = Color.White,
                                    containerColor = UnerbSurfaceVariant,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }

                // Paso 3: Seleccionar Unidad Curricular (Materia)
                Text(
                    text = "3. Seleccione la Materia (Pensum Oficial):",
                    style = MaterialTheme.typography.labelMedium,
                    color = UnerbPrimaryNavy,
                    fontWeight = FontWeight.Bold
                )
                if (materiasFiltradas.isEmpty()) {
                    Text(
                        text = "No hay materias disponibles en este trayecto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        materiasFiltradas.forEach { mat ->
                            val isSelected = selectedMateriaId == mat.id
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) UnerbElectricBlue else UnerbSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMateriaId = mat.id }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedMateriaId = mat.id },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color.White,
                                            unselectedColor = UnerbPrimaryNavy
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mat.displayNombre,
                                            color = if (isSelected) Color.White else TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Código: ${mat.codigo}",
                                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMateriaId != 0) {
                        onConfirmar(selectedMateriaId)
                    }
                },
                enabled = selectedMateriaId != 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UnerbPrimaryNavy,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Asignar al Bloque", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = UnerbPrimaryNavy, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
fun AuditoriaTab(
    logs: List<AsistenciaLogItemDto>,
    baseUrl: String,
    isLoading: Boolean,
    onFilter: (fecha: String, tipo: String, punt: String) -> Unit
) {
    var tipoFiltro by remember { mutableStateOf("") }
    var puntFiltro by remember { mutableStateOf("") }
    var selectedLogWithJustificativo by remember { mutableStateOf<AsistenciaLogItemDto?>(null) }

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = UnerbPrimaryNavy,
        selectedLabelColor = Color.White,
        containerColor = UnerbSurfaceVariant,
        labelColor = TextPrimary
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Barra de Filtros con alto contraste
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = tipoFiltro == "" && puntFiltro == "",
                onClick = { tipoFiltro = ""; puntFiltro = ""; onFilter("", "", "") },
                label = { Text("Todos", fontWeight = FontWeight.Bold) },
                colors = chipColors
            )
            FilterChip(
                selected = tipoFiltro == "ENTRADA",
                onClick = { tipoFiltro = "ENTRADA"; onFilter("", tipoFiltro, puntFiltro) },
                label = { Text("Entradas", fontWeight = FontWeight.Bold) },
                colors = chipColors
            )
            FilterChip(
                selected = tipoFiltro == "SALIDA",
                onClick = { tipoFiltro = "SALIDA"; onFilter("", tipoFiltro, puntFiltro) },
                label = { Text("Salidas", fontWeight = FontWeight.Bold) },
                colors = chipColors
            )
            FilterChip(
                selected = puntFiltro == "TARDE",
                onClick = {
                    puntFiltro = if (puntFiltro == "TARDE") "" else "TARDE"
                    onFilter("", tipoFiltro, puntFiltro)
                },
                label = { Text("Con Retardo", fontWeight = FontWeight.Bold) },
                colors = chipColors
            )
        }

        if (isLoading && logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UnerbElectricBlue)
            }
        } else if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay registros de asistencia para los filtros seleccionados.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    val tieneJustificativo = log.justificacion_estado == "ENVIADA" ||
                        log.justificacion_estado == "REVISADA" ||
                        !log.justificacion_texto.isNullOrBlank()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                DocenteAvatar(
                                    fotoUrl = log.foto_carnet_url,
                                    nombre = log.nombre,
                                    apellido = log.apellido,
                                    baseUrl = baseUrl,
                                    modifier = Modifier.size(44.dp),
                                    isCircle = true
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${log.nombre} ${log.apellido}",
                                            fontWeight = FontWeight.Bold,
                                            color = UnerbPrimaryNavy
                                        )
                                        Text(
                                            text = log.tipo,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (log.tipo == "ENTRADA") StatusBlue else StatusAmber
                                        )
                                    }
                                    Text(
                                        text = "C.I. ${log.cedula} • ${log.materia_detectada ?: "Jornada Docente"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = DateTimeUtils.formatDisplayDateTime(log.fecha_hora),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        val puntLabel = if (log.puntualidad_estado == "TARDE") {
                                            "TARDE (+${log.minutos_retraso ?: 0} min)"
                                        } else {
                                            log.puntualidad_estado ?: "A_TIEMPO"
                                        }
                                        Text(
                                            text = puntLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.puntualidad_estado == "A_TIEMPO") StatusGreen else StatusAmber
                                        )
                                    }
                                }
                            }

                            if (tieneJustificativo) {
                                Button(
                                    onClick = { selectedLogWithJustificativo = log },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = UnerbPrimaryNavy,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ver Justificativo de Retardo",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedLogWithJustificativo != null) {
        val log = selectedLogWithJustificativo!!
        val fullEvidenciaUrl = remember(log.justificacion_imagen_url, baseUrl) {
            ImageUtils.resolveFullMediaUrl(log.justificacion_imagen_url, baseUrl)
        }

        AlertDialog(
            onDismissRequest = { selectedLogWithJustificativo = null },
            title = {
                Text(
                    text = "Justificativo de Retardo",
                    fontWeight = FontWeight.Bold,
                    color = UnerbPrimaryNavy
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Docente: ${log.nombre} ${log.apellido} (C.I. ${log.cedula})",
                        fontWeight = FontWeight.Bold,
                        color = UnerbPrimaryNavy
                    )
                    Text(
                        text = "Clase: ${log.materia_detectada ?: "Jornada"} • Retardo: +${log.minutos_retraso ?: 0} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Divider()
                    Text(
                        text = "Motivo / Explicación Escrita:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UnerbSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = log.justificacion_texto ?: "Sin texto descriptivo.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    if (!fullEvidenciaUrl.isNullOrBlank()) {
                        Text(
                            text = "Evidencia Fotográfica / Récipe Adjunto:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        AdaptiveEvidenceImage(
                            rawUrl = log.justificacion_imagen_url,
                            baseUrl = baseUrl,
                            contentDescription = "Evidencia de Justificativo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 300.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedLogWithJustificativo = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    Text("Cerrar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
