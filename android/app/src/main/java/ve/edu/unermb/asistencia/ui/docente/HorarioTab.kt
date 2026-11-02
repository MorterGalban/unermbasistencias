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
import ve.edu.unermb.asistencia.data.remote.dto.HorarioBloqueDto
import ve.edu.unermb.asistencia.data.remote.dto.HorarioDocenteResponse
import ve.edu.unermb.asistencia.ui.theme.*

@Composable
fun HorarioTab(
    horarioData: HorarioDocenteResponse?,
    isLoading: Boolean,
    onRefresh: () -> Unit
) {
    val diaHoy = DateTimeUtils.todayDayOfWeekSpanish()
    val diasOrden = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")

    val horariosPorDia = horarioData?.horarios?.groupBy { it.dia_semana } ?: emptyMap()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && horarioData == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = UnerbElectricBlue
            )
        } else if (horarioData?.horarios.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Sin Horario Asignado",
                    style = MaterialTheme.typography.titleLarge,
                    color = UnerbPrimaryNavy
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "La Coordinación del PNF en Informática aún no ha configurado sus bloques horarios semanales.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRefresh,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UnerbPrimaryNavy,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Comprobar Actualizaciones", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    // Resumen Clases de Hoy
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = UnerbSurface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = UnerbElectricBlue
                                    )
                                    Text(
                                        text = "Hoy es $diaHoy",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = UnerbPrimaryNavy,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                val clasesHoyCount = horarioData?.clasesHoy?.size ?: 0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (clasesHoyCount > 0) StatusGreen.copy(alpha = 0.15f) else TextSecondary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "$clasesHoyCount clases hoy",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (clasesHoyCount > 0) StatusGreen else TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                diasOrden.forEach { dia ->
                    val clasesDelDia = horariosPorDia[dia] ?: emptyList()
                    val esHoy = dia.equals(diaHoy, ignoreCase = true)

                    item {
                        DiaHorarioCard(
                            dia = dia,
                            esHoy = esHoy,
                            clases = clasesDelDia
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiaHorarioCard(
    dia: String,
    esHoy: Boolean,
    clases: List<HorarioBloqueDto>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (esHoy) UnerbSky.copy(alpha = 0.08f) else UnerbSurface
        ),
        elevation = CardDefaults.cardElevation(if (esHoy) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dia.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (esHoy) UnerbElectricBlue else UnerbPrimaryNavy,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                if (esHoy) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = UnerbElectricBlue
                    ) {
                        Text(
                            text = "HOY",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (clases.isEmpty()) {
                Text(
                    text = "Sin clases programadas este día.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                clases.forEach { bloque ->
                    BloqueItemView(bloque = bloque)
                }
            }
        }
    }
}

@Composable
fun BloqueItemView(bloque: HorarioBloqueDto) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = UnerbSurface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de Bloque
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(UnerbPrimaryNavy),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B${bloque.bloque_numero}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bloque.materia_nombre,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = UnerbElectricBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = bloque.bloque_horario,
                        style = MaterialTheme.typography.bodyMedium,
                        color = UnerbElectricBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "${bloque.trayecto} • ${bloque.trimestre}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

