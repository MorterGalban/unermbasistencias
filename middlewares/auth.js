const jwt = require('jsonwebtoken');
const db = require('../database/db');

const JWT_SECRET = process.env.JWT_SECRET || 'UNERMB_PNF_INFORMATICA_SUPER_SECRET_KEY_2026_PROD';

/**
 * Middleware para verificar la validez del token JWT y el estado activo del usuario
 */
const verifyToken = async (req, res, next) => {
  const authHeader = req.headers['authorization'];
  let token = null;

  if (authHeader && authHeader.startsWith('Bearer ')) {
    token = authHeader.split(' ')[1];
  } else if (req.query && req.query.token) {
    token = req.query.token;
  }

  if (!token) {
    return res.status(401).json({
      success: false,
      error: 'Acceso no autorizado. Debe iniciar sesión para continuar.'
    });
  }

  try {
    const decoded = jwt.verify(token, JWT_SECRET);
    
    // Validar existencia y estado actual en base de datos MySQL en tiempo real
    const users = await db.query(
      `SELECT id, nombre, apellido, cedula, email, foto_carnet_url, rol, estado
       FROM usuarios WHERE id = ?`,
      [decoded.id]
    );

    if (!users || users.length === 0) {
      return res.status(401).json({
        success: false,
        error: 'Sesión no válida o usuario no encontrado.'
      });
    }

    const user = users[0];

    if (user.estado === 'PENDIENTE') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'PENDIENTE',
        error: 'Su cuenta está PENDIENTE de aprobación por el Superroot / Coordinación del PNF en Informática.'
      });
    }

    if (user.estado === 'RECHAZADO') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'RECHAZADO',
        error: 'Su solicitud de registro ha sido rechazada. Contacte a la Coordinación del PNF.'
      });
    }

    if (user.estado === 'INACTIVO') {
      return res.status(403).json({
        success: false,
        codigo_estado: 'INACTIVO',
        error: 'Su cuenta se encuentra inactiva en el sistema.'
      });
    }

    req.user = user;
    next();
  } catch (err) {
    return res.status(401).json({
      success: false,
      error: 'Sesión expirada o token inválido. Por favor inicie sesión nuevamente.'
    });
  }
};

/**
 * Exclusivo para Superroot (Administrador PNF Informática)
 */
const requireSuperroot = (req, res, next) => {
  if (!req.user || req.user.rol !== 'SUPERROOT') {
    return res.status(403).json({
      success: false,
      error: 'Acceso restringido: Se requieren permisos de Superroot (Coordinación PNF Informática).'
    });
  }
  next();
};

/**
 * Permite acceso a Docentes o Superroot
 */
const requireDocente = (req, res, next) => {
  if (!req.user || (req.user.rol !== 'DOCENTE' && req.user.rol !== 'SUPERROOT')) {
    return res.status(403).json({
      success: false,
      error: 'Acceso restringido: Solo docentes autorizados pueden realizar esta acción.'
    });
  }
  next();
};

module.exports = {
  verifyToken,
  requireSuperroot,
  requireDocente,
  JWT_SECRET
};
