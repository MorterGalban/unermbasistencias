# UNERMB - PNF EN INFORMÁTICA
## Sistema Web de Control de Asistencia Docente y Asignación de Horarios con MySQL y QR

**Institución:** UNIVERSIDAD NACIONAL EXPERIMENTAL "RAFAEL MARÍA BARALT" (UNERMB)  
**Programa:** PROGRAMA NACIONAL DE FORMACIÓN (PNF) EN INFORMÁTICA  
**Base de Datos Persistente Definitiva:** MySQL 8+ / InnoDB (Sin rastros de SQLite)  
**Entorno de Producción:** Railway Cloud Deployment (Listo para producción)  
**Fuente de Datos Oficial del Pensum:** `pensum2026.txt` (Estricto 100%)

---

### TABLA DE CONTENIDOS
1. [Descripción General](#1-descripción-general)
2. [Fuente de Datos Oficial del Pensum (pensum2026.txt)](#2-fuente-de-datos-oficial-del-pensum-pensum2026txt)
3. [Despliegue y Persistencia en Railway (MySQL)](#3-despliegue-y-persistencia-en-railway-mysql)
4. [Roles y Funcionalidades del Sistema](#4-roles-y-funcionalidades-del-sistema)
   - [Panel del Superroot (Administrador / Coordinador)](#a-panel-del-superroot-administrador--coordinador)
   - [Panel del Docente](#b-panel-del-docente)
5. [Estructura y Franjas Horarias Oficiales](#5-estructura-y-franjas-horarias-oficiales)
6. [Esquema Relacional de Base de Datos MySQL](#6-esquema-relacional-de-base-de-datos-mysql)
7. [Endpoints de la API RESTful](#7-endpoints-de-la-api-restful)
8. [Instrucciones de Despliegue en Railway y Ejecución Local](#8-instrucciones-de-despliegue-en-railway-y-ejecución-local)
9. [Credenciales de Acceso Inicial](#9-credenciales-de-acceso-inicial)

---

### 1. DESCRIPCIÓN GENERAL
Aplicación web full stack de alta disponibilidad diseñada para la Coordinación del PNF en Informática de la UNERMB. Permite la administración integral de personal docente, control de usuarios con eliminación directa, auditoría visual de actividad del sistema (logs), asignación de horarios independientes mediante selección en cascada por trayecto, trimestre y materia oficial, y marcaje de asistencia docente vía código QR dinámico con verificación estricta de puntualidad (A Tiempo / Retardo) contra las franjas horarias asignadas.

---

### 2. FUENTE DE DATOS OFICIAL DEL PENSUM (pensum2026.txt)
La aplicación toma como guía única y fiel el archivo `pensum2026.txt`, sin alterar, omitir ni inventar ninguna materia:
- **Trayecto inicial (PIU):** Trimestre PIU-1 (10 asignaturas oficiales).
- **Trayecto 1:** Trimestres 1-1, 1-2, 1-3.
- **Trayecto 2:** Trimestres 2-1, 2-2, 2-3.
- **Trayecto 3:** Trimestres 3-1, 3-2, 3-3.
- **Trayecto 4:** Trimestres 4-1, 4-2, 4-3.

El sembrado en la tabla relacional `materias` de MySQL se ejecuta automáticamente al arrancar la aplicación si la base de datos se encuentra vacía.

---

### 3. DESPLIEGUE Y PERSISTENCIA EN RAILWAY (MYSQL)
- **Conexión Automática y Resiliente:** Soporta tanto URI unificada (`MYSQL_URL` o `DATABASE_URL`) provista nativamente por Railway al agregar un plugin de MySQL, como variables individuales (`DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_PORT`, o `MYSQLHOST`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`, `MYSQLPORT`).
- **Inicialización en Arranque:** Ejecuta el archivo DDL `database/schema.sql`, crea las tablas con integridad referencial (`ON DELETE CASCADE`), e inserta el usuario Superroot inicial y el catálogo del pensum sin necesidad de scripts manuales externos.
- **Persistencia Permanente:** Utiliza el motor transaccional `InnoDB` de MySQL con codificación `utf8mb4_unicode_ci`. Se eliminó completamente SQLite (`node:sqlite` y archivos `.db`).

---

### 4. ROLES Y FUNCIONALIDADES DEL SISTEMA

#### A. Panel del Superroot (Administrador / Coordinador)
1. **Gestión de Usuarios:**
   - Listado completo de usuarios registrados con fotografía carnet, cédula, correo y estado.
   - **Capacidad de eliminación directa:** Botón para eliminar usuarios permanentemente con eliminación en cascada de sus horarios y asistencias.
   - Activación y desactivación de docentes.
2. **Control de Logs del Sistema:**
   - Panel visual de auditoría para consultar los registros de actividad: inicios de sesión, asignaciones y eliminaciones de horarios, aprobación y rechazo de postulaciones, eliminación de usuarios y marcajes.
   - Filtros por tipo de evento y por fecha, además de buscador por texto.
3. **Asignación Exclusiva de Materias y Horarios por Docente:**
   - **Control Exclusivo de Materias:** El Superroot es el único usuario con permisos para asignar y remover las unidades curriculares oficiales (hasta 5 materias por docente) utilizando el selector en cascada del Pensum 2026.
   - Visualización de la lista de docentes y las materias asignadas.
   - Botón individual "Asignar Horario" para acceder a la gestión integral de materias y horarios de cada docente.
   - Cuadrícula semanal interactiva (Lunes a Viernes, 5 bloques fijos).
   - **Sistema de selección en cascada:**
     1. Seleccionar el Trayecto (Trayecto inicial, Trayecto 1, Trayecto 2, Trayecto 3 o Trayecto 4).
     2. Según el trayecto elegido, desplegar el Trimestre correspondiente.
     3. Según el trimestre elegido, desplegar la lista exacta de Materias.
     4. Asignar la materia seleccionada a un Día de la semana (Lunes a Viernes) y a un Bloque Horario fijo.
   - Validación automática de colisión de horarios para evitar que un docente sea asignado dos veces al mismo día y franja horaria.
   - Eliminación individual de bloques asignados y materias con un solo clic.

#### B. Panel del Docente
1. **Registro Simplificado:**
   - El docente completa su registro con sus datos personales y fotografía tipo carnet obligatoria. La asignación de materias queda reservada para el Superroot.
2. **Consulta de Unidades Curriculares Asignadas:**
   - Pestaña de consulta exclusiva para visualizar las asignaturas que la Coordinación le ha autorizado e impartido según el Pensum 2026.
3. **Horario y Control de Asistencia QR:**
   - **Visualización de su horario semanal independiente:** Cuadrícula completa con sus clases asignadas por el Superroot, destacando el día actual y las clases programadas para hoy.
   - **Sistema de control de puntualidad:** Al escanear el Código QR institucional, el sistema verifica la hora exacta y el día frente a su horario asignado:
     - **A TIEMPO:** Si marca dentro del rango de inicio de su clase programada (hasta 10 minutos de cortesía).
     - **TARDE:** Si marca posterior al inicio de su clase, registrando los minutos exactos de retraso.
     - **FUERA DE HORARIO / SIN HORARIO:** Si marca en días o franjas donde no tiene asignación docente.

---

### 5. ESTRUCTURA Y FRANJAS HORARIAS OFICIALES
Los horarios cubren los 5 días hábiles de la semana (Lunes a Viernes) y se construyen progresivamente mediante las asignaciones manuales del Superroot en los siguientes 5 bloques fijos:
1. **Bloque 1:** 7:30 am – 9:00 am (`07:30:00` a `09:00:00`)
2. **Bloque 2:** 9:00 am – 10:30 am (`09:00:00` a `10:30:00`)
3. **Bloque 3:** 10:30 am – 12:00 pm (`10:30:00` a `12:00:00`)
4. **Bloque 4:** 12:00 pm – 1:30 pm (`12:00:00` a `13:30:00`)
5. **Bloque 5:** 1:30 pm – 3:00 pm (`13:30:00` a `15:00:00`)

Cada registro de horario refleja:
- Día de la semana (`Lunes`, `Martes`, `Miércoles`, `Jueves`, `Viernes`)
- Bloque Horario (etiqueta y número del 1 al 5)
- Materia (fiel al `pensum2026.txt`)
- PNF (Informática)
- Trimestre
- Trayecto

---

### 6. ESQUEMA RELACIONAL DE BASE DE DATOS MYSQL
Ubicado en `database/schema.sql`.

- `usuarios`: Cuentas con roles (`SUPERROOT`, `DOCENTE`), estados (`ACTIVO`, `PENDIENTE`, `RECHAZADO`, `INACTIVO`), fotografía carnet y hash bcrypt.
- `materias`: Asignaturas oficiales con trayecto, trimestre y código del PNF en Informática.
- `docente_materias`: Relación N:M de materias declaradas por cada docente (máximo 5).
- `horarios_docente`: Asignación individual de horarios por docente, día, bloque, materia, trayecto y trimestre con restricción UNIQUE `(docente_id, dia_semana, bloque_numero)`.
- `asistencias`: Marcajes con detección automática de `ENTRADA`/`SALIDA`, cálculo de `puntualidad_estado` (`A_TIEMPO`, `TARDE`, `FUERA_DE_HORARIO`, `SIN_HORARIO`), minutos de retraso, bloque y materia detectada.
- `system_logs`: Auditoría inmutable de eventos administrativos y del sistema.
- `qr_tokens_seguridad`: Tokens dinámicos firmados con HMAC SHA-256.

---

### 7. ENDPOINTS DE LA API RESTFUL

#### Autenticación y Pensum
- `GET /api/auth/materias`: Catálogo plano oficial de materias.
- `GET /api/auth/pensum-estructura`: Estructura jerárquica en cascada oficial (pensum2026.txt), franjas horarias y días hábiles.
- `POST /api/auth/registro`: Registro docente con foto carnet (materias reservadas para Superroot).
- `POST /api/auth/login`: Autenticación con JWT (bloquea usuarios en estado PENDIENTE).
- `POST /api/auth/reset-password`: Restablecimiento de contraseña por cédula y correo.
- `GET /api/auth/me`: Perfil del usuario autenticado.

#### Superroot (Coordinador)
- `GET /api/superroot/dashboard-stats`: Métricas en tiempo real.
- `GET /api/superroot/solicitudes`: Solicitudes pendientes con fotografía.
- `PATCH /api/superroot/solicitudes/:id/aprobar`: Aprobar docente (pasa a ACTIVO).
- `PATCH /api/superroot/solicitudes/:id/rechazar`: Rechazar docente.
- `GET /api/superroot/docentes`: Listado general de usuarios y materias.
- `PATCH /api/superroot/docentes/:id/estado`: Activar o desactivar docente.
- `DELETE /api/superroot/usuarios/:id`: **Eliminación directa de usuario.**
- `GET /api/superroot/system-logs`: **Consulta visual de logs de auditoría.**
- `GET /api/superroot/docentes/:id/horario`: Horario semanal y materias del docente.
- `POST /api/superroot/docentes/:id/horario`: **Asignar clase al horario (cascada pensum2026.txt, día y bloque).**
- `DELETE /api/superroot/horarios/:horario_id`: Quitar clase del horario.
- `POST /api/superroot/docentes/:id/materias`: **Asignar materia del pensum al docente.**
- `DELETE /api/superroot/docentes/:id/materias/:materia_id`: **Quitar materia del docente.**
- `GET /api/superroot/generar-qr`: Generación de Código QR Oficial firmado con HMAC-SHA256.
- `GET /api/superroot/asistencias`: Logs de asistencia con filtro de puntualidad y exportación a CSV.

#### Docente
- `POST /api/docente/marcar-asistencia`: Marcaje de asistencia con cámara QR y control de puntualidad frente a horario.
- `GET /api/docente/mi-horario`: **Consulta de su horario semanal oficial.**
- `GET /api/docente/historial`: Historial personal con estado de puntualidad.
- `GET /api/docente/mis-materias`: Consulta de materias asignadas por Coordinación.

---

### 8. INSTRUCCIONES DE DESPLIEGUE EN RAILWAY Y EJECUCIÓN LOCAL

#### Despliegue en Railway:
1. Conecta tu repositorio de GitHub en [Railway](https://railway.app/).
2. Agrega una base de datos **MySQL** en tu proyecto de Railway (`+ New` -> `Database` -> `Add MySQL`).
3. Railway conectará automáticamente la variable `MYSQL_URL` o las variables individuales (`MYSQLHOST`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`, `MYSQLPORT`).
4. Despliega el servicio. Al arrancar `node server.js`, la aplicación ejecutará automáticamente las migraciones de `schema.sql` y sembrará el pensum oficial y las credenciales iniciales.

#### Ejecución Local:
```bash
# Instalar dependencias
npm install

# Configurar variables de entorno (en archivo .env o variables de sistema)
# DB_HOST=localhost
# DB_USER=root
# DB_PASSWORD=tu_password
# DB_NAME=asistencia_unermb
# DB_PORT=3306

# Iniciar servidor
npm start
```

---

### 9. CREDENCIALES DE ACCESO INICIAL
- **Rol:** SUPERROOT (Coordinador PNF en Informática)
- **Correo Electrónico:** `admin@unermb.edu.ve`
- **Contraseña:** `Admin123!`
- **Cédula:** `10000001`
