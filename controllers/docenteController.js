const crypto = require('crypto');
const db = require('../database/db');
const { BLOQUES_HORARIOS, DIAS_SEMANA } = require('../database/pensumData');

const HMAC_SECRET = process.env.QR_HMAC_SECRET || 'UNERMB_PNF_HMAC_SECRET_KEY_ASISTENCIA_2026';

/**
 * Obtener el día de la semana actual en español con zona horaria de Venezuela (Lunes a Viernes)
 */
function getDiaSemanaActual(date = new Date()) {
  const weekdayStr = new Intl.DateTimeFormat('es-VE', {
    timeZone: 'America/Caracas',
    weekday: 'long'
  }).format(date);
  return weekdayStr.charAt(0).toUpperCase() + weekdayStr.slice(1);
}

/**
 * Convertir string de hora HH:MM:SS a minutos desde medianoche
 */
function timeToMinutes(timeStr) {
  if (!timeStr) return 0;
  const parts = timeStr.split(':');
  const h = parseInt(parts[0], 10) || 0;
  const m = parseInt(parts[1], 10) || 0;
  return h * 60 + m;
}

/**
 * Evaluar la puntualidad de un docente según su horario oficial asignado para el día de hoy
 */
function evaluarPuntualidad(horariosHoy, dateNow = new Date()) {
  if (!horariosHoy || horariosHoy.length === 0) {
    return {
      estado: 'SIN_HORARIO',
      minutosRetraso: 0,
      bloqueHorario: null,
      materiaNombre: null,
      mensaje: 'El docente no tiene clases programadas en su horario para el día de hoy.'
    };
  }

  // Hora exacta en la zona horaria legal de Venezuela (HLV / UTC-4)
  const horaActualStr = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'America/Caracas',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  }).format(dateNow);
  const minutosActuales = timeToMinutes(horaActualStr);

  // Buscar si cae dentro o cerca de algún bloque del día
  // Tolerancia de llegada a tiempo: 10 minutos de cortesía después de la hora de inicio
  const TOLERANCIA_MINUTOS = 10;

  let mejorBloque = null;
  let menorDistancia = Infinity;

  for (const h of horariosHoy) {
    const minInicio = timeToMinutes(h.hora_inicio);
    const minFin = timeToMinutes(h.hora_fin);

    // ¿Está dentro del rango de la clase (desde 20 min antes hasta el fin del bloque)?
    if (minutosActuales >= minInicio - 20 && minutosActuales <= minFin) {
      mejorBloque = h;
      break;
    }

    // Calcular distancia al inicio de este bloque
    const distancia = Math.abs(minutosActuales - minInicio);
    if (distancia < menorDistancia) {
      menorDistancia = distancia;
      mejorBloque = h;
    }
  }

  if (!mejorBloque) {
    mejorBloque = horariosHoy[0];
  }

  const minInicioBloque = timeToMinutes(mejorBloque.hora_inicio);
  const minFinBloque = timeToMinutes(mejorBloque.hora_fin);

  // Si marca antes o hasta la tolerancia de 10 min
  if (minutosActuales <= minInicioBloque + TOLERANCIA_MINUTOS) {
    return {
      estado: 'A_TIEMPO',
      minutosRetraso: 0,
      bloqueHorario: mejorBloque.bloque_horario,
      materiaNombre: mejorBloque.materia_nombre,
      mensaje: `Asistencia A TIEMPO para la clase "${mejorBloque.materia_nombre}" (${mejorBloque.bloque_horario}).`
    };
  }

  // Si marca después de la tolerancia pero antes de terminar el bloque
  if (minutosActuales > minInicioBloque + TOLERANCIA_MINUTOS && minutosActuales <= minFinBloque) {
    const retraso = minutosActuales - minInicioBloque;
    return {
      estado: 'TARDE',
      minutosRetraso: retraso,
      bloqueHorario: mejorBloque.bloque_horario,
      materiaNombre: mejorBloque.materia_nombre,
      mensaje: `Retardo detectado: Llegó con ${retraso} minutos de retraso a la clase "${mejorBloque.materia_nombre}" (${mejorBloque.bloque_horario}).`
    };
  }

  // Si marca muy alejado del horario de sus clases (más de 45 min fuera de bloque)
  return {
    estado: 'FUERA_DE_HORARIO',
    minutosRetraso: Math.max(0, minutosActuales - minInicioBloque),
    bloqueHorario: mejorBloque.bloque_horario,
    materiaNombre: mejorBloque.materia_nombre,
    mensaje: `Marcaje fuera de franja horaria para "${mejorBloque.materia_nombre}" (${mejorBloque.bloque_horario}).`
  };
}

/**
 * Procesar marcaje de asistencia mediante escaneo de Código QR
 * Detección automática de ENTRADA vs SALIDA y verificación estricta de puntualidad
 */
const registrarMarcajeQR = async (req, res) => {
  const { qr_payload } = req.body;
  const usuarioId = req.user.id;

  if (!qr_payload || typeof qr_payload !== 'string') {
    return res.status(400).json({
      success: false,
      error: 'Código QR no recibido o formato inválido.'
    });
  }

  try {
    // 1. Verificación de Ciberseguridad del Código QR Institucional
    let esValido = false;
    let qrData = null;

    try {
      qrData = JSON.parse(qr_payload);
      if (qrData.institucion === 'UNERMB' && qrData.codigo && qrData.codigo.includes('UNERMB-PNF-INFO')) {
        if (qrData.firma && qrData.nonce) {
          const hmac = crypto.createHmac('sha256', HMAC_SECRET);
          hmac.update(`${qrData.codigo}|${qrData.ts}|${qrData.nonce}`);
          const firmaCalculada = hmac.digest('hex');
          if (firmaCalculada === qrData.firma) {
            esValido = true;
          }
        } else {
          esValido = true;
        }
      }
    } catch {
      if (qr_payload.includes('UNERMB-PNF-INFO-OFICIAL') || qr_payload.includes('UNERMB-PNF-INFO')) {
        esValido = true;
      }
    }

    if (!esValido) {
      return res.status(403).json({
        success: false,
        error: 'Código QR inválido o no reconocido por el sistema oficial de la UNERMB PNF en Informática.'
      });
    }

    // 2. Control de tiempo mínimo entre marcajes (Protección contra doble escaneo accidental - 60s)
    const ultimosMarcajes = await db.query(`
      SELECT id, tipo, fecha_hora, TIMESTAMPDIFF(SECOND, fecha_hora, NOW()) as segundos_transcurridos
      FROM asistencias
      WHERE usuario_id = ?
      ORDER BY fecha_hora DESC
      LIMIT 1
    `, [usuarioId]);

    if (ultimosMarcajes.length > 0 && ultimosMarcajes[0].segundos_transcurridos !== null && ultimosMarcajes[0].segundos_transcurridos < 60) {
      const restantes = 60 - ultimosMarcajes[0].segundos_transcurridos;
      return res.status(429).json({
        success: false,
        error: `Acaba de registrar un marcaje de ${ultimosMarcajes[0].tipo}. Por favor espere ${restantes} segundos antes de volver a escanear.`
      });
    }

    // 3. Detección automática de ENTRADA o SALIDA del día actual
    const marcajesHoy = await db.query(`
      SELECT id, tipo, fecha_hora
      FROM asistencias
      WHERE usuario_id = ? AND DATE(fecha_hora) = CURDATE()
      ORDER BY fecha_hora DESC
      LIMIT 1
    `, [usuarioId]);

    let nuevoTipo = 'ENTRADA';
    if (marcajesHoy.length > 0) {
      nuevoTipo = marcajesHoy[0].tipo === 'ENTRADA' ? 'SALIDA' : 'ENTRADA';
    }

    // 4. Verificación de Horario y Control de Asistencia / Puntualidad
    const diaActual = getDiaSemanaActual();
    const horariosHoy = await db.query(`
      SELECT 
        id, dia_semana, bloque_numero, bloque_horario, hora_inicio, hora_fin, materia_nombre, trayecto, trimestre
      FROM horarios_docente
      WHERE docente_id = ? AND dia_semana = ?
      ORDER BY bloque_numero ASC
    `, [usuarioId, diaActual]);

    const evaluacion = nuevoTipo === 'ENTRADA'
      ? evaluarPuntualidad(horariosHoy, new Date())
      : {
          estado: 'A_TIEMPO',
          minutosRetraso: 0,
          bloqueHorario: horariosHoy.length > 0 ? horariosHoy[horariosHoy.length - 1].bloque_horario : null,
          materiaNombre: horariosHoy.length > 0 ? horariosHoy[horariosHoy.length - 1].materia_nombre : null,
          mensaje: 'Marcaje de SALIDA registrado satisfactoriamente.'
        };

    // 5. Inserción auditada del marcaje en la base de datos MySQL
    const clientIp = req.ip || req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '127.0.0.1';
    const userAgent = req.headers['user-agent'] || 'Dispositivo Móvil';
    const qrHash = crypto.createHash('sha256').update(qr_payload).digest('hex');

    const result = await db.execute(`
      INSERT INTO asistencias (
        usuario_id,
        tipo,
        metodo,
        ip_address,
        user_agent,
        qr_payload_hash,
        puntualidad_estado,
        minutos_retraso,
        bloque_horario_detectado,
        materia_detectada
      ) VALUES (?, ?, 'QR_SCANNER', ?, ?, ?, ?, ?, ?, ?)
    `, [
      usuarioId,
      nuevoTipo,
      clientIp,
      userAgent,
      qrHash,
      evaluacion.estado,
      evaluacion.minutosRetraso,
      evaluacion.bloqueHorario,
      evaluacion.materiaNombre
    ]);

    const [nuevoRegistro] = await db.query(`
      SELECT id, usuario_id, fecha_hora, tipo, puntualidad_estado, minutos_retraso, bloque_horario_detectado, materia_detectada
      FROM asistencias
      WHERE id = ?
    `, [result.insertId]);

    // Registrar en logs del sistema
    await db.logEvent(
      usuarioId,
      'MARCAJE_ASISTENCIA',
      `Marcaje de ${nuevoTipo} registrado por docente ${req.user.nombre} ${req.user.apellido} (${evaluacion.estado}${evaluacion.minutosRetraso > 0 ? ` con ${evaluacion.minutosRetraso} min retraso` : ''}).`,
      req
    );

    res.status(201).json({
      success: true,
      tipo: nuevoTipo,
      mensaje: `¡Marcaje de ${nuevoTipo} registrado con éxito!`,
      puntualidad: evaluacion,
      registro: nuevoRegistro,
      docente: {
        nombre: req.user.nombre,
        apellido: req.user.apellido,
        cedula: req.user.cedula
      }
    });
  } catch (err) {
    console.error('Error registrando marcaje:', err);
    res.status(500).json({
      success: false,
      error: 'Error interno al procesar el marcaje de asistencia: ' + err.message
    });
  }
};

/**
 * Historial personal del docente autenticado
 */
const getHistorialPersonal = async (req, res) => {
  const usuarioId = req.user.id;

  try {
    const marcajes = await db.query(`
      SELECT 
        id, fecha_hora, tipo, metodo, ip_address, puntualidad_estado, minutos_retraso, bloque_horario_detectado, materia_detectada
      FROM asistencias
      WHERE usuario_id = ?
      ORDER BY fecha_hora DESC
      LIMIT 100
    `, [usuarioId]);

    const totalEntradas = marcajes.filter(m => m.tipo === 'ENTRADA').length;
    const totalSalidas = marcajes.filter(m => m.tipo === 'SALIDA').length;
    const totalATiempo = marcajes.filter(m => m.puntualidad_estado === 'A_TIEMPO').length;
    const totalTarde = marcajes.filter(m => m.puntualidad_estado === 'TARDE').length;

    // Marcajes de hoy sincronizados estrictamente con la fecha legal de Venezuela (UTC-4)
    const hoyStr = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'America/Caracas',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(new Date());

    const marcajesHoy = marcajes.filter(m => {
      const d = new Intl.DateTimeFormat('en-CA', {
        timeZone: 'America/Caracas',
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
      }).format(new Date(m.fecha_hora));
      return d === hoyStr;
    });

    let estadoActual = 'SIN_MARCAJE';
    if (marcajesHoy.length > 0) {
      estadoActual = marcajesHoy[0].tipo === 'ENTRADA' ? 'PRESENTE' : 'JORNADA_FINALIZADA';
    }

    res.json({
      success: true,
      resumen: {
        total: marcajes.length,
        entradas: totalEntradas,
        salidas: totalSalidas,
        a_tiempo: totalATiempo,
        tarde: totalTarde,
        estadoActual,
        marcajesHoy: marcajesHoy.length
      },
      historial: marcajes
    });
  } catch (err) {
    console.error('Error al obtener historial personal:', err);
    res.status(500).json({
      success: false,
      error: 'Error al consultar historial: ' + err.message
    });
  }
};

/**
 * Materias generales declaradas por el docente autenticado
 */
const getMisMaterias = async (req, res) => {
  const usuarioId = req.user.id;
  try {
    const materias = await db.query(`
      SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf, m.horas_semanales
      FROM docente_materias dm
      JOIN materias m ON dm.materia_id = m.id
      WHERE dm.usuario_id = ?
      ORDER BY m.trayecto ASC, m.trimestre ASC
    `, [usuarioId]);

    res.json({
      success: true,
      total: materias.length,
      materias
    });
  } catch (err) {
    console.error('Error al obtener mis materias:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Actualizar las materias generales: RESERVADO EXCLUSIVAMENTE PARA SUPERROOT
 * Se bloquea la modificación directa por parte del docente.
 */
const actualizarMisMaterias = async (req, res) => {
  return res.status(403).json({
    success: false,
    error: 'Operación no permitida: La asignación y modificación de materias está reservada exclusivamente para la Coordinación del PNF en Informática (Superroot).'
  });
};

/**
 * Obtener el horario semanal asignado al docente autenticado
 * Requerimiento 2.B.2: Visualización de su horario semanal independiente asignado por el superroot
 */
const getMiHorario = async (req, res) => {
  const usuarioId = req.user.id;

  try {
    const horarios = await db.query(`
      SELECT 
        hd.id,
        hd.materia_id,
        hd.dia_semana,
        hd.bloque_numero,
        hd.bloque_horario,
        hd.hora_inicio,
        hd.hora_fin,
        hd.pnf,
        hd.trayecto,
        hd.trimestre,
        hd.materia_nombre,
        m.codigo as materia_codigo
      FROM horarios_docente hd
      JOIN materias m ON hd.materia_id = m.id
      WHERE hd.docente_id = ?
      ORDER BY 
        FIELD(hd.dia_semana, 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'),
        hd.bloque_numero ASC
    `, [usuarioId]);

    // Determinar día actual y clases de hoy
    const diaActual = getDiaSemanaActual();
    const clasesHoy = horarios.filter(h => h.dia_semana === diaActual);

    res.json({
      success: true,
      diaActual,
      clasesHoy,
      totalBloques: horarios.length,
      horarios,
      bloquesHorarios: BLOQUES_HORARIOS,
      diasSemana: DIAS_SEMANA
    });
  } catch (err) {
    console.error('Error al obtener mi horario:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

module.exports = {
  registrarMarcajeQR,
  getHistorialPersonal,
  getMisMaterias,
  actualizarMisMaterias,
  getMiHorario
};
