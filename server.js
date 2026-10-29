// Forzar zona horaria legal de Venezuela (HLV / UTC-4) en todo el entorno de ejecución Node.js
process.env.TZ = 'America/Caracas';

const express = require('express');
const path = require('path');
const fs = require('fs');
const cors = require('cors');
const helmet = require('helmet');

// Garantizar existencia de directorio de almacenamiento seguro de fotos tipo carnet
const uploadCarnetDir = path.join(__dirname, 'uploads', 'carnet');
if (!fs.existsSync(uploadCarnetDir)) {
  fs.mkdirSync(uploadCarnetDir, { recursive: true });
}

// Inicializador de base de datos MySQL relacional persistente
const { initDatabase } = require('./database/db');

const authRoutes = require('./routes/authRoutes');
const superrootRoutes = require('./routes/superrootRoutes');
const docenteRoutes = require('./routes/docenteRoutes');

const app = express();
const PORT = process.env.PORT || 3000;
const HOST = '0.0.0.0';

// Configuración de Seguridad HTTP con Helmet (permisivo en desarrollo local)
app.use(
  helmet({
    contentSecurityPolicy: false,
    crossOriginEmbedderPolicy: false
  })
);

// Habilitar CORS y Parseo de datos
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// Servir recursos estáticos:
// 1. multimedia: logos institucionales (uner.png, pnf.png)
app.use('/multimedia', express.static(path.join(__dirname, 'multimedia')));
// 2. uploads: fotos tipo carnet subidas por los docentes
app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
// 3. public: interfaz de usuario web responsive
app.use(express.static(path.join(__dirname, 'public')));

// Rutas de API RESTful
app.use('/api/auth', authRoutes);
app.use('/api/superroot', superrootRoutes);
app.use('/api/docente', docenteRoutes);

// Ruta de comprobación de salud del sistema (compatible con Railway y monitoreo)
const healthHandler = (req, res) => {
  const now = new Date();
  res.json({
    status: 'ONLINE',
    database: 'MySQL',
    institucion: 'UNIVERSIDAD NACIONAL EXPERIMENTAL "RAFAEL MARÍA BARALT"',
    programa: 'PNF EN INFORMÁTICA',
    timezone: 'America/Caracas (HLV / UTC-4)',
    hora_venezuela: new Intl.DateTimeFormat('es-VE', {
      timeZone: 'America/Caracas',
      dateStyle: 'full',
      timeStyle: 'long'
    }).format(now),
    server_time_iso: now.toISOString()
  });
};

app.get('/api/health', healthHandler);
app.get('/health', healthHandler);

// Redireccionar todas las rutas no reconocidas al frontend Single-Page Application
app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Manejador global de errores
app.use((err, req, res, next) => {
  console.error('Error no capturado:', err);
  res.status(err.status || 500).json({
    success: false,
    error: err.message || 'Error interno en el servidor.'
  });
});

async function startServer() {
  try {
    await initDatabase();
  } catch (err) {
    console.error('[SERVER] Advertencia: Error durante initDatabase inicial (se reintentará en conexiones entrantes):', err.message);
  }

  app.listen(PORT, HOST, () => {
    console.log(`================================================================`);
    console.log(`UNERMB - PNF EN INFORMÁTICA | CONTROL DE ASISTENCIA QR`);
    console.log(`Servidor activo y escuchando en: http://${HOST}:${PORT}`);
    console.log(`Base de datos persistente: MySQL (Railway Production Ready)`);
    console.log(`================================================================`);
  });
}

if (require.main === module) {
  startServer();
}

module.exports = app;
