const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// Directorio seguro de almacenamiento para fotos tipo carnet
const uploadDir = path.join(__dirname, '..', 'uploads', 'carnet');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

// Directorio seguro de almacenamiento para evidencias de justificativos de retardo
const justificativosDir = path.join(__dirname, '..', 'uploads', 'justificativos');
if (!fs.existsSync(justificativosDir)) {
  fs.mkdirSync(justificativosDir, { recursive: true });
}

// Configuración de almacenamiento en disco con nombres aleatorios no predecibles
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    const ext = path.extname(file.originalname).toLowerCase() || '.jpg';
    const uniqueSuffix = crypto.randomBytes(12).toString('hex');
    const safeFilename = `carnet_${Date.now()}_${uniqueSuffix}${ext}`;
    cb(null, safeFilename);
  }
});

const justificativoStorage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, justificativosDir);
  },
  filename: function (req, file, cb) {
    const ext = path.extname(file.originalname).toLowerCase() || '.jpg';
    const uniqueSuffix = crypto.randomBytes(12).toString('hex');
    const safeFilename = `justificativo_${Date.now()}_${uniqueSuffix}${ext}`;
    cb(null, safeFilename);
  }
});

// Filtro estricto de tipos MIME y extensiones permitidas (JPG, PNG, WEBP)
const fileFilter = (req, file, cb) => {
  const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.webp'];

  const ext = path.extname(file.originalname).toLowerCase();

  if (!allowedMimeTypes.includes(file.mimetype) && !allowedExtensions.includes(ext)) {
    const error = new Error('Formato de archivo inválido. Solo se admiten imágenes JPG, JPEG, PNG o WEBP.');
    error.code = 'INVALID_FILE_TYPE';
    return cb(error, false);
  }

  cb(null, true);
};

// Límite de tamaño: 5 Megabytes máximo
const uploadCarnet = multer({
  storage: storage,
  fileFilter: fileFilter,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5 MB
  }
}).single('foto_carnet');

const uploadJustificativo = multer({
  storage: justificativoStorage,
  fileFilter: fileFilter,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5 MB
  }
}).single('evidencia');

// Convierte un archivo subido por Multer en Data URI Base64 persistente para MySQL (inmune al sistema efímero de Railway)
function attachBase64DataUri(file) {
  if (!file || !file.path) return;
  try {
    if (fs.existsSync(file.path)) {
      const buffer = fs.readFileSync(file.path);
      const ext = path.extname(file.originalname || file.filename || '').toLowerCase();
      let mime = file.mimetype;
      if (!mime || !mime.startsWith('image/')) {
        mime = ext === '.png' ? 'image/png' : ext === '.webp' ? 'image/webp' : 'image/jpeg';
      }
      file.base64DataUri = `data:${mime};base64,${buffer.toString('base64')}`;
    }
  } catch (err) {
    console.warn('[UPLOAD] Advertencia convirtiendo imagen a Base64:', err.message);
  }
}

/**
 * Normaliza cualquier valor de foto_carnet_url o justificacion_imagen_url almacenado en BD
 * para garantizar que la API SIEMPRE devuelva un Data URI Base64 ("data:image/jpeg;base64,...")
 * o una URL absoluta ("https://..."), nunca una ruta relativa suelta ("/uploads/...").
 */
function normalizeMediaUrl(rawUrl, req = null) {
  if (!rawUrl || typeof rawUrl !== 'string') return null;
  const trimmed = rawUrl.trim();
  if (!trimmed) return null;

  // 1. Si ya es un Data URI Base64 o una URL absoluta HTTP/HTTPS, devolver intacto
  if (trimmed.startsWith('data:image/') || trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed;
  }

  // 2. Si es una ruta relativa (/uploads/... o /multimedia/...), intentar convertir desde disco a Base64 persistente
  const cleanRel = trimmed.replace(/^\//, '');
  const localAbsPath = path.join(__dirname, '..', cleanRel);
  try {
    if (fs.existsSync(localAbsPath) && fs.statSync(localAbsPath).isFile()) {
      const ext = path.extname(localAbsPath).toLowerCase();
      const mime = ext === '.png' ? 'image/png' : ext === '.webp' ? 'image/webp' : 'image/jpeg';
      const base64 = fs.readFileSync(localAbsPath).toString('base64');
      return `data:${mime};base64,${base64}`;
    }
  } catch (e) {
    // Continuar al fallback de URL absoluta
  }

  // 3. Construir URL absoluta con protocolo HTTPS en Railway o el host activo del request
  let baseUrl = process.env.PUBLIC_BASE_URL || (process.env.RAILWAY_PUBLIC_DOMAIN ? `https://${process.env.RAILWAY_PUBLIC_DOMAIN}` : '');
  if (!baseUrl && req) {
    const forwardedProto = req.headers['x-forwarded-proto'] ? String(req.headers['x-forwarded-proto']).split(',')[0].trim() : null;
    const host = req.headers['x-forwarded-host'] || req.headers.host || 'localhost:3000';
    const isCloud = host.includes('railway.app') || host.includes('rlwy.net') || (!host.includes('localhost') && !host.startsWith('127.0.0.1') && !host.startsWith('192.168.'));
    const proto = forwardedProto || (isCloud ? 'https' : (req.protocol || 'http'));
    baseUrl = `${proto}://${host}`;
  }
  if (!baseUrl) {
    baseUrl = 'https://unermbasistencias-production.up.railway.app';
  }
  return `${baseUrl.replace(/\/$/, '')}/${cleanRel}`;
}

// Middleware envoltorio para captura de errores controlados
const handleCarnetUpload = (req, res, next) => {
  uploadCarnet(req, res, (err) => {
    if (err instanceof multer.MulterError) {
      if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(400).json({
          success: false,
          error: 'La fotografía excede el tamaño máximo permitido de 5 MB.'
        });
      }
      return res.status(400).json({
        success: false,
        error: `Error al subir la imagen: ${err.message}`
      });
    } else if (err) {
      return res.status(400).json({
        success: false,
        error: err.message || 'Error en la validación del archivo de imagen.'
      });
    }
    if (req.file) {
      attachBase64DataUri(req.file);
    }
    next();
  });
};

// Middleware envoltorio para captura de errores en evidencia opcional de justificativo
const handleJustificativoUpload = (req, res, next) => {
  uploadJustificativo(req, res, (err) => {
    if (err instanceof multer.MulterError) {
      if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(400).json({
          success: false,
          error: 'La imagen del justificativo excede el tamaño máximo permitido de 5 MB.'
        });
      }
      return res.status(400).json({
        success: false,
        error: `Error al subir la imagen del justificativo: ${err.message}`
      });
    } else if (err) {
      return res.status(400).json({
        success: false,
        error: err.message || 'Error en la validación del archivo de evidencia.'
      });
    }
    if (req.file) {
      attachBase64DataUri(req.file);
    }
    next();
  });
};

module.exports = {
  handleCarnetUpload,
  handleJustificativoUpload,
  normalizeMediaUrl
};
