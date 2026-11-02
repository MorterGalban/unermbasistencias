const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { getFlattenedMaterias } = require('./pensumData');

const schemaPath = path.join(__dirname, 'schema.sql');

// Cargar variables de entorno desde archivo .env local si existe (.env o .env.example.env)
const envFileNames = ['.env', '.env.example.env', '.env.local'];
for (const envName of envFileNames) {
  const envFilePath = path.join(__dirname, '..', envName);
  if (fs.existsSync(envFilePath)) {
    try {
      const envContent = fs.readFileSync(envFilePath, 'utf8');
      for (const line of envContent.split(/\r?\n/)) {
        const trimmed = line.trim();
        if (trimmed && !trimmed.startsWith('#')) {
          const eqIdx = trimmed.indexOf('=');
          if (eqIdx !== -1) {
            const key = trimmed.slice(0, eqIdx).trim();
            const val = trimmed.slice(eqIdx + 1).trim().replace(/^['"]|['"]$/g, '');
            if (!process.env[key] && val !== '') {
              process.env[key] = val;
            }
          }
        }
      }
    } catch (e) {
      // ignorar error de lectura si no hay permisos
    }
  }
}

/**
 * Resolver configuración de conexión para MySQL (compatible con Railway y desarrollo local)
 * Prioridad:
 * 1. MYSQL_URL o DATABASE_URL (Formato URI estándar de Railway)
 * 2. Variables específicas de Railway (MYSQLHOST, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE, MYSQLPORT)
 * 3. Variables estándar (DB_HOST, DB_USER, DB_PASSWORD, DB_NAME, DB_PORT)
 */
function getPoolConfig() {
  const databaseUrl = process.env.MYSQL_URL || process.env.DATABASE_URL;

  // Detección inteligente de SSL para Railway y bases de datos en la nube
  const isCloudHost = (host) => host && (host.includes('railway.app') || host.includes('rlwy.net') || host.includes('aws') || host.includes('cloud'));
  const explicitSsl = process.env.DB_SSL === 'true';

  if (databaseUrl) {
    try {
      const parsedUrl = new URL(databaseUrl);
      const requiresSsl = explicitSsl || 
        parsedUrl.searchParams.get('ssl') === 'true' || 
        parsedUrl.searchParams.get('sslmode') === 'require' ||
        isCloudHost(parsedUrl.hostname);

      return {
        host: parsedUrl.hostname,
        port: parsedUrl.port ? parseInt(parsedUrl.port, 10) : 3306,
        user: decodeURIComponent(parsedUrl.username || 'root'),
        password: decodeURIComponent(parsedUrl.password || ''),
        database: parsedUrl.pathname ? parsedUrl.pathname.replace(/^\//, '') : 'railway',
        waitForConnections: true,
        connectionLimit: 10,
        queueLimit: 0,
        enableKeepAlive: true,
        keepAliveInitialDelay: 10000,
        timezone: '-04:00', // Horario legal de Venezuela (UTC-4)
        ...(requiresSsl ? { ssl: { rejectUnauthorized: false } } : {})
      };
    } catch (e) {
      console.warn('Advertencia: No se pudo parsear DATABASE_URL como URL, usando conexión directa.');
    }
  }

  const host = process.env.DB_HOST || process.env.MYSQLHOST || 'localhost';
  const requiresSslDirect = explicitSsl || isCloudHost(host);

  return {
    host: host,
    port: parseInt(process.env.DB_PORT || process.env.MYSQLPORT || '3306', 10),
    user: process.env.DB_USER || process.env.MYSQLUSER || 'root',
    password: process.env.DB_PASSWORD || process.env.MYSQLPASSWORD || '',
    database: process.env.DB_NAME || process.env.MYSQLDATABASE || 'asistencia_unermb',
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0,
    enableKeepAlive: true,
    keepAliveInitialDelay: 10000,
    timezone: '-04:00',
    ...(requiresSslDirect ? { ssl: { rejectUnauthorized: false } } : {})
  };
}

const pool = mysql.createPool(getPoolConfig());

// Garantizar que toda conexión nueva en el pool aplique la zona horaria legal de Venezuela (UTC-4)
pool.on('connection', (conn) => {
  conn.query("SET time_zone = '-04:00';", (err) => {
    if (err) {
      console.warn('[DB] Advertencia al configurar time_zone en conexión MySQL:', err.message);
    }
  });
});

/**
 * Ejecutar consulta preparada (SELECT)
 */
async function query(sql, params = []) {
  const [rows] = await pool.query(sql, params);
  return rows;
}

/**
 * Ejecutar comando preparado (INSERT, UPDATE, DELETE)
 */
async function execute(sql, params = []) {
  const [result] = await pool.execute(sql, params);
  return result;
}

/**
 * Registrar auditoría en system_logs
 */
async function logEvent(usuarioId, tipoEvento, descripcion, req = null) {
  try {
    let ipAddress = '127.0.0.1';
    let userAgent = 'System';

    if (req) {
      ipAddress = req.ip || req.headers['x-forwarded-for'] || req.socket?.remoteAddress || '127.0.0.1';
      userAgent = req.headers['user-agent'] || 'Unknown';
    }

    await pool.execute(
      `INSERT INTO system_logs (usuario_id, tipo_evento, descripcion, ip_address, user_agent)
       VALUES (?, ?, ?, ?, ?)`,
      [usuarioId || null, tipoEvento, descripcion, ipAddress, userAgent]
    );
  } catch (err) {
    console.error('Error al registrar log de auditoría:', err.message);
  }
}

/**
 * Inicialización de Tablas, Sembrado del Pensum Oficial y Superroot
 */
async function initDatabase(retries = 5, delayMs = 3000) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      console.log(`[DB] Conectando a MySQL (intento ${attempt}/${retries})...`);
      const connection = await pool.getConnection();

      try {
        console.log('[DB] Conexión establecida con MySQL.');
        await connection.query("SET time_zone = '-04:00';");

        // 1. Ejecutar schema.sql por sentencias
        const schemaSql = fs.readFileSync(schemaPath, 'utf8');
        // Separar por punto y coma, ignorando comentarios
        const statements = schemaSql
          .replace(/--.*$/gm, '')
          .split(';')
          .map(s => s.trim())
          .filter(s => s.length > 0);

        for (const stmt of statements) {
          await connection.query(stmt);
        }
        console.log('[DB] Tablas relacionales verificadas / creadas exitosamente.');

        // 1.B Migración segura de columnas para el Módulo de Justificación de Retardos y Persistencia Base64 de Fotografías en Railway
        const alterStatements = [
          "ALTER TABLE usuarios MODIFY COLUMN foto_carnet_url LONGTEXT NOT NULL",
          "ALTER TABLE asistencias ADD COLUMN justificacion_texto TEXT NULL",
          "ALTER TABLE asistencias ADD COLUMN justificacion_imagen_url LONGTEXT NULL",
          "ALTER TABLE asistencias MODIFY COLUMN justificacion_imagen_url LONGTEXT NULL",
          "ALTER TABLE asistencias ADD COLUMN justificacion_fecha DATETIME NULL",
          "ALTER TABLE asistencias ADD COLUMN justificacion_estado ENUM('SIN_JUSTIFICAR', 'ENVIADA', 'REVISADA') DEFAULT 'SIN_JUSTIFICAR'"
        ];
        for (const alterSql of alterStatements) {
          try {
            await connection.query(alterSql);
          } catch (alterErr) {
            // Ignorar error 1060 (Duplicate column name) si la columna ya existe
            if (alterErr.errno !== 1060 && !String(alterErr.message).includes('Duplicate column')) {
              console.warn('[DB] Nota en migración de columna:', alterErr.message);
            }
          }
        }

        // 1.C Auto-migración a Base64 Data URI para fotos existentes en disco local (evita pérdida en reinicios de Railway)
        try {
          const [usersWithRelativePhoto] = await connection.query(
            "SELECT id, foto_carnet_url FROM usuarios WHERE foto_carnet_url LIKE '/uploads/%' OR foto_carnet_url LIKE 'uploads/%'"
          );
          for (const u of usersWithRelativePhoto) {
            const relPath = String(u.foto_carnet_url).replace(/^\//, '');
            const absPath = path.join(__dirname, '..', relPath);
            if (fs.existsSync(absPath)) {
              const ext = path.extname(absPath).toLowerCase();
              const mime = ext === '.png' ? 'image/png' : ext === '.webp' ? 'image/webp' : 'image/jpeg';
              const base64 = fs.readFileSync(absPath).toString('base64');
              const dataUri = `data:${mime};base64,${base64}`;
              await connection.query('UPDATE usuarios SET foto_carnet_url = ? WHERE id = ?', [dataUri, u.id]);
            }
          }
        } catch (migPhotoErr) {
          console.warn('[DB] Nota en migración de fotos a Base64:', migPhotoErr.message);
        }

        // 2. Sembrado estricto de materias según pensum2026.txt
        const [materiaRows] = await connection.query('SELECT COUNT(*) as count FROM materias');
        const materiasOficiales = getFlattenedMaterias();

        if (materiaRows[0].count === 0 || materiaRows[0].count < materiasOficiales.length) {
          console.log(`[DB] Sembrando catálogo de materias oficiales (${materiasOficiales.length} asignaturas)...`);
          for (const m of materiasOficiales) {
            await connection.query(
              `INSERT INTO materias (codigo, nombre_materia, trayecto, trimestre, pnf)
               VALUES (?, ?, ?, ?, ?)
               ON DUPLICATE KEY UPDATE codigo = VALUES(codigo), pnf = VALUES(pnf)`,
              [m.codigo, m.nombre, m.trayecto, m.trimestre, m.pnf]
            );
          }
          console.log('[DB] Catálogo de materias sembrado fielmente según pensum2026.txt.');
        }

        // 3. Sembrado de Superroot Oficial (Coordinador)
        const superrootEmail = 'admin@unermb.edu.ve';
        const [superCheck] = await connection.query('SELECT id FROM usuarios WHERE email = ?', [superrootEmail]);

        if (superCheck.length === 0) {
          const salt = bcrypt.genSaltSync(12);
          const passwordHash = bcrypt.hashSync('Admin123!', salt);

          await connection.query(
            `INSERT INTO usuarios (nombre, apellido, cedula, email, password_hash, foto_carnet_url, rol, estado)
             VALUES (?, ?, ?, ?, ?, ?, 'SUPERROOT', 'ACTIVO')`,
            [
              'Coordinación',
              'PNF Informática',
              '10000001',
              superrootEmail,
              passwordHash,
              '/multimedia/pnf.png'
            ]
          );
          console.log('[DB] Usuario SUPERROOT inicial creado (admin@unermb.edu.ve).');
        }

        // 4. Sembrado de Token QR Oficial si no existe
        const [qrCheck] = await connection.query('SELECT COUNT(*) as count FROM qr_tokens_seguridad WHERE activo = 1');
        if (qrCheck[0].count === 0) {
          await connection.query(
            `INSERT INTO qr_tokens_seguridad (token_codigo, firma_hmac, valido_hasta, activo)
             VALUES (?, ?, DATE_ADD(NOW(), INTERVAL 365 DAY), 1)`,
            [
              'UNERMB-PNF-INFO-OFICIAL-SEC2026',
              'HMAC_SHA256_UNERMB_PNF_COORDINACION_KEY_2026_VALID'
            ]
          );
          console.log('[DB] Token QR oficial institucional inicial activado.');
        }

        console.log('[DB] Base de datos MySQL inicializada y lista para producción.');
        return;
      } finally {
        connection.release();
      }
    } catch (err) {
      console.error(`[DB] Error al inicializar MySQL (intento ${attempt}/${retries}):`, err.message);
      if (attempt === retries) {
        console.error('[DB] No se pudo conectar a MySQL tras múltiples intentos.');
        throw err;
      }
      await new Promise(res => setTimeout(res, delayMs));
    }
  }
}

module.exports = {
  pool,
  query,
  execute,
  logEvent,
  initDatabase
};
