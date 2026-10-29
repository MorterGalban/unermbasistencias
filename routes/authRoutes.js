const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const { handleCarnetUpload } = require('../middlewares/upload');
const { validateRegistroDocente, validateLogin } = require('../middlewares/validators');
const { verifyToken } = require('../middlewares/auth');

// Obtener catálogo oficial plano de materias
router.get('/materias', authController.getMateriasCatalog);

// Obtener estructura jerárquica en cascada oficial (pensum2026.txt)
router.get('/pensum-estructura', authController.getEstructuraPensum);

// Registro de docente con foto tipo carnet y validaciones estrictas
router.post('/registro', handleCarnetUpload, validateRegistroDocente, authController.registerDocente);

// Inicio de sesión
router.post('/login', validateLogin, authController.login);

// Restablecer contraseña para docentes que la hayan olvidado o deseen cambiarla
router.post('/reset-password', authController.resetPassword);

// Obtener datos del perfil autenticado
router.get('/me', verifyToken, authController.getMe);

module.exports = router;
