const crypto = require('crypto');
const QRCode = require('qrcode');
const db = require('../database/db');
const { BLOQUES_HORARIOS, DIAS_SEMANA } = require('../database/pensumData');

const HMAC_SECRET = process.env.QR_HMAC_SECRET || 'UNERMB_PNF_HMAC_SECRET_KEY_ASISTENCIA_2026';

/**
 * Obtener estadísticas globales para el Dashboard del Superroot
 */
const getEstadisticasDashboard = async (req, res) => {
  try {
    const [totalDocentesRow] = await db.query(`SELECT COUNT(*) as count FROM usuarios WHERE rol = 'DOCENTE'`);
    const [pendientesRow] = await db.query(`SELECT COUNT(*) as count FROM usuarios WHERE rol = 'DOCENTE' AND estado = 'PENDIENTE'`);
    const [activosRow] = await db.query(`SELECT COUNT(*) as count FROM usuarios WHERE rol = 'DOCENTE' AND estado = 'ACTIVO'`);
    const [horariosAsignadosRow] = await db.query(`SELECT COUNT(*) as count FROM horarios_docente`);

    // Marcajes de hoy
    const [marcajesHoy] = await db.query(`
      SELECT 
        COUNT(*) as total,
        COALESCE(SUM(CASE WHEN tipo = 'ENTRADA' THEN 1 ELSE 0 END), 0) as entradas,
        COALESCE(SUM(CASE WHEN tipo = 'SALIDA' THEN 1 ELSE 0 END), 0) as salidas,
        COALESCE(SUM(CASE WHEN puntualidad_estado = 'A_TIEMPO' THEN 1 ELSE 0 END), 0) as a_tiempo,
        COALESCE(SUM(CASE WHEN puntualidad_estado = 'TARDE' THEN 1 ELSE 0 END), 0) as tarde
      FROM asistencias
      WHERE DATE(fecha_hora) = CURDATE()
    `);

    res.json({
      success: true,
      stats: {
        totalDocentes: totalDocentesRow.count,
        pendientes: pendientesRow.count,
        activos: activosRow.count,
        horariosAsignados: horariosAsignadosRow.count,
        marcajesHoy: {
          total: parseInt(marcajesHoy.total, 10) || 0,
          entradas: parseInt(marcajesHoy.entradas, 10) || 0,
          salidas: parseInt(marcajesHoy.salidas, 10) || 0,
          a_tiempo: parseInt(marcajesHoy.a_tiempo, 10) || 0,
          tarde: parseInt(marcajesHoy.tarde, 10) || 0
        }
      }
    });
  } catch (err) {
    console.error('Error en getEstadisticasDashboard:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Obtener solicitudes PENDIENTES con fotografía carnet y materias seleccionadas
 */
const getSolicitudesPendientes = async (req, res) => {
  try {
    const docentesPendientes = await db.query(`
      SELECT id, nombre, apellido, cedula, email, foto_carnet_url, estado, created_at
      FROM usuarios
      WHERE rol = 'DOCENTE' AND estado = 'PENDIENTE'
      ORDER BY created_at DESC
    `);

    // Obtener las materias de cada docente
    const resultados = await Promise.all(
      docentesPendientes.map(async (docente) => {
        const materias = await db.query(`
          SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf
          FROM docente_materias dm
          JOIN materias m ON dm.materia_id = m.id
          WHERE dm.usuario_id = ?
        `, [docente.id]);

        return {
          ...docente,
          materias
        };
      })
    );

    res.json({
      success: true,
      solicitudes: resultados
    });
  } catch (err) {
    console.error('Error al obtener solicitudes pendientes:', err);
    res.status(500).json({ success: false, error: 'Error al obtener solicitudes: ' + err.message });
  }
};

/**
 * Aprobar solicitud docente (pasa a ACTIVO)
 */
const aprobarSolicitud = async (req, res) => {
  const { id } = req.params;
  try {
    const rows = await db.query(`SELECT id, nombre, apellido, estado FROM usuarios WHERE id = ? AND rol = 'DOCENTE'`, [id]);
    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }
    const docente = rows[0];

    await db.execute(`
      UPDATE usuarios
      SET estado = 'ACTIVO'
      WHERE id = ?
    `, [id]);

    await db.logEvent(
      req.user.id,
      'APROBAR_DOCENTE',
      `Solicitud aprobada: Docente ${docente.nombre} ${docente.apellido} (ID: ${id}) pasó a estado ACTIVO.`,
      req
    );

    res.json({
      success: true,
      message: `La solicitud del docente ${docente.nombre} ${docente.apellido} ha sido APROBADA exitosamente. Ahora puede iniciar sesión y registrar asistencia.`
    });
  } catch (err) {
    console.error('Error al aprobar solicitud:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Rechazar solicitud docente
 */
const rechazarSolicitud = async (req, res) => {
  const { id } = req.params;
  try {
    const rows = await db.query(`SELECT id, nombre, apellido, estado FROM usuarios WHERE id = ? AND rol = 'DOCENTE'`, [id]);
    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }
    const docente = rows[0];

    await db.execute(`
      UPDATE usuarios
      SET estado = 'RECHAZADO'
      WHERE id = ?
    `, [id]);

    await db.logEvent(
      req.user.id,
      'RECHAZAR_DOCENTE',
      `Solicitud rechazada: Docente ${docente.nombre} ${docente.apellido} (ID: ${id}) pasó a estado RECHAZADO.`,
      req
    );

    res.json({
      success: true,
      message: `La solicitud del docente ${docente.nombre} ${docente.apellido} ha sido RECHAZADA.`
    });
  } catch (err) {
    console.error('Error al rechazar solicitud:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Listado general de docentes con sus materias y fotografía
 */
const getDocentes = async (req, res) => {
  const { estado, buscar } = req.query;
  try {
    let sql = `
      SELECT id, nombre, apellido, cedula, email, foto_carnet_url, estado, rol, created_at
      FROM usuarios
      WHERE 1=1
    `;
    const params = [];

    // Por defecto lista DOCENTES a menos que se pida todo
    if (req.query.incluir_admins !== 'true') {
      sql += ` AND rol = 'DOCENTE'`;
    }

    if (estado && ['ACTIVO', 'INACTIVO', 'PENDIENTE', 'RECHAZADO'].includes(estado)) {
      sql += ` AND estado = ?`;
      params.push(estado);
    }

    if (buscar && buscar.trim() !== '') {
      sql += ` AND (nombre LIKE ? OR apellido LIKE ? OR cedula LIKE ? OR email LIKE ?)`;
      const term = `%${buscar.trim()}%`;
      params.push(term, term, term, term);
    }

    sql += ` ORDER BY apellido ASC, nombre ASC`;

    const docentes = await db.query(sql, params);

    const resultado = await Promise.all(
      docentes.map(async (doc) => {
        const materias = await db.query(`
          SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf
          FROM docente_materias dm
          JOIN materias m ON dm.materia_id = m.id
          WHERE dm.usuario_id = ?
        `, [doc.id]);

        const [horariosCount] = await db.query(`
          SELECT COUNT(*) as total_bloques FROM horarios_docente WHERE docente_id = ?
        `, [doc.id]);

        return {
          ...doc,
          materias,
          total_bloques_horario: horariosCount.total_bloques || 0
        };
      })
    );

    res.json({
      success: true,
      docentes: resultado
    });
  } catch (err) {
    console.error('Error al obtener docentes:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Cambiar estado de un docente (Activar / Desactivar)
 */
const cambiarEstadoDocente = async (req, res) => {
  const { id } = req.params;
  const { nuevo_estado } = req.body;

  if (!['ACTIVO', 'INACTIVO', 'RECHAZADO', 'PENDIENTE'].includes(nuevo_estado)) {
    return res.status(400).json({ success: false, error: 'Estado solicitado no válido.' });
  }

  try {
    const result = await db.execute(`
      UPDATE usuarios
      SET estado = ?
      WHERE id = ? AND rol = 'DOCENTE'
    `, [nuevo_estado, id]);

    if (result.affectedRows === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }

    await db.logEvent(
      req.user.id,
      'CAMBIAR_ESTADO_DOCENTE',
      `Estado del docente ID ${id} actualizado a ${nuevo_estado}.`,
      req
    );

    res.json({
      success: true,
      message: `El estado del docente ha sido actualizado a ${nuevo_estado}.`
    });
  } catch (err) {
    console.error('Error al cambiar estado docente:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Eliminar usuario directamente (Superroot)
 * Requerimiento 2.A.1: Capacidad de eliminarlos directamente
 */
const eliminarUsuario = async (req, res) => {
  const { id } = req.params;
  const usuarioId = parseInt(id, 10);

  if (isNaN(usuarioId)) {
    return res.status(400).json({ success: false, error: 'ID de usuario inválido.' });
  }

  if (req.user.id === usuarioId) {
    return res.status(400).json({ success: false, error: 'Operación no permitida: No puede eliminar su propia cuenta de Superroot en uso.' });
  }

  try {
    const users = await db.query('SELECT id, nombre, apellido, cedula, email, rol FROM usuarios WHERE id = ?', [usuarioId]);
    if (users.length === 0) {
      return res.status(404).json({ success: false, error: 'Usuario no encontrado.' });
    }

    const usuario = users[0];

    // Si es superroot, evitar que se quede sin ningún superroot
    if (usuario.rol === 'SUPERROOT') {
      const [countSuper] = await db.query("SELECT COUNT(*) as count FROM usuarios WHERE rol = 'SUPERROOT'");
      if (countSuper.count <= 1) {
        return res.status(400).json({ success: false, error: 'No se puede eliminar el único Superroot del sistema.' });
      }
    }

    // Ejecutar eliminación (Las llaves foráneas con ON DELETE CASCADE limpian materias, horarios y asistencias)
    await db.execute('DELETE FROM usuarios WHERE id = ?', [usuarioId]);

    await db.logEvent(
      req.user.id,
      'ELIMINAR_USUARIO',
      `Usuario eliminado permanentemente: ${usuario.nombre} ${usuario.apellido} (C.I. V-${usuario.cedula}, Email: ${usuario.email}, Rol: ${usuario.rol}).`,
      req
    );

    res.json({
      success: true,
      message: `El usuario ${usuario.nombre} ${usuario.apellido} ha sido eliminado definitivamente del sistema.`
    });
  } catch (err) {
    console.error('Error al eliminar usuario:', err);
    res.status(500).json({ success: false, error: 'Error al eliminar usuario: ' + err.message });
  }
};

/**
 * Control de Logs de Actividad del Sistema (Superroot)
 * Requerimiento 2.A.2: Apartado visual para consultar los registros de actividad del sistema
 */
const getSystemLogs = async (req, res) => {
  const { tipo_evento, fecha, buscar } = req.query;

  try {
    let sql = `
      SELECT 
        l.id,
        l.usuario_id,
        u.nombre,
        u.apellido,
        u.cedula,
        u.rol,
        l.tipo_evento,
        l.descripcion,
        l.ip_address,
        l.user_agent,
        l.created_at
      FROM system_logs l
      LEFT JOIN usuarios u ON l.usuario_id = u.id
      WHERE 1=1
    `;
    const params = [];

    if (tipo_evento && tipo_evento.trim() !== '') {
      sql += ` AND l.tipo_evento = ?`;
      params.push(tipo_evento.trim());
    }

    if (fecha && fecha.trim() !== '') {
      sql += ` AND DATE(l.created_at) = ?`;
      params.push(fecha.trim());
    }

    if (buscar && buscar.trim() !== '') {
      sql += ` AND (l.descripcion LIKE ? OR u.nombre LIKE ? OR u.apellido LIKE ? OR u.cedula LIKE ?)`;
      const term = `%${buscar.trim()}%`;
      params.push(term, term, term, term);
    }

    sql += ` ORDER BY l.created_at DESC LIMIT 300`;

    const logs = await db.query(sql, params);

    res.json({
      success: true,
      total: logs.length,
      logs
    });
  } catch (err) {
    console.error('Error al consultar logs del sistema:', err);
    res.status(500).json({ success: false, error: 'Error al consultar logs: ' + err.message });
  }
};

/**
 * Obtener el horario asignado a un docente específico
 * Requerimiento 2.A.3: Acceso al perfil individual de cada docente para asignarle su horario
 */
const getDocenteHorario = async (req, res) => {
  const { id } = req.params;
  const docenteId = parseInt(id, 10);

  try {
    const docentes = await db.query(
      `SELECT id, nombre, apellido, cedula, email, foto_carnet_url, estado FROM usuarios WHERE id = ? AND rol = 'DOCENTE'`,
      [docenteId]
    );

    if (docentes.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }

    const docente = docentes[0];

    // Materias generales del docente
    const materiasGenerales = await db.query(`
      SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf
      FROM docente_materias dm
      JOIN materias m ON dm.materia_id = m.id
      WHERE dm.usuario_id = ?
    `, [docenteId]);

    // Horarios asignados ordenados por día y bloque
    const horarios = await db.query(`
      SELECT 
        hd.id,
        hd.docente_id,
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
        m.codigo as materia_codigo,
        hd.created_at
      FROM horarios_docente hd
      JOIN materias m ON hd.materia_id = m.id
      WHERE hd.docente_id = ?
      ORDER BY 
        FIELD(hd.dia_semana, 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'),
        hd.bloque_numero ASC
    `, [docenteId]);

    res.json({
      success: true,
      docente,
      materiasGenerales,
      horarios,
      bloquesHorariosDisponibles: BLOQUES_HORARIOS,
      diasSemanaDisponibles: DIAS_SEMANA
    });
  } catch (err) {
    console.error('Error al obtener horario del docente:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Asignar un bloque de clase al horario de un docente
 * Requerimiento 2.A.3: Sistema de selección en cascada guiado fielmente por pensum2026.txt
 */
const asignarDocenteHorario = async (req, res) => {
  const { id } = req.params;
  const docenteId = parseInt(id, 10);
  const { materia_id, dia_semana, bloque_numero } = req.body;

  if (!materia_id || !dia_semana || !bloque_numero) {
    return res.status(400).json({
      success: false,
      error: 'Debe especificar la Materia (vía cascada de pensum), el Día de la semana y el Bloque Horario.'
    });
  }

  if (!DIAS_SEMANA.includes(dia_semana)) {
    return res.status(400).json({
      success: false,
      error: `Día inválido. Los días hábiles permitidos son: ${DIAS_SEMANA.join(', ')}.`
    });
  }

  const bloqueNum = parseInt(bloque_numero, 10);
  const bloqueConfig = BLOQUES_HORARIOS.find(b => b.numero === bloqueNum);
  if (!bloqueConfig) {
    return res.status(400).json({
      success: false,
      error: 'Bloque horario inválido. Debe ser un número del 1 al 5 correspondiente a las franjas horarias oficiales.'
    });
  }

  try {
    // 1. Verificar existencia del docente
    const docentes = await db.query('SELECT id, nombre, apellido FROM usuarios WHERE id = ? AND rol = "DOCENTE"', [docenteId]);
    if (docentes.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }
    const docente = docentes[0];

    // 2. Verificar existencia de la materia (estricta del pensum)
    const materias = await db.query(
      'SELECT id, codigo, nombre_materia, trayecto, trimestre, pnf FROM materias WHERE id = ?',
      [parseInt(materia_id, 10)]
    );
    if (materias.length === 0) {
      return res.status(404).json({ success: false, error: 'Materia no encontrada en el pensum oficial.' });
    }
    const materia = materias[0];

    // 3. Validar conflicto de horario: ¿El docente ya tiene una clase asignada en ese día y bloque?
    const conflicto = await db.query(`
      SELECT id, materia_nombre, bloque_horario
      FROM horarios_docente
      WHERE docente_id = ? AND dia_semana = ? AND bloque_numero = ?
    `, [docenteId, dia_semana, bloqueNum]);

    if (conflicto.length > 0) {
      return res.status(409).json({
        success: false,
        error: `Conflicto de horario: El docente ya tiene asignada la materia "${conflicto[0].materia_nombre}" el ${dia_semana} en el bloque ${conflicto[0].bloque_horario}.`
      });
    }

    // 4. Insertar asignación de horario con todos los metadatos requeridos
    const result = await db.execute(`
      INSERT INTO horarios_docente (
        docente_id,
        materia_id,
        dia_semana,
        bloque_numero,
        bloque_horario,
        hora_inicio,
        hora_fin,
        pnf,
        trayecto,
        trimestre,
        materia_nombre
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `, [
      docenteId,
      materia.id,
      dia_semana,
      bloqueNum,
      bloqueConfig.etiqueta,
      bloqueConfig.hora_inicio,
      bloqueConfig.hora_fin,
      materia.pnf || 'Informática',
      materia.trayecto,
      materia.trimestre,
      materia.nombre_materia
    ]);

    // Asegurar que la materia quede también en docente_materias si no estaba
    await db.execute(`
      INSERT INTO docente_materias (usuario_id, materia_id)
      VALUES (?, ?)
      ON DUPLICATE KEY UPDATE usuario_id = VALUES(usuario_id)
    `, [docenteId, materia.id]);

    await db.logEvent(
      req.user.id,
      'ASIGNAR_HORARIO',
      `Horario asignado a ${docente.nombre} ${docente.apellido}: ${materia.nombre_materia} (${materia.trayecto} - ${materia.trimestre}) el ${dia_semana} (${bloqueConfig.etiqueta}).`,
      req
    );

    res.status(201).json({
      success: true,
      message: `¡Horario asignado exitosamente! ${materia.nombre_materia} los ${dia_semana} en el bloque ${bloqueConfig.etiqueta}.`,
      horarioId: result.insertId
    });
  } catch (err) {
    console.error('Error al asignar horario:', err);
    res.status(500).json({ success: false, error: 'Error al asignar horario: ' + err.message });
  }
};

/**
 * Eliminar una asignación de horario de un docente
 */
const eliminarDocenteHorario = async (req, res) => {
  const { horario_id } = req.params;
  const hId = parseInt(horario_id, 10);

  try {
    const rows = await db.query(`
      SELECT hd.id, hd.docente_id, hd.materia_nombre, hd.dia_semana, hd.bloque_horario, u.nombre, u.apellido
      FROM horarios_docente hd
      JOIN usuarios u ON hd.docente_id = u.id
      WHERE hd.id = ?
    `, [hId]);

    if (rows.length === 0) {
      return res.status(404).json({ success: false, error: 'Asignación de horario no encontrada.' });
    }

    const reg = rows[0];

    await db.execute('DELETE FROM horarios_docente WHERE id = ?', [hId]);

    await db.logEvent(
      req.user.id,
      'ELIMINAR_HORARIO',
      `Bloque de horario eliminado de ${reg.nombre} ${reg.apellido}: ${reg.materia_nombre} el ${reg.dia_semana} (${reg.bloque_horario}).`,
      req
    );

    res.json({
      success: true,
      message: 'Bloque de horario removido exitosamente.'
    });
  } catch (err) {
    console.error('Error al eliminar horario:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

/**
 * Asignar materia general a un docente (Exclusivo Superroot)
 */
const asignarMateriaDocente = async (req, res) => {
  const { id } = req.params;
  const docenteId = parseInt(id, 10);
  const { materia_id } = req.body;

  if (isNaN(docenteId) || !materia_id) {
    return res.status(400).json({ success: false, error: 'Docente y Materia requeridos.' });
  }

  const mId = parseInt(materia_id, 10);

  try {
    const docentes = await db.query('SELECT id, nombre, apellido FROM usuarios WHERE id = ? AND rol = "DOCENTE"', [docenteId]);
    if (docentes.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }
    const docente = docentes[0];

    const materias = await db.query('SELECT id, nombre_materia, codigo, trayecto, trimestre FROM materias WHERE id = ?', [mId]);
    if (materias.length === 0) {
      return res.status(404).json({ success: false, error: 'Materia no encontrada en el pensum oficial.' });
    }
    const materia = materias[0];

    // Verificar cantidad actual de materias
    const materiasActuales = await db.query('SELECT COUNT(*) as total FROM docente_materias WHERE usuario_id = ?', [docenteId]);
    if (materiasActuales[0].total >= 5) {
      return res.status(400).json({
        success: false,
        error: 'Límite alcanzado: El docente ya tiene asignadas 5 materias como máximo permitido.'
      });
    }

    // Insertar si no existe
    await db.execute(
      'INSERT INTO docente_materias (usuario_id, materia_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE usuario_id = VALUES(usuario_id)',
      [docenteId, mId]
    );

    await db.logEvent(
      req.user.id,
      'ASIGNAR_MATERIA_DOCENTE',
      `Materia asignada a ${docente.nombre} ${docente.apellido}: ${materia.nombre_materia} (${materia.trayecto} - ${materia.trimestre}).`,
      req
    );

    res.status(201).json({
      success: true,
      message: `¡Materia "${materia.nombre_materia}" asignada con éxito al docente!`,
      materia
    });
  } catch (err) {
    console.error('Error al asignar materia:', err);
    res.status(500).json({ success: false, error: 'Error al asignar materia: ' + err.message });
  }
};

/**
 * Eliminar materia general de un docente (Exclusivo Superroot)
 */
const eliminarMateriaDocente = async (req, res) => {
  const { id, materia_id } = req.params;
  const docenteId = parseInt(id, 10);
  const mId = parseInt(materia_id, 10);

  if (isNaN(docenteId) || isNaN(mId)) {
    return res.status(400).json({ success: false, error: 'Parámetros inválidos.' });
  }

  try {
    const docentes = await db.query('SELECT id, nombre, apellido FROM usuarios WHERE id = ? AND rol = "DOCENTE"', [docenteId]);
    if (docentes.length === 0) {
      return res.status(404).json({ success: false, error: 'Docente no encontrado.' });
    }
    const docente = docentes[0];

    const materiaRows = await db.query('SELECT nombre_materia FROM materias WHERE id = ?', [mId]);
    const nombreMateria = materiaRows.length > 0 ? materiaRows[0].nombre_materia : `ID ${mId}`;

    await db.execute('DELETE FROM docente_materias WHERE usuario_id = ? AND materia_id = ?', [docenteId, mId]);
    // Eliminar también bloques de horario asociados a esa materia para ese docente
    await db.execute('DELETE FROM horarios_docente WHERE docente_id = ? AND materia_id = ?', [docenteId, mId]);

    await db.logEvent(
      req.user.id,
      'ELIMINAR_MATERIA_DOCENTE',
      `Materia removida de ${docente.nombre} ${docente.apellido}: ${nombreMateria} (y sus bloques de horario asociados).`,
      req
    );

    res.json({
      success: true,
      message: `Materia "${nombreMateria}" removida exitosamente del perfil del docente.`
    });
  } catch (err) {
    console.error('Error al eliminar materia:', err);
    res.status(500).json({ success: false, error: 'Error al eliminar materia: ' + err.message });
  }
};

/**
 * Generador y visualizador de Código QR Institucional Oficial
 * Utiliza HMAC SHA-256 para firmar el payload y prevenir falsificaciones
 */
const generarQROficial = async (req, res) => {
  try {
    let tokens = await db.query(
      `SELECT id, token_codigo, firma_hmac, valido_hasta, activo
       FROM qr_tokens_seguridad
       WHERE activo = 1 AND valido_hasta > NOW()
       ORDER BY id DESC LIMIT 1`
    );

    let tokenActivo;
    if (!tokens || tokens.length === 0) {
      const nuevoCodigo = 'UNERMB-PNF-INFO-OFICIAL-SEC2026';
      const nuevaFirma = 'HMAC_SHA256_UNERMB_PNF_COORDINACION_KEY_2026_VALID';
      const insertResult = await db.execute(
        `INSERT INTO qr_tokens_seguridad (token_codigo, firma_hmac, valido_hasta, activo, creado_por)
         VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 365 DAY), 1, ?)`,
        [nuevoCodigo, nuevaFirma, req.user?.id || null]
      );
      tokens = await db.query('SELECT id, token_codigo, firma_hmac, valido_hasta, activo FROM qr_tokens_seguridad WHERE id = ?', [insertResult.insertId]);
      tokenActivo = tokens[0];
    } else {
      tokenActivo = tokens[0];
    }

    const timestamp = Date.now();
    const nonce = crypto.randomBytes(8).toString('hex');
    const hmac = crypto.createHmac('sha256', HMAC_SECRET);
    hmac.update(`${tokenActivo.token_codigo}|${timestamp}|${nonce}`);
    const firma = hmac.digest('hex');

    const qrPayload = JSON.stringify({
      institucion: 'UNERMB',
      programa: 'PNF EN INFORMÁTICA',
      codigo: tokenActivo.token_codigo,
      ts: timestamp,
      nonce: nonce,
      firma: firma,
      valido_hasta: tokenActivo.valido_hasta
    });

    const qrDataURL = await QRCode.toDataURL(qrPayload, {
      errorCorrectionLevel: 'H',
      margin: 2,
      width: 360,
      color: {
        dark: '#002855', // Azul institucional UNERMB profundo
        light: '#FFFFFF'
      }
    });

    res.json({
      success: true,
      qrDataUrl: qrDataURL,
      qrDataURL: qrDataURL,
      token: tokenActivo.token_codigo,
      valido_hasta: tokenActivo.valido_hasta,
      qrPayload,
      info: {
        institucion: 'UNIVERSIDAD NACIONAL EXPERIMENTAL "RAFAEL MARÍA BARALT"',
        programa: 'PNF EN INFORMÁTICA',
        ubicacion: 'Coordinación Docente / Punto Central de Marcaje',
        valido_hasta: tokenActivo.valido_hasta
      }
    });
  } catch (err) {
    console.error('Error generando QR:', err);
    res.status(500).json({ success: false, error: 'Error al generar el Código QR Oficial: ' + err.message });
  }
};

/**
 * Renovar o regenerar el Token QR oficial (Superroot)
 */
const renovarQROficial = async (req, res) => {
  try {
    await db.execute(`UPDATE qr_tokens_seguridad SET activo = 0 WHERE activo = 1`);

    const nuevoCodigo = `UNERMB-PNF-INFO-${crypto.randomBytes(4).toString('hex').toUpperCase()}-2026`;
    const nuevaFirma = crypto.randomBytes(16).toString('hex');

    await db.execute(
      `INSERT INTO qr_tokens_seguridad (token_codigo, firma_hmac, valido_hasta, activo, creado_por)
       VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 365 DAY), 1, ?)`,
      [nuevoCodigo, nuevaFirma, req.user?.id || null]
    );

    await db.logEvent(
      req.user?.id,
      'RENOVAR_QR',
      `Nuevo Token QR Institucional generado: ${nuevoCodigo}`,
      req
    );

    return generarQROficial(req, res);
  } catch (err) {
    console.error('Error al renovar QR:', err);
    res.status(500).json({ success: false, error: 'Error al renovar QR: ' + err.message });
  }
};

/**
 * Reporte y registros de asistencias con filtros y verificación de puntualidad
 */
const getAsistenciasLogs = async (req, res) => {
  const { fecha, docente_id, tipo, puntualidad } = req.query;

  try {
    let sql = `
      SELECT 
        a.id, 
        a.usuario_id, 
        u.nombre, 
        u.apellido, 
        u.cedula, 
        u.email, 
        u.foto_carnet_url,
        a.fecha_hora, 
        a.tipo, 
        a.metodo, 
        a.ip_address,
        a.puntualidad_estado,
        a.minutos_retraso,
        a.bloque_horario_detectado,
        a.materia_detectada
      FROM asistencias a
      JOIN usuarios u ON a.usuario_id = u.id
      WHERE 1=1
    `;
    const params = [];

    if (fecha && fecha.trim() !== '') {
      sql += ` AND DATE(a.fecha_hora) = ?`;
      params.push(fecha.trim());
    }

    if (docente_id && docente_id.trim() !== '') {
      sql += ` AND a.usuario_id = ?`;
      params.push(parseInt(docente_id, 10));
    }

    if (tipo && ['ENTRADA', 'SALIDA'].includes(tipo)) {
      sql += ` AND a.tipo = ?`;
      params.push(tipo);
    }

    if (puntualidad && ['A_TIEMPO', 'TARDE', 'FUERA_DE_HORARIO', 'SIN_HORARIO'].includes(puntualidad)) {
      sql += ` AND a.puntualidad_estado = ?`;
      params.push(puntualidad);
    }

    sql += ` ORDER BY a.fecha_hora DESC LIMIT 250`;

    const logs = await db.query(sql, params);

    // Contadores del filtro
    const totalEntradas = logs.filter(l => l.tipo === 'ENTRADA').length;
    const totalSalidas = logs.filter(l => l.tipo === 'SALIDA').length;
    const totalATiempo = logs.filter(l => l.puntualidad_estado === 'A_TIEMPO').length;
    const totalTarde = logs.filter(l => l.puntualidad_estado === 'TARDE').length;

    res.json({
      success: true,
      resumen: {
        total: logs.length,
        entradas: totalEntradas,
        salidas: totalSalidas,
        a_tiempo: totalATiempo,
        tarde: totalTarde
      },
      logs
    });
  } catch (err) {
    console.error('Error al obtener logs de asistencia:', err);
    res.status(500).json({ success: false, error: err.message });
  }
};

module.exports = {
  getEstadisticasDashboard,
  getSolicitudesPendientes,
  aprobarSolicitud,
  rechazarSolicitud,
  getDocentes,
  cambiarEstadoDocente,
  eliminarUsuario,
  getSystemLogs,
  getDocenteHorario,
  asignarDocenteHorario,
  eliminarDocenteHorario,
  asignarMateriaDocente,
  eliminarMateriaDocente,
  generarQROficial,
  renovarQROficial,
  getAsistenciasLogs
};
