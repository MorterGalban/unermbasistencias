// ==============================================================================
// UNERMB - PNF EN INFORMÁTICA | ASISTENCIA DOCENTE QR
// PANEL DEL DOCENTE: HORARIOS ASIGNADOS, ESCÁNER QR CON CONTROL DE PUNTUALIDAD
// Y ACTUALIZACIÓN DE MATERIAS EN CASCADA (pensum2026.txt)
// ==============================================================================

const DocenteModule = {
  html5QrCode: null,
  isScanning: false,
  currentFacingMode: 'environment', // Cámara trasera por defecto para escanear en móviles
  editandoMaterias: false,
  materiasEnEdicion: [],

  init() {
    this.setupDocenteTabs();
    this.loadDocenteProfile();
    this.loadMiHorario();
    this.loadHistorialPersonal();
    this.loadMisMaterias();
    this.setupScannerControls();
  },

  setupDocenteTabs() {
    const tabButtons = document.querySelectorAll('#view-docente .tab-btn');
    tabButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        tabButtons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        const tabTarget = btn.getAttribute('data-tab');
        document.querySelectorAll('.docente-tab-content').forEach(c => c.style.display = 'none');
        const activeContainer = document.getElementById(tabTarget);
        if (activeContainer) activeContainer.style.display = 'block';

        if (tabTarget === 'tab-docente-horario') this.loadMiHorario();
        if (tabTarget === 'tab-docente-historial') this.loadHistorialPersonal();
        if (tabTarget === 'tab-docente-materias') this.loadMisMaterias();
      });
    });
  },

  async fetchSecure(url, options = {}) {
    const fullUrl = (typeof API_BASE !== 'undefined' && !url.startsWith('http')) ? (API_BASE + url) : url;
    return fetch(fullUrl, {
      ...options,
      headers: {
        'Authorization': `Bearer ${AppState.token}`,
        'Content-Type': 'application/json',
        ...(options.headers || {})
      }
    });
  },

  // ----------------------------------------------------------------------------
  // PERFIL DEL DOCENTE Y BIENVENIDA
  // ----------------------------------------------------------------------------
  loadDocenteProfile() {
    const user = AppState.user;
    if (!user) return;

    const nameEl = document.getElementById('docente-welcome-name');
    const ciEl = document.getElementById('docente-welcome-ci');
    const photoEl = document.getElementById('docente-welcome-photo');

    if (nameEl) nameEl.textContent = `${user.nombre} ${user.apellido}`;
    if (ciEl) ciEl.textContent = `V-${user.cedula}`;
    if (photoEl) photoEl.src = user.foto_carnet_url || '/multimedia/uner.png';
  },

  // ----------------------------------------------------------------------------
  // HORARIO SEMANAL INDIVIDUAL DEL DOCENTE (Requerimiento 2.B.2 y Sección 3)
  // ----------------------------------------------------------------------------
  async loadMiHorario() {
    const container = document.getElementById('docente-horario-container');
    if (!container) return;

    container.innerHTML = `<div style="text-align: center; padding: 2rem; color: var(--text-muted);">Cargando su horario oficial...</div>`;

    try {
      const res = await this.fetchSecure('/api/docente/mi-horario');
      const data = await res.json();

      if (!data.success) {
        container.innerHTML = `<p style="color: var(--status-danger);">Error al cargar horario: ${data.error}</p>`;
        return;
      }

      const diasSemana = data.diasSemana || ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes'];
      const bloquesHorarios = data.bloquesHorarios || [];
      const horarios = data.horarios || [];
      const diaActual = data.diaActual;

      // Resumen de clases hoy
      let resumenHoyHtml = '';
      if (data.clasesHoy && data.clasesHoy.length > 0) {
        resumenHoyHtml = `
          <div style="background: #eff6ff; border-left: 4px solid var(--unermb-blue-accent); padding: 0.85rem 1rem; border-radius: var(--radius-sm); margin-bottom: 1rem; font-size: 0.85rem;">
            <div style="font-weight: 700; color: var(--unermb-blue-dark); margin-bottom: 0.3rem;">
              Hoy es ${diaActual} - Clases Programadas (${data.clasesHoy.length}):
            </div>
            <ul style="padding-left: 1.2rem; margin: 0; color: var(--text-secondary);">
              ${data.clasesHoy.map(c => `
                <li><strong>${c.bloque_horario}:</strong> ${c.materia_nombre} (${c.trayecto} - ${c.trimestre})</li>
              `).join('')}
            </ul>
          </div>
        `;
      } else {
        resumenHoyHtml = `
          <div style="background: #f8fafc; border: 1px dashed #cbd5e1; padding: 0.75rem 1rem; border-radius: var(--radius-sm); margin-bottom: 1rem; font-size: 0.82rem; color: var(--text-muted);">
            Hoy es <strong>${diaActual}</strong>: No tiene bloques de clase programados para el día de hoy.
          </div>
        `;
      }

      // Tabla interactiva de Horario Semanal
      let tableHtml = `
        ${resumenHoyHtml}
        <div class="schedule-table-container">
          <table class="schedule-table">
            <thead>
              <tr>
                <th class="time-header-cell">Franja Horaria</th>
                ${diasSemana.map(d => `
                  <th style="${d === diaActual ? 'background: var(--unermb-blue-primary); border-bottom: 3px solid #38bdf8;' : ''}">
                    ${d} ${d === diaActual ? '<span style="font-size: 0.68rem; display: block; color: #38bdf8;">(Hoy)</span>' : ''}
                  </th>
                `).join('')}
              </tr>
            </thead>
            <tbody>
      `;

      bloquesHorarios.forEach(bloque => {
        tableHtml += `
          <tr>
            <td class="time-header-cell">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--unermb-blue-dark); display: block;">${bloque.etiqueta}</span>
              <span style="font-size: 0.68rem; color: var(--text-muted);">Bloque ${bloque.numero}</span>
            </td>
        `;

        diasSemana.forEach(dia => {
          const claseAsignada = horarios.find(h => h.dia_semana === dia && h.bloque_numero === bloque.numero);
          const isToday = dia === diaActual;

          if (claseAsignada) {
            tableHtml += `
              <td style="${isToday ? 'background: #f0fdf4;' : ''}">
                <div class="schedule-slot-card">
                  <div class="schedule-slot-subject">${claseAsignada.materia_nombre}</div>
                  <div class="schedule-slot-meta">
                    <strong>PNF:</strong> ${claseAsignada.pnf || 'Informática'}<br>
                    <strong>${claseAsignada.trayecto}</strong><br>
                    <strong>Trimestre:</strong> ${claseAsignada.trimestre}
                  </div>
                </div>
              </td>
            `;
          } else {
            tableHtml += `
              <td>
                <div class="schedule-slot-empty">Disponible</div>
              </td>
            `;
          }
        });

        tableHtml += `</tr>`;
      });

      tableHtml += `
            </tbody>
          </table>
        </div>
      `;

      container.innerHTML = tableHtml;
    } catch (err) {
      container.innerHTML = `<p style="color: var(--status-danger);">Error al conectar: ${err.message}</p>`;
    }
  },

  // ----------------------------------------------------------------------------
  // CONTROL DEL ESCÁNER QR CON CÁMARA MÓVIL Y VERIFICACIÓN DE PUNTUALIDAD
  // ----------------------------------------------------------------------------
  setupScannerControls() {
    const btnStart = document.getElementById('btn-start-scanner');
    const btnStop = document.getElementById('btn-stop-scanner');
    const btnSwitch = document.getElementById('btn-switch-camera');
    const fileQrInput = document.getElementById('qr-file-fallback-input');

    if (btnStart) btnStart.addEventListener('click', () => this.startScanner());
    if (btnStop) btnStop.addEventListener('click', () => this.stopScanner());
    if (btnSwitch) {
      btnSwitch.addEventListener('click', () => {
        this.currentFacingMode = this.currentFacingMode === 'environment' ? 'user' : 'environment';
        if (this.isScanning) {
          this.stopScanner().then(() => this.startScanner());
        }
      });
    }

    if (fileQrInput) fileQrInput.addEventListener('change', (e) => this.handleFileQRScan(e));
  },

  async startScanner() {
    const readerElement = document.getElementById('qr-reader');
    const btnStart = document.getElementById('btn-start-scanner');
    const btnStop = document.getElementById('btn-stop-scanner');
    const scannerOverlay = document.getElementById('scanner-overlay');

    if (!readerElement) return;

    if (!window.Html5Qrcode) {
      showToast('Librería del escáner no disponible. Use la opción de subir imagen.', 'error');
      return;
    }

    try {
      if (!this.html5QrCode) {
        this.html5QrCode = new Html5Qrcode("qr-reader");
        window.html5QrCodeScannerInstance = this.html5QrCode;
      }

      const config = {
        fps: 15,
        qrbox: { width: 220, height: 220 },
        aspectRatio: 1.0
      };

      await this.html5QrCode.start(
        { facingMode: this.currentFacingMode },
        config,
        (decodedText) => this.handleQrScanSuccess(decodedText),
        () => {}
      );

      this.isScanning = true;
      if (btnStart) btnStart.style.display = 'none';
      if (btnStop) btnStop.style.display = 'inline-flex';
      if (scannerOverlay) scannerOverlay.style.display = 'flex';
      showToast('Cámara activa. Apunte al Código QR oficial de la Coordinación.', 'info');
    } catch (err) {
      console.error('Error al iniciar cámara:', err);
      showToast(`No se pudo acceder a la cámara: ${err.message || 'Permiso denegado'}. Puede utilizar el botón para subir archivo con QR.`, 'error', 6000);
      this.isScanning = false;
      if (btnStart) btnStart.style.display = 'inline-flex';
      if (btnStop) btnStop.style.display = 'none';
      if (scannerOverlay) scannerOverlay.style.display = 'none';
    }
  },

  async stopScanner() {
    const btnStart = document.getElementById('btn-start-scanner');
    const btnStop = document.getElementById('btn-stop-scanner');
    const scannerOverlay = document.getElementById('scanner-overlay');

    if (this.html5QrCode && this.isScanning) {
      try {
        await this.html5QrCode.stop();
        this.isScanning = false;
      } catch (err) {
        console.error('Error al detener cámara:', err);
      }
    }
    if (btnStart) btnStart.style.display = 'inline-flex';
    if (btnStop) btnStop.style.display = 'none';
    if (scannerOverlay) scannerOverlay.style.display = 'none';
  },

  async handleQrScanSuccess(decodedText) {
    if (!this.isScanning) return;
    this.stopScanner();

    showToast('Procesando marcaje y evaluando horario oficial...', 'info');

    try {
      const res = await this.fetchSecure('/api/docente/marcar-asistencia', {
        method: 'POST',
        body: JSON.stringify({ qr_payload: decodedText })
      });

      const data = await res.json();

      if (data.success) {
        const punt = data.puntualidad;
        let toastType = 'success';
        let puntText = '';

        if (punt) {
          if (punt.estado === 'A_TIEMPO') {
            toastType = 'success';
            puntText = ` [A TIEMPO: ${punt.materiaNombre || 'Jornada'}]`;
          } else if (punt.estado === 'TARDE') {
            toastType = 'warning';
            puntText = ` [RETARDO: ${punt.minutosRetraso} min para ${punt.materiaNombre}]`;
          } else if (punt.estado === 'FUERA_DE_HORARIO') {
            toastType = 'info';
            puntText = ` [Fuera de bloque programado]`;
          } else {
            toastType = 'info';
            puntText = ` [Sin horario programado hoy]`;
          }
        }

        showToast(`${data.mensaje}${puntText}`, toastType, 7000);
        this.loadHistorialPersonal();
        this.loadMiHorario();
      } else {
        showToast(data.error || 'Error al procesar asistencia.', 'error', 6000);
      }
    } catch (err) {
      console.error('Error en marcaje QR:', err);
      showToast('Error de comunicación con el servidor.', 'error');
    }
  },

  async handleFileQRScan(e) {
    const file = e.target.files[0];
    if (!file) return;

    if (!window.Html5Qrcode) {
      showToast('Librería de escáner no disponible.', 'error');
      return;
    }

    try {
      showToast('Decodificando código QR desde la imagen...', 'info');
      const html5QrCode = new Html5Qrcode("qr-reader");
      const decodedText = await html5QrCode.scanFile(file, true);
      this.handleQrScanSuccess(decodedText);
    } catch (err) {
      showToast('No se encontró un Código QR válido en la imagen proporcionada.', 'error');
    } finally {
      e.target.value = '';
    }
  },

  // ----------------------------------------------------------------------------
  // HISTORIAL PERSONAL DEL DOCENTE
  // ----------------------------------------------------------------------------
  async loadHistorialPersonal() {
    const container = document.getElementById('docente-historial-list');
    const statEntradas = document.getElementById('docente-stat-entradas');
    const statSalidas = document.getElementById('docente-stat-salidas');
    const badgeEstadoHoy = document.getElementById('badge-estado-hoy');

    if (!container) return;
    container.innerHTML = `<p style="text-align: center; color: var(--text-muted); padding: 1.5rem;">Cargando historial...</p>`;

    try {
      const res = await this.fetchSecure('/api/docente/historial');
      const data = await res.json();

      if (!data.success) {
        container.innerHTML = `<p style="color: var(--status-danger);">Error: ${data.error}</p>`;
        return;
      }

      if (statEntradas) statEntradas.textContent = data.resumen.entradas;
      if (statSalidas) statSalidas.textContent = data.resumen.salidas;

      if (badgeEstadoHoy) {
        if (data.resumen.estadoActual === 'PRESENTE') {
          badgeEstadoHoy.className = 'badge badge-activo';
          badgeEstadoHoy.textContent = 'PRESENTE EN SEDE';
        } else if (data.resumen.estadoActual === 'JORNADA_FINALIZADA') {
          badgeEstadoHoy.className = 'badge badge-salida';
          badgeEstadoHoy.textContent = 'JORNADA FINALIZADA';
        } else {
          badgeEstadoHoy.className = 'badge badge-pendiente';
          badgeEstadoHoy.textContent = 'SIN MARCAJE HOY';
        }
      }

      if (data.historial.length === 0) {
        container.innerHTML = `
          <div style="text-align: center; padding: 2rem; color: var(--text-muted);">
            No tiene marcajes de asistencia registrados todavía.
          </div>
        `;
        return;
      }

      container.innerHTML = data.historial.map(item => {
        const isEntrada = item.tipo === 'ENTRADA';
        const badgeClass = isEntrada ? 'badge-entrada' : 'badge-salida';

        let puntualidadBadge = '';
        if (item.puntualidad_estado === 'A_TIEMPO') {
          puntualidadBadge = `<span class="badge badge-a-tiempo">A TIEMPO</span>`;
        } else if (item.puntualidad_estado === 'TARDE') {
          puntualidadBadge = `<span class="badge badge-tarde">TARDE (+${item.minutos_retraso} min)</span>`;
        } else if (item.puntualidad_estado === 'FUERA_DE_HORARIO') {
          puntualidadBadge = `<span class="badge badge-fuera-horario">FUERA DE HORARIO</span>`;
        } else if (item.puntualidad_estado === 'SIN_HORARIO') {
          puntualidadBadge = `<span class="badge badge-sin-horario">SIN HORARIO HOY</span>`;
        }

        return `
          <div style="display: flex; align-items: center; justify-content: space-between; padding: 0.85rem 0; border-bottom: 1px solid #f1f5f9; gap: 0.5rem; flex-wrap: wrap;">
            <div>
              <div style="display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.2rem;">
                <span class="badge ${badgeClass}">${item.tipo}</span>
                ${puntualidadBadge}
              </div>
              <div style="font-size: 0.78rem; color: var(--text-muted);">
                ${item.materia_detectada ? `Clase: <strong>${item.materia_detectada}</strong> (${item.bloque_horario_detectado})` : 'Marcaje General'}
              </div>
            </div>
            <div style="text-align: right;">
              <span style="font-weight: 700; color: var(--unermb-blue-dark); font-size: 0.9rem;">
                ${new Date(item.fecha_hora).toLocaleTimeString()}
              </span>
              <div style="font-size: 0.72rem; color: var(--text-muted);">
                ${new Date(item.fecha_hora).toLocaleDateString()}
              </div>
            </div>
          </div>
        `;
      }).join('');
    } catch (err) {
      container.innerHTML = `<p style="color: var(--status-danger);">Error al cargar historial: ${err.message}</p>`;
    }
  },

  // ----------------------------------------------------------------------------
  // CONSULTA DE MATERIAS ASIGNADAS (Solo Lectura - Asignadas por Superroot)
  // ----------------------------------------------------------------------------
  async loadMisMaterias() {
    const container = document.getElementById('docente-materias-cards');
    if (!container) return;

    container.innerHTML = `<p style="text-align: center; color: var(--text-muted); padding: 1.5rem;">Cargando sus materias asignadas...</p>`;

    try {
      const res = await this.fetchSecure('/api/docente/mis-materias');
      const data = await res.json();

      if (!data.success) {
        container.innerHTML = `<p style="color: var(--status-danger);">Error: ${data.error}</p>`;
        return;
      }

      this.materiasActuales = data.materias || [];

      let html = `
        <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem; flex-wrap: wrap; gap: 0.5rem;">
          <div>
            <span style="font-size: 0.85rem; color: var(--text-secondary);">
              Total de unidades curriculares asignadas: <strong>${data.total} / 5</strong>
            </span>
          </div>
          <span class="badge" style="background: var(--unermb-blue-light); color: var(--unermb-blue-dark); font-weight: 600;">
            Pensum Oficial UNERMB 2026
          </span>
        </div>
      `;

      if (!data.materias || data.materias.length === 0) {
        html += `
          <div style="text-align: center; padding: 2.5rem 1rem; color: var(--text-muted); background: #f8fafc; border-radius: var(--radius-md); border: 1px dashed #cbd5e1;">
            <div style="display: flex; justify-content: center; margin-bottom: 0.5rem;">
              <svg class="icon-svg icon-svg-stroke" style="width: 2.2rem; height: 2.2rem; color: #94a3b8;" viewBox="0 0 24 24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"></path><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"></path></svg>
            </div>
            <h4 style="font-size: 0.95rem; color: var(--unermb-blue-dark); margin-bottom: 0.25rem;">Aún no tiene materias asignadas</h4>
            <p style="font-size: 0.8rem; color: var(--text-muted); max-width: 420px; margin: 0 auto;">
              La Coordinación del PNF en Informática (Superroot) es la encargada de asignar sus asignaturas oficiales y sus horarios semanales.
            </p>
          </div>
        `;
      } else {
        html += `
          <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 0.85rem;">
            ${data.materias.map(m => `
              <div class="card" style="padding: 1rem; border-left: 4px solid var(--unermb-blue-primary); margin-bottom: 0; box-shadow: var(--shadow-sm);">
                <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 0.4rem;">
                  <span class="badge" style="background: var(--unermb-blue-dark); color: #fff; font-size: 0.72rem;">${m.codigo || 'PNF'}</span>
                  <span style="font-size: 0.72rem; color: var(--text-muted); font-weight: 600;">${m.horas_semanales || 4}h / semana</span>
                </div>
                <h4 style="font-size: 0.95rem; color: var(--unermb-blue-dark); margin-bottom: 0.35rem; line-height: 1.3;">${m.nombre_materia}</h4>
                <div style="font-size: 0.78rem; color: var(--text-secondary); background: #f8fafc; padding: 0.35rem 0.6rem; border-radius: 4px; border: 1px solid #e2e8f0;">
                  <strong>${m.trayecto}</strong> &bull; ${m.trimestre}
                </div>
              </div>
            `).join('')}
          </div>
        `;
      }

      container.innerHTML = html;
    } catch (err) {
      container.innerHTML = `<p style="color: var(--status-danger);">Error al cargar materias: ${err.message}</p>`;
    }
  }
};

window.initDocenteDashboard = () => DocenteModule.init();
