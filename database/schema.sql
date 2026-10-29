-- ==============================================================================
-- UNIVERSIDAD NACIONAL EXPERIMENTAL "RAFAEL MARÍA BARALT" (UNERMB)
-- PROGRAMA NACIONAL DE FORMACIÓN (PNF) EN INFORMÁTICA
-- SISTEMA DE CONTROL DE ASISTENCIA DOCENTE MEDIANTE CÓDIGO QR Y HORARIOS
-- ESQUEMA RELACIONAL OFICIAL MYSQL (RAILWAY PRODUCCIÓN PERSISTENTE)
-- ==============================================================================

-- 1. TABLA: usuarios
-- Gestión de cuentas con roles, estados y credenciales cifradas con bcrypt
CREATE TABLE IF NOT EXISTS usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    cedula VARCHAR(20) NOT NULL UNIQUE,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    foto_carnet_url VARCHAR(255) NOT NULL,
    rol ENUM('SUPERROOT', 'DOCENTE') NOT NULL DEFAULT 'DOCENTE',
    estado ENUM('PENDIENTE', 'ACTIVO', 'RECHAZADO', 'INACTIVO') NOT NULL DEFAULT 'PENDIENTE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_usuarios_cedula (cedula),
    INDEX idx_usuarios_email (email),
    INDEX idx_usuarios_estado (estado),
    INDEX idx_usuarios_rol (rol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. TABLA: materias
-- Catálogo oficial e inmutable de asignaturas del PNF en Informática (Guía fiel: pensum2026.txt)
CREATE TABLE IF NOT EXISTS materias (
    id INT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30) NULL,
    nombre_materia VARCHAR(150) NOT NULL,
    trayecto VARCHAR(100) NOT NULL,
    trimestre VARCHAR(50) NOT NULL,
    pnf VARCHAR(50) NOT NULL DEFAULT 'Informática',
    horas_semanales INT DEFAULT 4,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_trayecto_trimestre_materia (trayecto, trimestre, nombre_materia),
    INDEX idx_materias_trayecto (trayecto),
    INDEX idx_materias_trimestre (trimestre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. TABLA: docente_materias
-- Materias generales que el docente declara o tiene autorizadas para impartir (Límite estricto de 5)
CREATE TABLE IF NOT EXISTS docente_materias (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    materia_id INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_docente_materia (usuario_id, materia_id),
    CONSTRAINT fk_dm_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_dm_materia FOREIGN KEY (materia_id) REFERENCES materias(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_dm_usuario (usuario_id),
    INDEX idx_dm_materia (materia_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. TABLA: horarios_docente (schedules)
-- Asignación manual de horarios por el Superroot para cada docente individualmente
-- Bloques de tiempo fijos (Lunes a Viernes de 7:30 am a 3:00 pm)
CREATE TABLE IF NOT EXISTS horarios_docente (
    id INT AUTO_INCREMENT PRIMARY KEY,
    docente_id INT NOT NULL,
    materia_id INT NOT NULL,
    dia_semana ENUM('Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes') NOT NULL,
    bloque_numero INT NOT NULL,
    bloque_horario VARCHAR(60) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fin TIME NOT NULL,
    pnf VARCHAR(50) NOT NULL DEFAULT 'Informática',
    trayecto VARCHAR(100) NOT NULL,
    trimestre VARCHAR(50) NOT NULL,
    materia_nombre VARCHAR(150) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_docente_dia_bloque (docente_id, dia_semana, bloque_numero),
    CONSTRAINT fk_hd_docente FOREIGN KEY (docente_id) REFERENCES usuarios(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_hd_materia FOREIGN KEY (materia_id) REFERENCES materias(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_hd_docente (docente_id),
    INDEX idx_hd_dia (dia_semana),
    INDEX idx_hd_bloque (bloque_numero)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. TABLA: asistencias
-- Registro inmutable de marcajes de Entrada y Salida con verificación de puntualidad frente al horario asignado
CREATE TABLE IF NOT EXISTS asistencias (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NOT NULL,
    fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    tipo ENUM('ENTRADA', 'SALIDA') NOT NULL,
    metodo VARCHAR(30) DEFAULT 'QR_SCANNER',
    ip_address VARCHAR(50),
    user_agent TEXT,
    qr_payload_hash VARCHAR(255),
    puntualidad_estado ENUM('A_TIEMPO', 'TARDE', 'FUERA_DE_HORARIO', 'SIN_HORARIO') DEFAULT 'A_TIEMPO',
    minutos_retraso INT DEFAULT 0,
    bloque_horario_detectado VARCHAR(60) NULL,
    materia_detectada VARCHAR(150) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_asistencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_asistencias_usuario (usuario_id),
    INDEX idx_asistencias_fecha (fecha_hora),
    INDEX idx_asistencias_tipo (tipo),
    INDEX idx_asistencias_puntualidad (puntualidad_estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. TABLA: system_logs
-- Registro de auditoría visual y eventos relevantes del sistema (Acceso del Superroot)
CREATE TABLE IF NOT EXISTS system_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    usuario_id INT NULL,
    tipo_evento VARCHAR(60) NOT NULL,
    descripcion TEXT NOT NULL,
    ip_address VARCHAR(50) NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_logs_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_logs_evento (tipo_evento),
    INDEX idx_logs_fecha (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. TABLA: qr_tokens_seguridad
-- Tokens de seguridad firmados con HMAC SHA-256 para códigos QR institucionales
CREATE TABLE IF NOT EXISTS qr_tokens_seguridad (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token_codigo VARCHAR(100) NOT NULL UNIQUE,
    firma_hmac VARCHAR(255) NOT NULL,
    fecha_emision TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valido_hasta TIMESTAMP NOT NULL,
    creado_por INT NULL,
    activo TINYINT(1) DEFAULT 1,
    CONSTRAINT fk_qr_creador FOREIGN KEY (creado_por) REFERENCES usuarios(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_qr_activo (activo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
