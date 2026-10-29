const db = require('../database/db');

// Expresiones regulares de seguridad
const REGEX_ALFABETICO = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]+$/;
const REGEX_CEDULA = /^\d{1,9}$/;
const REGEX_EMAIL = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
// Contraseña: al menos 8 caracteres
const REGEX_PASSWORD = /^.{8,}$/;

/**
 * Middleware para validar el registro exhaustivo de docentes
 */
const validateRegistroDocente = async (req, res, next) => {
  const { nombre, apellido, cedula, email, password, confirm_password } = req.body;
  let materias = req.body.materias;

  // Normalizar array de materias (puede venir como array o string JSON)
  if (typeof materias === 'string') {
    try {
      materias = JSON.parse(materias);
    } catch {
      materias = [materias];
    }
  } else if (!Array.isArray(materias)) {
    materias = materias ? [materias] : [];
  }

  // 1. Validación de Nombres
  if (!nombre || typeof nombre !== 'string' || !REGEX_ALFABETICO.test(nombre.trim())) {
    return res.status(400).json({
      success: false,
      error: 'El campo "Nombre" solo debe contener caracteres alfabéticos y espacios (sin números ni símbolos).'
    });
  }
  if (nombre.trim().length < 2 || nombre.trim().length > 60) {
    return res.status(400).json({
      success: false,
      error: 'El nombre debe tener entre 2 y 60 caracteres.'
    });
  }

  // 2. Validación de Apellidos
  if (!apellido || typeof apellido !== 'string' || !REGEX_ALFABETICO.test(apellido.trim())) {
    return res.status(400).json({
      success: false,
      error: 'El campo "Apellido" solo debe contener caracteres alfabéticos y espacios (sin números ni símbolos).'
    });
  }
  if (apellido.trim().length < 2 || apellido.trim().length > 60) {
    return res.status(400).json({
      success: false,
      error: 'El apellido debe tener entre 2 y 60 caracteres.'
    });
  }

  // 3. Validación de Cédula de Identidad
  const cleanCedula = String(cedula || '').trim();
  if (!cleanCedula || !REGEX_CEDULA.test(cleanCedula)) {
    return res.status(400).json({
      success: false,
      error: 'La Cédula de Identidad debe ser estrictamente numérica y tener un máximo de 9 dígitos.'
    });
  }
  const numCedula = parseInt(cleanCedula, 10);
  if (numCedula <= 0 || numCedula > 999999999) {
    return res.status(400).json({
      success: false,
      error: 'La Cédula debe estar en el rango válido (máximo 999.999.999).'
    });
  }

  // 4. Validación de Correo Electrónico
  const cleanEmail = String(email || '').trim().toLowerCase();
  if (!cleanEmail || !REGEX_EMAIL.test(cleanEmail)) {
    return res.status(400).json({
      success: false,
      error: 'El Correo Electrónico no tiene un formato válido.'
    });
  }

  // 5. Validación de Contraseña (mínimo 8 caracteres)
  if (!password || typeof password !== 'string' || password.length < 8) {
    return res.status(400).json({
      success: false,
      error: 'La contraseña debe tener al menos 8 caracteres.'
    });
  }

  // 6. Confirmación de Contraseña
  if (password !== confirm_password) {
    return res.status(400).json({
      success: false,
      error: 'La confirmación de la contraseña no coincide con la contraseña ingresada.'
    });
  }

  // 7. Validación de Fotografía Tipo Carnet
  if (!req.file) {
    return res.status(400).json({
      success: false,
      error: 'La Fotografía Tipo Carnet es OBLIGATORIA para completar el registro docente.'
    });
  }

  // 8. Validación de Materias (Opcional en registro: Asignación reservada para Superroot; Máximo 5)
  let materiasUnicas = [];
  if (materias && materias.length > 0) {
    const cleanMaterias = materias.map(m => parseInt(m, 10)).filter(m => !isNaN(m) && m > 0);
    materiasUnicas = [...new Set(cleanMaterias)];

    if (materiasUnicas.length > 5) {
      return res.status(400).json({
        success: false,
        error: 'Límite superado: Un docente no puede registrar más de 5 materias como máximo.'
      });
    }
  }

  try {
    // 9. Comprobación de Unicidad en Base de Datos MySQL
    const existeCedula = await db.query('SELECT id FROM usuarios WHERE cedula = ?', [cleanCedula]);
    if (existeCedula.length > 0) {
      return res.status(409).json({
        success: false,
        error: 'La Cédula de Identidad ya se encuentra registrada en el sistema.'
      });
    }

    const existeEmail = await db.query('SELECT id FROM usuarios WHERE email = ?', [cleanEmail]);
    if (existeEmail.length > 0) {
      return res.status(409).json({
        success: false,
        error: 'El Correo Electrónico ya se encuentra registrado en el sistema.'
      });
    }

    // Pasar datos higienizados al controlador
    req.sanitizedData = {
      nombre: nombre.trim(),
      apellido: apellido.trim(),
      cedula: cleanCedula,
      email: cleanEmail,
      password: password,
      materiasIds: materiasUnicas,
      fotoUrl: `/uploads/carnet/${req.file.filename}`
    };

    next();
  } catch (err) {
    console.error('Error en validación de registro:', err);
    return res.status(500).json({
      success: false,
      error: 'Error interno en la base de datos durante la validación.'
    });
  }
};

/**
 * Validación de credenciales de inicio de sesión
 */
const validateLogin = (req, res, next) => {
  const { identificador, password } = req.body;
  if (!identificador || !password) {
    return res.status(400).json({
      success: false,
      error: 'Debe ingresar su Correo o Cédula y su Contraseña.'
    });
  }
  req.cleanIdentificador = String(identificador).trim().toLowerCase();
  req.cleanPassword = String(password);
  next();
};

module.exports = {
  validateRegistroDocente,
  validateLogin,
  REGEX_ALFABETICO,
  REGEX_CEDULA,
  REGEX_EMAIL,
  REGEX_PASSWORD
};
