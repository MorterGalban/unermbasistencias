const express = require('express');
const router = express.Router();
const docenteController = require('../controllers/docenteController');
const { verifyToken, requireDocente } = require('../middlewares/auth');

// Todas las rutas requieren autenticación y rol DOCENTE o SUPERROOT
router.use(verifyToken, requireDocente);

// Marcaje de Asistencia vía Código QR con Verificación de Horario
router.post('/marcar-asistencia', docenteController.registrarMarcajeQR);

// Historial personal de marcajes con estado de puntualidad
router.get('/historial', docenteController.getHistorialPersonal);

// Materias generales asignadas y actualización de materias (Requerimiento 2.B.1)
router.get('/mis-materias', docenteController.getMisMaterias);
router.put('/mis-materias', docenteController.actualizarMisMaterias);

// Horario semanal individual asignado por el Superroot (Requerimiento 2.B.2)
router.get('/mi-horario', docenteController.getMiHorario);

module.exports = router;
