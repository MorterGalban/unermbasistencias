const http = require('http');
const fs = require('fs');
const path = require('path');

// Asegurar que el servidor está corriendo en localhost:3000
const BASE_URL = 'http://localhost:3000';

function makeRequest(method, urlPath, body = null, headers = {}) {
  return new Promise((resolve, reject) => {
    const url = new URL(urlPath, BASE_URL);
    const reqOptions = {
      method,
      hostname: url.hostname,
      port: url.port,
      path: url.pathname + url.search,
      headers: { ...headers }
    };

    let postData = null;
    if (body) {
      if (typeof body === 'string' || Buffer.isBuffer(body)) {
        postData = body;
      } else {
        postData = JSON.stringify(body);
        reqOptions.headers['Content-Type'] = 'application/json';
      }
      reqOptions.headers['Content-Length'] = Buffer.byteLength(postData);
    }

    const req = http.request(reqOptions, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve({ status: res.statusCode, headers: res.headers, body: json });
        } catch {
          resolve({ status: res.statusCode, headers: res.headers, body: data });
        }
      });
    });

    req.on('error', reject);
    if (postData) req.write(postData);
    req.end();
  });
}

// Construir payload multipart/form-data simple para subida de foto carnet
function buildMultipart(fields, fileField, filename, fileBuffer, mimeType) {
  const boundary = '----WebKitFormBoundary' + Math.random().toString(36).substring(2);
  let chunks = [];

  for (const [key, value] of Object.entries(fields)) {
    chunks.push(Buffer.from(`--${boundary}\r\n`));
    chunks.push(Buffer.from(`Content-Disposition: form-data; name="${key}"\r\n\r\n`));
    chunks.push(Buffer.from(`${value}\r\n`));
  }

  if (fileField && fileBuffer) {
    chunks.push(Buffer.from(`--${boundary}\r\n`));
    chunks.push(Buffer.from(`Content-Disposition: form-data; name="${fileField}"; filename="${filename}"\r\n`));
    chunks.push(Buffer.from(`Content-Type: ${mimeType}\r\n\r\n`));
    chunks.push(fileBuffer);
    chunks.push(Buffer.from(`\r\n`));
  }

  chunks.push(Buffer.from(`--${boundary}--\r\n`));
  return {
    boundary,
    body: Buffer.concat(chunks)
  };
}

async function runTests() {
  console.log('--- INICIANDO SUITE DE PRUEBAS AUTOMATIZADAS E2E ---');
  let passCount = 0;
  let failCount = 0;

  function assert(condition, testName) {
    if (condition) {
      console.log(`[PASS] ${testName}`);
      passCount++;
    } else {
      console.error(`[FAIL] ${testName}`);
      failCount++;
    }
  }

  try {
    // 1. Health check
    const health = await makeRequest('GET', '/api/health');
    assert(health.status === 200 && health.body.status === 'ONLINE', 'Endpoint /api/health responde ONLINE');

    // 2. Materias catálogo
    const materiasRes = await makeRequest('GET', '/api/auth/materias');
    assert(materiasRes.status === 200 && materiasRes.body.materias.length > 0, `Catálogo de materias PNF cargado (${materiasRes.body.materias.length} materias)`);

    // 3. Login Superroot
    const superrootLogin = await makeRequest('POST', '/api/auth/login', {
      identificador: 'admin@unermb.edu.ve',
      password: 'Admin123!'
    });
    assert(superrootLogin.status === 200 && superrootLogin.body.token, 'Login Superroot exitoso y JWT generado');
    const superrootToken = superrootLogin.body.token;

    // 4. Intentar registrar docente con datos inválidos (Validación de Seguridad)
    // A. Nombre con números
    const dummyImage = Buffer.from('fake-image-bytes-header');
    const badName = buildMultipart(
      {
        nombre: 'Pedro123',
        apellido: 'Perez',
        cedula: '19876543',
        email: 'pedro@unermb.edu.ve',
        password: 'Password123!',
        confirm_password: 'Password123!',
        materias: JSON.stringify([1, 2])
      },
      'foto_carnet',
      'carnet.jpg',
      dummyImage,
      'image/jpeg'
    );
    const badNameRes = await makeRequest('POST', '/api/auth/registro', badName.body, {
      'Content-Type': `multipart/form-data; boundary=${badName.boundary}`
    });
    assert(badNameRes.status === 400, 'Rechazo por nombre con caracteres no alfabéticos (400)');

    // B. Más de 5 materias (Regla académica de límite 5)
    const tooManySubjects = buildMultipart(
      {
        nombre: 'Carlos',
        apellido: 'Guanipa',
        cedula: '19876543',
        email: 'carlos@unermb.edu.ve',
        password: 'Password123!',
        confirm_password: 'Password123!',
        materias: JSON.stringify([1, 2, 3, 4, 5, 6]) // 6 materias
      },
      'foto_carnet',
      'carnet.jpg',
      dummyImage,
      'image/jpeg'
    );
    const subjectsRes = await makeRequest('POST', '/api/auth/registro', tooManySubjects.body, {
      'Content-Type': `multipart/form-data; boundary=${tooManySubjects.boundary}`
    });
    assert(subjectsRes.status === 400 && subjectsRes.body.error.includes('5 materias'), 'Rechazo estricto al exceder límite de 5 materias (400)');

    // 5. Registro exitoso de docente válido (3 materias, foto tipo carnet)
    const uniqueSuffix = Math.floor(100000 + Math.random() * 899999);
    const testCedula = '24' + uniqueSuffix;
    const testEmail = `profesor.${uniqueSuffix}@unermb.edu.ve`;
    const validMultipart = buildMultipart(
      {
        nombre: 'Alejandro',
        apellido: 'Morales',
        cedula: testCedula,
        email: testEmail,
        password: 'Docente2026!',
        confirm_password: 'Docente2026!',
        materias: JSON.stringify([1, 2, 3]) // 3 materias
      },
      'foto_carnet',
      'carnet.jpg',
      dummyImage,
      'image/jpeg'
    );
    const regRes = await makeRequest('POST', '/api/auth/registro', validMultipart.body, {
      'Content-Type': `multipart/form-data; boundary=${validMultipart.boundary}`
    });
    assert(regRes.status === 201 && regRes.body.usuario.estado === 'PENDIENTE', 'Docente registrado exitosamente en estado PENDIENTE');
    const nuevoDocenteId = regRes.body.usuario.id;

    // 6. Intento de login del docente recién registrado (Debe ser denegado por estar PENDIENTE)
    const pendingLogin = await makeRequest('POST', '/api/auth/login', {
      identificador: testEmail,
      password: 'Docente2026!'
    });
    assert(pendingLogin.status === 403 && pendingLogin.body.codigo_estado === 'PENDIENTE', 'Login bloqueado para cuenta en estado PENDIENTE (403)');

    // 7. Superroot consulta solicitudes pendientes
    const pendientesRes = await makeRequest('GET', '/api/superroot/solicitudes', null, {
      'Authorization': `Bearer ${superrootToken}`
    });
    const found = pendientesRes.body.solicitudes.find(s => s.id === nuevoDocenteId);
    assert(found && found.materias.length === 3, 'Superroot visualiza solicitud con foto y materias asignadas');

    // 8. Superroot aprueba la solicitud
    const aprobarRes = await makeRequest('PATCH', `/api/superroot/solicitudes/${nuevoDocenteId}/aprobar`, null, {
      'Authorization': `Bearer ${superrootToken}`
    });
    assert(aprobarRes.status === 200 && aprobarRes.body.success, 'Superroot aprueba docente exitosamente');

    // 9. Ahora el docente puede iniciar sesión normalmente
    const activeLogin = await makeRequest('POST', '/api/auth/login', {
      identificador: testEmail,
      password: 'Docente2026!'
    });
    assert(activeLogin.status === 200 && activeLogin.body.token, 'Docente ACTIVO inicia sesión con éxito');
    const docenteToken = activeLogin.body.token;

    // 10. Superroot genera el código QR oficial de la Coordinación
    const qrRes = await makeRequest('GET', '/api/superroot/generar-qr', null, {
      'Authorization': `Bearer ${superrootToken}`
    });
    assert(qrRes.status === 200 && qrRes.body.qrDataURL && qrRes.body.qrPayload, 'Superroot genera Código QR Institucional con HMAC');
    const qrPayload = qrRes.body.qrPayload;

    // 11. Docente escanea QR -> Detección automática de ENTRADA
    const scanEntrada = await makeRequest('POST', '/api/docente/marcar-asistencia', {
      qr_payload: qrPayload
    }, {
      'Authorization': `Bearer ${docenteToken}`
    });
    assert(scanEntrada.status === 201 && scanEntrada.body.tipo === 'ENTRADA', 'Primer marcaje detectado automáticamente como ENTRADA');

    // 12. Historial personal del docente
    const historialRes = await makeRequest('GET', '/api/docente/historial', null, {
      'Authorization': `Bearer ${docenteToken}`
    });
    assert(historialRes.status === 200 && historialRes.body.resumen.entradas >= 1, 'Historial personal refleja marcaje registrado');

    // 13. Superroot consulta logs de asistencias
    const logsRes = await makeRequest('GET', '/api/superroot/asistencias', null, {
      'Authorization': `Bearer ${superrootToken}`
    });
    assert(logsRes.status === 200 && logsRes.body.logs.length >= 1, 'Superroot visualiza marcajes en reporte de auditoría');

    // 14. Bloqueo al Docente para modificar materias (Exclusivo Superroot)
    const docenteEditAttempt = await makeRequest('PUT', '/api/docente/mis-materias', { materiasIds: [4, 5] }, {
      'Authorization': `Bearer ${docenteToken}`
    });
    assert(docenteEditAttempt.status === 403, 'Docente bloqueado al intentar modificar materias directamente (403)');

    // 15. Superroot asigna materia directamente al docente
    const superrootAssignMateria = await makeRequest('POST', `/api/superroot/docentes/${nuevoDocenteId}/materias`, { materia_id: 4 }, {
      'Authorization': `Bearer ${superrootToken}`
    });
    assert(superrootAssignMateria.status === 201 && superrootAssignMateria.body.success, 'Superroot asigna materia al docente exitosamente');

    // 16. Registro de docente sin materias seleccionadas (Nuevo flujo donde Superroot asigna materias)
    const uniqueSuffix2 = Math.floor(100000 + Math.random() * 899999);
    const noSubjectsDocente = buildMultipart(
      {
        nombre: 'Beatriz',
        apellido: 'Rivas',
        cedula: '25' + uniqueSuffix2,
        email: `profesora.${uniqueSuffix2}@unermb.edu.ve`,
        password: 'Docente2026!',
        confirm_password: 'Docente2026!'
      },
      'foto_carnet',
      'carnet.jpg',
      dummyImage,
      'image/jpeg'
    );
    const regNoSubjects = await makeRequest('POST', '/api/auth/registro', noSubjectsDocente.body, {
      'Content-Type': `multipart/form-data; boundary=${noSubjectsDocente.boundary}`
    });
    assert(regNoSubjects.status === 201 && regNoSubjects.body.usuario.materias_registradas === 0, 'Docente se registra exitosamente sin materias (asignación delegada a Superroot)');

    console.log(`\n========================================`);
    console.log(`RESULTADOS FINALES: ${passCount} PASADOS / ${failCount} FALLADOS`);
    console.log(`========================================\n`);

    process.exit(failCount === 0 ? 0 : 1);
  } catch (err) {
    console.error('Error durante la ejecución del test:', err);
    process.exit(1);
  }
}

// Iniciar servidor si no está activo, o ejecutar directamente contra el puerto 3000
const app = require('./server');
const serverInstance = app.listen(3000, '0.0.0.0', () => {
  setTimeout(() => {
    runTests();
  }, 400);
});

serverInstance.on('error', (e) => {
  if (e.code === 'EADDRINUSE') {
    // El servidor ya está corriendo previamente en el puerto 3000
    runTests();
  } else {
    console.error('Error al inicializar el servidor de pruebas:', e);
  }
});
