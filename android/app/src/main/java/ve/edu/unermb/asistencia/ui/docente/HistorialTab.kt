package ve.edu.unermb.asistencia.ui.docente

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ve.edu.unermb.asistencia.core.DateTimeUtils
import ve.edu.unermb.asistencia.data.remote.dto.AsistenciaRegistroDto
import ve.edu.unermb.asistencia.data.remote.dto.HistorialResponse
import ve.edu.unermb.asistencia.ui.theme.*

@Composable
fun HistorialTab(
    historialData: HistorialResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onJustificarClick: (AsistenciaRegistroDto) -> Unit = {}
) {
    val resumen = historialData?.resumen
    val items = historialData?.historial ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && historialData == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = UnerbElectricBlue
            )
        } else if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin Marcajes Registrados",
                    style = MaterialTheme.typography.titleLarge,
                    color = UnerbPrimaryNavy
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aún no ha escaneado el Código QR para registrar asistencia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Métricas y Resumen
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Resumen de Asistencia",
                            style = MaterialTheme.typography.titleMedium,
                            color = UnerbPrimaryNavy,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(
                                title = "Entradas",
                                value = resumen?.entradas?.toString() ?: "0",
                                color = StatusBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "A Tiempo",
                                value = resumen?.a_tiempo?.toString() ?: "0",
                                color = StatusGreen,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Retardos",
                                value = resumen?.tarde?.toString() ?: "0",
                                color = StatusAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Historial Detallado",
                        style = MaterialTheme.typography.titleMedium,
                        color = UnerbPrimaryNavy,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(items) { reg ->
                    HistorialItemCard(
                        reg = reg,
                        onJustificarClick = { onJustificarClick(reg) }
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = UnerbSurface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun HistorialItemCard(
    reg: AsistenciaRegistroDto,
    onJustificarClick: () -> Unit = {}
) {
    val isEntrada = reg.tipo == "ENTRADA"
    val puntEstado = reg.puntualidad_estado ?: "A_TIEMPO"
    val minutos = reg.minutos_retraso ?: 0
    val tieneJustificativo = reg.justificacion_estado == "ENVIADA" || reg.justificacion_estado == "REVISADA" || !reg.justificacion_texto.isNullOrBlank()

    val (badgeColor, badgeText) = when (puntEstado) {
        "A_TIEMPO" -> StatusGreen to "A TIEMPO"
        "TARDE" -> StatusAmber to "TARDE (+$minutos min)"
        "FUERA_DE_HORARIO" -> StatusRed to "FUERA DE HORARIO"
        else -> TextSecondary to puntEstado
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = UnerbSurface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icono Tipo Marcaje
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isEntrada) StatusBlue.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEntrada) Icons.Default.Login else Icons.Default.Logout,
                        contentDescription = null,
                        tint = if (isEntrada) StatusBlue else StatusAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reg.tipo,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = UnerbPrimaryNavy
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    reg.materia_detectada?.let { mat ->
                        Text(
                            text = mat,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    reg.bloque_horario_detectado?.let { bloque ->
                        Text(
                            text = "Bloque: $bloque",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = DateTimeUtils.formatDisplayDateTime(reg.fecha_hora),
                        style = MaterialTheme.typography.labelSmall,
                        color = UnerbElectricBlue
                    )
                }
            }

            if (isEntrada && puntEstado == "TARDE") {
                if (tieneJustificativo) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UnerbPrimaryNavy.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "✓ Justificativo enviado a Coordinación",
                                style = MaterialTheme.typography.labelSmall,
                                color = UnerbElectricBlue,
                                fontWeight = FontWeight.Bold
                            )
                            if (!reg.justificacion_texto.isNullOrBlank()) {
                                Text(
                                    text = "\"${reg.justificacion_texto}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onJustificarClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = UnerbPrimaryNavy)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adjuntar Justificativo de Retardo (Opcional)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

