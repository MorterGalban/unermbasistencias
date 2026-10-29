const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const db = require('../database/db');
const { JWT_SECRET } = require('../middlewares/auth');
const { PENSUM_OFICIAL, BLOQUES_HORARIOS, DIAS_SEMANA } = require('../database/pensumData');

/**
 * Obtener catálogo oficial de materias del PNF en Informática (pensum2026.txt)
 */
const getMateriasCatalog = async (req, res) => {
  try {
    const materias = await db.query(`
      SELECT id, codigo, nombre_materia, trayecto, trimestre, pnf, horas_semanales
      FROM materias
      ORDER BY trayecto ASC, trimestre ASC, nombre_materia ASC
    `);

    res.json({
      success: true,
      materias
    });
  } catch (err) {
    console.error('Error al obtener materias:', err);
    res.status(500).json({
      success: false,
      error: 'Error al consultar las materias disponibles.'
    });
  }
};

/**
 * Obtener estructura jerárquica en cascada del pensum (Trayecto -> Trimestre -> Materias)
 * Guía fiel: pensum2026.txt
 */
const getEstructuraPensum = async (req, res) => {
  try {
    const materiasBd = await db.query(`
      SELECT id, codigo, nombre_materia, trayecto, trimestre, pnf
      FROM materias
      ORDER BY trayecto ASC, trimestre ASC, nombre_materia ASC
    `);

    // Construir estructura jerárquica
    const trayectosMap = new Map();

    for (const m of materiasBd) {
      if (!trayectosMap.has(m.trayecto)) {
        trayectosMap.set(m.trayecto, new Map());
      }
      const trimestresMap = trayectosMap.get(m.trayecto);
      if (!trimestresMap.has(m.trimestre)) {
        trimestresMap.set(m.trimestre, []);
      }
      trimestresMap.get(m.trimestre).push({
        id: m.id,
        codigo: m.codigo,
        nombre: m.nombre_materia,
        pnf: m.pnf
      });
    }

    const estructura = [];
    for (const [trayecto, trimestres] of trayectosMap.entries()) {
      const trimestresArr = [];
      for (const [trimestre, materias] of trimestres.entries()) {
        trimestresArr.push({
          nombre: trimestre,
          materias
        });
      }
      estructura.push({
        nombre: trayecto,
        trimestres: trimestresArr
      });
    }

    res.json({
      success: true,
      estructura,
      bloquesHorarios: BLOQUES_HORARIOS,
      diasSemana: DIAS_SEMANA
    });
  } catch (err) {
    console.error('Error al obtener estructura de pensum:', err);
    res.status(500).json({
      success: false,
      error: 'Error al obtener la estructura del pensum.'
    });
  }
};

/**
 * Registro de nuevo docente del PNF en Informática
 */
const registerDocente = async (req, res) => {
  const { nombre, apellido, cedula, email, password, materiasIds, fotoUrl } = req.sanitizedData;

  try {
    // 1. Hashear contraseña con bcrypt (factor de costo 12)
    const salt = bcrypt.genSaltSync(12);
    const passwordHash = bcrypt.hashSync(password, salt);

    // 2. Inserción del usuario en estado PENDIENTE
    const resultUsuario = await db.execute(
      `INSERT INTO usuarios (nombre, apellido, cedula, email, password_hash, foto_carnet_url, rol, estado)
       VALUES (?, ?, ?, ?, ?, ?, 'DOCENTE', 'PENDIENTE')`,
      [nombre, apellido, cedula, email, passwordHash, fotoUrl]
    );

    const nuevoUsuarioId = resultUsuario.insertId;

    // 3. Inserción de materias seleccionadas (1 a 5 materias)
    for (const materiaId of materiasIds) {
      await db.execute(
        `INSERT INTO docente_materias (usuario_id, materia_id) VALUES (?, ?)`,
        [nuevoUsuarioId, materiaId]
      );
    }

    // 4. Registro en el log del sistema
    await db.logEvent(
      nuevoUsuarioId,
      'REGISTRO_DOCENTE',
      `Nuevo docente registrado: ${nombre} ${apellido} (C.I. V-${cedula}) con ${materiasIds.length} materias. Estado: PENDIENTE.`,
      req
    );

    res.status(201).json({
      success: true,
      message: '¡Registro exitoso! Su solicitud ha sido enviada y se encuentra en estado PENDIENTE. No podrá iniciar sesión hasta que el Superroot (Coordinación PNF Informática) apruebe sus recaudos.',
      usuario: {
        id: nuevoUsuarioId,
        nombre,
        apellido,
        cedula,
        email,
        estado: 'PENDIENTE',
        materias_registradas: materiasIds.length
      }
    });
  } catch (err) {
    console.error('Error en registro:', err);
    res.status(500).json({
      success: false,
      error: 'Ocurrió un error al procesar el registro: ' + (err.message || 'Error interno')
    });
  }
};

/**
 * Inicio de sesión seguro
 */
const login = async (req, res) => {
  const { cleanIdentificador, cleanPassword } = req;

  try {
    // Buscar usuario por correo electrónico o por cédula
    const users = await db.query(
      `SELECT id, nombre, apellido, cedula, email, password_hash, foto_carnet_url, rol, estado
       FROM usuarios
       WHERE email = ? OR cedula = ?`,
      [cleanIdentificador, cleanIdentificador]
    );

    if (users.length === 0) {
      return res.status(401).json({
        success: false,
        error: 'Credenciales inválidas. Verifique su correo/cédula y contraseña.'
      });
    }

    const user = users[0];

    // Comparación segura de contraseña
    const passwordMatch = bcrypt.compareSync(cleanPassword, user.password_hash);
    if (!passwordMatch) {
      return res.status(401).json({
        success: false,
        error: 'Credenciales inválidas. Verifique su correo/cédula y contraseña.'
      });
    }

    // Comprobación de estado del usuario
    if (user.estado === 'PENDIENTE') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'PENDIENTE',
        error: 'Su cuenta está PENDIENTE de aprobación por la Coordinación del PNF en Informática. Por favor espere que el Superroot revise su foto y materias asignadas.'
      });
    }

    if (user.estado === 'RECHAZADO') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'RECHAZADO',
        error: 'Su solicitud de registro ha sido RECHAZADA. Contacte a la Coordinación del PNF en Informática.'
      });
    }

    if (user.estado === 'INACTIVO') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'INACTIVO',
        error: 'Su cuenta se encuentra actualmente INACTIVA. Contacte al Administrador.'
      });
    }

    // Generar Token JWT de sesión
    const tokenPayload = {
      id: user.id,
      nombre: user.nombre,
      apellido: user.apellido,
      email: user.email,
      cedula: user.cedula,
      rol: user.rol,
      estado: user.estado
    };

    const token = jwt.sign(tokenPayload, JWT_SECRET, { expiresIn: '12h' });

    // Obtener materias generales asignadas si es docente
    const materias = await db.query(
      `SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf
       FROM docente_materias dm
       JOIN materias m ON dm.materia_id = m.id
       WHERE dm.usuario_id = ?`,
      [user.id]
    );

    // Auditoría de inicio de sesión
    await db.logEvent(
      user.id,
      'LOGIN',
      `Inicio de sesión exitoso de ${user.nombre} ${user.apellido} (${user.rol}).`,
      req
    );

    res.json({
      success: true,
      message: 'Inicio de sesión exitoso.',
      token,
      usuario: {
        id: user.id,
        nombre: user.nombre,
        apellido: user.apellido,
        cedula: user.cedula,
        email: user.email,
        foto_carnet_url: user.foto_carnet_url,
        rol: user.rol,
        estado: user.estado,
        materias
      }
    });
  } catch (err) {
    console.error('Error en login:', err);
    res.status(500).json({
      success: false,
      error: 'Error interno en el servidor durante la autenticación.'
    });
  }
};

/**
 * Obtener perfil del usuario autenticado
 */
const getMe = async (req, res) => {
  try {
    const materias = await db.query(
      `SELECT m.id, m.codigo, m.nombre_materia, m.trayecto, m.trimestre, m.pnf
       FROM docente_materias dm
       JOIN materias m ON dm.materia_id = m.id
       WHERE dm.usuario_id = ?`,
      [req.user.id]
    );

    // Obtener datos frescos del usuario
    const [freshUser] = await db.query(
      `SELECT id, nombre, apellido, cedula, email, foto_carnet_url, rol, estado
       FROM usuarios WHERE id = ?`,
      [req.user.id]
    );

    res.json({
      success: true,
      usuario: {
        ...(freshUser || req.user),
        materias
      }
    });
  } catch (err) {
    console.error('Error en getMe:', err);
    res.status(500).json({
      success: false,
      error: 'Error al recuperar perfil.'
    });
  }
};

/**
 * Restablecer contraseña mediante Cédula y Correo
 */
const resetPassword = async (req, res) => {
  const { cedula, email, new_password, confirm_password } = req.body;

  const cleanCedula = String(cedula || '').trim();
  const cleanEmail = String(email || '').trim().toLowerCase();

  if (!cleanCedula || !cleanEmail || !new_password) {
    return res.status(400).json({
      success: false,
      error: 'Debe ingresar su Cédula, Correo Electrónico y la Nueva Contraseña.'
    });
  }

  if (new_password.length < 8) {
    return res.status(400).json({
      success: false,
      error: 'La nueva contraseña debe tener al menos 8 caracteres.'
    });
  }

  if (new_password !== confirm_password) {
    return res.status(400).json({
      success: false,
      error: 'La confirmación no coincide con la nueva contraseña.'
    });
  }

  try {
    const users = await db.query(
      'SELECT id, nombre, apellido, email, cedula FROM usuarios WHERE cedula = ? AND email = ?',
      [cleanCedula, cleanEmail]
    );

    if (users.length === 0) {
      return res.status(404).json({
        success: false,
        error: 'No se encontró ningún usuario con esa Cédula y Correo Electrónico coincidentes.'
      });
    }

    const user = users[0];
    const salt = bcrypt.genSaltSync(12);
    const passwordHash = bcrypt.hashSync(new_password, salt);

    await db.execute(
      'UPDATE usuarios SET password_hash = ? WHERE id = ?',
      [passwordHash, user.id]
    );

    await db.logEvent(
      user.id,
      'RESTABLECER_PASSWORD',
      `Contraseña restablecida exitosamente para ${user.nombre} ${user.apellido} (C.I. V-${user.cedula}).`,
      req
    );

    res.json({
      success: true,
      message: `¡Contraseña actualizada con éxito para ${user.nombre} ${user.apellido}! Ya puede iniciar sesión.`
    });
  } catch (err) {
    console.error('Error al restablecer contraseña:', err);
    res.status(500).json({
      success: false,
      error: 'Error interno al actualizar la contraseña.'
    });
  }
};

module.exports = {
  getMateriasCatalog,
  getEstructuraPensum,
  registerDocente,
  login,
  getMe,
  resetPassword
};
