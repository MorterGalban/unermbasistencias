const multer = require('multer');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// Directorio seguro de almacenamiento para fotos tipo carnet
const uploadDir = path.join(__dirname, '..', 'uploads', 'carnet');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

// Configuración de almacenamiento en disco con nombres aleatorios no predecibles
const storage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, uploadDir);
  },
  filename: function (req, file, cb) {
    const ext = path.extname(file.originalname).toLowerCase();
    const uniqueSuffix = crypto.randomBytes(12).toString('hex');
    const safeFilename = `carnet_${Date.now()}_${uniqueSuffix}${ext}`;
    cb(null, safeFilename);
  }
});

// Filtro estricto de tipos MIME y extensiones permitidas (JPG, PNG, WEBP)
const fileFilter = (req, file, cb) => {
  const allowedMimeTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
  const allowedExtensions = ['.jpg', '.jpeg', '.png', '.webp'];

  const ext = path.extname(file.originalname).toLowerCase();

  if (!allowedMimeTypes.includes(file.mimetype) || !allowedExtensions.includes(ext)) {
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
    next();
  });
};

module.exports = {
  handleCarnetUpload
};
