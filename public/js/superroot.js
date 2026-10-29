// ==============================================================================
// UNERMB - PNF EN INFORMÁTICA | ASISTENCIA DOCENTE QR
// PANEL SUPERROOT: GESTIÓN DE USUARIOS, ASIGNACIÓN DE HORARIOS EN CASCADA,
// CONTROL DE LOGS DE AUDITORÍA Y REPORTES
// ==============================================================================

const SuperrootModule = {
  activeTab: 'tab-solicitudes',
  qrData: null,
  currentDocenteHorarioId: null,

  init() {
    this.setupTabNavigation();
    this.loadDashboardStats();
    this.loadSolicitudesPendientes();
    this.setupFilters();
  },

  setupTabNavigation() {
    const tabButtons = document.querySelectorAll('#view-superroot .tab-btn');
    tabButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        tabButtons.forEach(b => b.classList.remove('active'));
        btn.classList.add('active');

        const tabTarget = btn.getAttribute('data-tab');
        this.activeTab = tabTarget;

        document.querySelectorAll('.superroot-tab-content').forEach(c => c.style.display = 'none');
        const activeContainer = document.getElementById(tabTarget);
        if (activeContainer) activeContainer.style.display = 'block';

        if (tabTarget === 'tab-solicitudes') this.loadSolicitudesPendientes();
        if (tabTarget === 'tab-docentes') this.loadDocentes();
        if (tabTarget === 'tab-system-logs') this.loadSystemLogs();
        if (tabTarget === 'tab-qr-generador') this.loadQROficial();
        if (tabTarget === 'tab-asistencias-logs') this.loadAsistenciasLogs();
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
  // ESTADÍSTICAS DEL DASHBOARD
  // ----------------------------------------------------------------------------
  async loadDashboardStats() {
    try {
      const res = await this.fetchSecure('/api/superroot/dashboard-stats');
      const data = await res.json();
      if (data.success) {
        const totalDocEl = document.getElementById('stat-total-docentes');
        const pendEl = document.getElementById('stat-pendientes');
        const actEl = document.getElementById('stat-activos');
        const marcajesEl = document.getElementById('stat-marcajes-hoy');
        const badgeSolicitudes = document.getElementById('badge-count-solicitudes');

        if (totalDocEl) totalDocEl.textContent = data.stats.totalDocentes;
        if (pendEl) pendEl.textContent = data.stats.pendientes;
        if (actEl) actEl.textContent = data.stats.activos;
        if (marcajesEl) {
          marcajesEl.textContent = `${data.stats.marcajesHoy.entradas}E / ${data.stats.marcajesHoy.salidas}S`;
        }

        if (badgeSolicitudes) {
          badgeSolicitudes.textContent = data.stats.pendientes;
          badgeSolicitudes.style.display = data.stats.pendientes > 0 ? 'inline-block' : 'none';
        }
      }
    } catch (err) {
      console.error('Error cargando stats:', err);
    }
  },

  // ----------------------------------------------------------------------------
  // SOLICITUDES PENDIENTES
  // ----------------------------------------------------------------------------
  async loadSolicitudesPendientes() {
    const container = document.getElementById('solicitudes-pendientes-container');
    if (!container) return;

    container.innerHTML = `<div style="text-align: center; padding: 2rem; color: var(--text-muted);"><p>Cargando solicitudes pendientes...</p></div>`;

    try {
      const res = await this.fetchSecure('/api/superroot/solicitudes');
      const data = await res.json();

      if (!data.success) {
        container.innerHTML = `
          <div class="card" style="padding: 1.5rem; color: var(--status-danger); text-align: center;">
            <p><strong>Error al obtener solicitudes:</strong> ${data.error || 'Acceso no autorizado o fallo del servidor.'}</p>
          </div>
        `;
        return;
      }

      if (!data.solicitudes || data.solicitudes.length === 0) {
        container.innerHTML = `
          <div style="text-align: center; padding: 3rem 1rem; background: #ffffff; border-radius: var(--radius-lg); border: 1px dashed #cbd5e1;">
            <div style="display: flex; justify-content: center; margin-bottom: 0.6rem;">
              <svg class="icon-svg icon-svg-stroke" style="width: 2.8rem; height: 2.8rem; color: var(--status-active);" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
            </div>
            <h3 style="color: var(--unermb-blue-dark); margin-bottom: 0.3rem;">No hay solicitudes pendientes</h3>
            <p style="color: var(--text-muted); font-size: 0.9rem;">Todas las postulaciones de docentes han sido evaluadas.</p>
          </div>
        `;
        return;
      }

      container.innerHTML = '';
      const grid = document.createElement('div');
      grid.className = 'pending-cards-grid';

      data.solicitudes.forEach(docente => {
        const card = document.createElement('div');
        card.className = 'docente-request-card';

        const materiasHtml = (docente.materias && docente.materias.length > 0)
          ? docente.materias.map(m => `
              <span class="materia-tag" title="${m.trayecto} - ${m.trimestre}">
                <strong>${m.codigo || 'PNF'}</strong>: ${m.nombre_materia}
              </span>
            `).join('')
          : '<span style="font-size: 0.75rem; color: var(--text-muted); font-style: italic;">Sin materias aún (se asignarán tras su aprobación)</span>';

        const photoUrl = docente.foto_carnet_url ? (docente.foto_carnet_url.startsWith('http') ? docente.foto_carnet_url : ((typeof API_BASE !== 'undefined' ? API_BASE : '') + docente.foto_carnet_url)) : '/multimedia/uner.png';

        card.innerHTML = `
          <div class="docente-profile-row">
            <img src="${photoUrl}" alt="Foto Carnet" class="docente-photo-thumb" onerror="this.src='/multimedia/uner.png'">
            <div class="docente-profile-data">
              <span class="badge badge-pendiente" style="margin-bottom: 0.3rem;">Solicitud Pendiente</span>
              <div class="docente-fullname">${docente.nombre} ${docente.apellido}</div>
              <div class="docente-meta-item"><strong>C.I.:</strong> ${typeof formatCedula === 'function' ? formatCedula(docente.cedula) : docente.cedula}</div>
              <div class="docente-meta-item"><strong>Email:</strong> ${docente.email}</div>
              <div class="docente-meta-item" style="font-size: 0.72rem; color: var(--text-muted);">
                <strong>Fecha:</strong> ${typeof formatVenezuelaDateTime === 'function' ? formatVenezuelaDateTime(docente.created_at).full : new Date(docente.created_at).toLocaleString()}
              </div>
            </div>
          </div>

          <div style="background: #f8fafc; padding: 0.75rem; border-radius: var(--radius-md); border: 1px solid #e2e8f0;">
            <div style="font-size: 0.78rem; font-weight: 700; color: var(--unermb-blue-dark); margin-bottom: 0.4rem; display: flex; align-items: center; justify-content: space-between;">
              <span>Materias Asignadas:</span>
              <span class="badge" style="background: var(--unermb-blue-dark); color: #fff;">${(docente.materias || []).length} / 5</span>
            </div>
            <div class="docente-materias-tags">
              ${materiasHtml}
            </div>
          </div>

          <div class="docente-actions-row">
            <button class="btn btn-success btn-sm btn-block" onclick="SuperrootModule.aprobarDocente(${docente.id}, '${docente.nombre} ${docente.apellido}')">
              <span>Aprobar Docente</span>
            </button>
            <button class="btn btn-danger btn-sm btn-block" onclick="SuperrootModule.rechazarDocente(${docente.id}, '${docente.nombre} ${docente.apellido}')">
              <span>Rechazar</span>
            </button>
          </div>
        `;

        grid.appendChild(card);
      });

      container.appendChild(grid);
    } catch (err) {
      container.innerHTML = `<p style="color: var(--status-danger);">Error al cargar solicitudes: ${err.message}</p>`;
    }
  },

  async aprobarDocente(id, nombre) {
    if (!confirm(`¿Confirma que desea APROBAR al docente ${nombre}? Pasará a estado ACTIVO.`)) return;

    try {
      const res = await this.fetchSecure(`/api/superroot/solicitudes/${id}/aprobar`, { method: 'PATCH' });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        this.loadSolicitudesPendientes();
        this.loadDashboardStats();
      } else {
        showToast(data.error || 'Error al aprobar.', 'error');
      }
    } catch {
      showToast('Error de conexión al aprobar.', 'error');
    }
  },

  async rechazarDocente(id, nombre) {
    if (!confirm(`¿Confirma que desea RECHAZAR la solicitud de ${nombre}?`)) return;

    try {
      const res = await this.fetchSecure(`/api/superroot/solicitudes/${id}/rechazar`, { method: 'PATCH' });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'warning');
        this.loadSolicitudesPendientes();
        this.loadDashboardStats();
      } else {
        showToast(data.error || 'Error al rechazar.', 'error');
      }
    } catch {
      showToast('Error de conexión al rechazar.', 'error');
    }
  },

  // ----------------------------------------------------------------------------
  // GESTIÓN DE USUARIOS / DOCENTES (Requerimiento 2.A.1)
  // ----------------------------------------------------------------------------
  async loadDocentes() {
    const tableBody = document.getElementById('docentes-table-body');
    const filterEstado = document.getElementById('filter-docente-estado')?.value || '';
    const searchVal = document.getElementById('search-docente-input')?.value || '';

    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem;">Cargando listado de usuarios...</td></tr>`;

    try {
      const query = new URLSearchParams({ estado: filterEstado, buscar: searchVal, incluir_admins: 'true' }).toString();
      const res = await this.fetchSecure(`/api/superroot/docentes?${query}`);
      const data = await res.json();

      if (!data.success || data.docentes.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem; color: var(--text-muted);">No se encontraron usuarios.</td></tr>`;
        return;
      }

      tableBody.innerHTML = data.docentes.map(doc => {
        let badgeClass = 'badge-pendiente';
        if (doc.estado === 'ACTIVO') badgeClass = 'badge-activo';
        if (doc.estado === 'RECHAZADO') badgeClass = 'badge-rechazado';
        if (doc.estado === 'INACTIVO') badgeClass = 'badge-inactivo';

        const materiasList = doc.materias.map(m => `
          <span class="materia-tag" title="${m.trayecto} - ${m.trimestre}">${m.codigo || m.nombre_materia}</span>
        `).join(' ');

        const esAdmin = doc.rol === 'SUPERROOT';

        return `
          <tr>
            <td style="width: 50px;">
              <img src="${doc.foto_carnet_url}" alt="Foto" style="width: 42px; height: 50px; object-fit: cover; border-radius: 4px; border: 1px solid var(--unermb-blue-dark);" onerror="this.src='/multimedia/uner.png'">
            </td>
            <td>
              <strong>${doc.nombre} ${doc.apellido}</strong><br>
              <span style="font-size: 0.78rem; color: var(--text-muted);">${doc.email}</span>
              ${esAdmin ? '<span class="badge" style="background:#0f172a; color:#fff; font-size:0.65rem; margin-left:4px;">ADMIN</span>' : ''}
            </td>
            <td><strong>${typeof formatCedula === 'function' ? formatCedula(doc.cedula) : doc.cedula}</strong></td>
            <td>
              <div style="display: flex; flex-wrap: wrap; gap: 4px; max-width: 250px;">
                ${materiasList || '<span style="color: var(--text-muted); font-size:0.75rem;">Sin materias</span>'}
              </div>
            </td>
            <td>
              <span class="badge" style="background: #eff6ff; color: var(--unermb-blue-dark); font-weight:700;">
                ${doc.total_bloques_horario || 0} Bloques
              </span>
            </td>
            <td><span class="badge ${badgeClass}">${doc.estado}</span></td>
            <td>
              <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                ${!esAdmin ? `
                  <button class="btn btn-primary btn-sm" style="padding: 0.3rem 0.5rem; font-size: 0.75rem; background: var(--unermb-blue-accent);" onclick="SuperrootModule.abrirGestionHorario(${doc.id})">
                    Asignar Horario
                  </button>
                  ${doc.estado === 'ACTIVO'
                    ? `<button class="btn btn-secondary btn-sm" style="padding: 0.3rem 0.5rem; font-size: 0.75rem;" onclick="SuperrootModule.cambiarEstadoDocente(${doc.id}, 'INACTIVO')">Desactivar</button>`
                    : `<button class="btn btn-success btn-sm" style="padding: 0.3rem 0.5rem; font-size: 0.75rem;" onclick="SuperrootModule.cambiarEstadoDocente(${doc.id}, 'ACTIVO')">Activar</button>`
                  }
                  <button class="btn btn-danger btn-sm" style="padding: 0.3rem 0.5rem; font-size: 0.75rem;" onclick="SuperrootModule.eliminarUsuario(${doc.id}, '${doc.nombre} ${doc.apellido}')" title="Eliminar usuario definitivamente">
                    Eliminar
                  </button>
                ` : `
                  <span style="font-size: 0.75rem; color: var(--text-muted); font-style: italic;">Cuenta Principal</span>
                `}
              </div>
            </td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      tableBody.innerHTML = `<tr><td colspan="7" style="color: var(--status-danger); text-align: center;">Error: ${err.message}</td></tr>`;
    }
  },

  async cambiarEstadoDocente(id, nuevoEstado) {
    try {
      const res = await this.fetchSecure(`/api/superroot/docentes/${id}/estado`, {
        method: 'PATCH',
        body: JSON.stringify({ nuevo_estado: nuevoEstado })
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        this.loadDocentes();
        this.loadDashboardStats();
      } else {
        showToast(data.error, 'error');
      }
    } catch {
      showToast('Error de conexión.', 'error');
    }
  },

  async eliminarUsuario(id, nombre) {
    if (!confirm(`¡ADVERTENCIA DE SEGURIDAD!\n\n¿Está completamente seguro de ELIMINAR permanentemente al usuario ${nombre}?\nEsta acción borrará definitivamente su cuenta, sus asistencias y todos sus horarios asignados.`)) {
      return;
    }

    try {
      const res = await this.fetchSecure(`/api/superroot/usuarios/${id}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        this.loadDocentes();
        this.loadDashboardStats();
      } else {
        showToast(data.error || 'No se pudo eliminar el usuario.', 'error');
      }
    } catch (err) {
      showToast('Error de conexión al eliminar usuario.', 'error');
    }
  },

  // ----------------------------------------------------------------------------
  // CONTROL DE LOGS DE AUDITORÍA DEL SISTEMA (Requerimiento 2.A.2)
  // ----------------------------------------------------------------------------
  async loadSystemLogs() {
    const tableBody = document.getElementById('system-logs-table-body');
    const filterEvento = document.getElementById('filter-log-evento')?.value || '';
    const filterFecha = document.getElementById('filter-log-fecha')?.value || '';
    const searchVal = document.getElementById('search-log-input')?.value || '';

    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 2rem;">Cargando registros de auditoría...</td></tr>`;

    try {
      const query = new URLSearchParams({ tipo_evento: filterEvento, fecha: filterFecha, buscar: searchVal }).toString();
      const res = await this.fetchSecure(`/api/superroot/system-logs?${query}`);
      const data = await res.json();

      if (!data.success || data.logs.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; padding: 2rem; color: var(--text-muted);">No hay eventos de auditoría para los criterios seleccionados.</td></tr>`;
        return;
      }

      tableBody.innerHTML = data.logs.map(log => {
        let badgeColor = '#0266cc';
        if (log.tipo_evento.includes('ELIMINAR') || log.tipo_evento.includes('RECHAZAR')) badgeColor = '#dc2626';
        if (log.tipo_evento.includes('APROBAR') || log.tipo_evento.includes('ASIGNAR')) badgeColor = '#059669';
        if (log.tipo_evento.includes('LOGIN')) badgeColor = '#002147';

        return `
          <tr>
            <td><span style="font-family: monospace; font-size: 0.75rem;">#${log.id}</span></td>
            <td>
              <span class="log-event-badge" style="background: ${badgeColor}20; color: ${badgeColor}; border: 1px solid ${badgeColor}40;">
                ${log.tipo_evento}
              </span>
            </td>
            <td>
              <strong>${log.nombre ? `${log.nombre} ${log.apellido}` : 'Sistema / Anónimo'}</strong><br>
              <span style="font-size: 0.72rem; color: var(--text-muted);">${log.cedula ? (typeof formatCedula === 'function' ? formatCedula(log.cedula) : log.cedula) : ''}</span>
            </td>
            <td>
              <div style="font-size: 0.82rem; word-break: break-word; max-width: 380px;">
                ${log.descripcion}
              </div>
            </td>
            <td>
              <span style="font-family: monospace; font-size: 0.75rem; color: var(--text-muted);">${log.ip_address || '127.0.0.1'}</span>
            </td>
            <td>
              <span style="font-size: 0.78rem; font-weight: 700; color: var(--unermb-blue-dark);">
                ${typeof formatVenezuelaDateTime === 'function' ? formatVenezuelaDateTime(log.created_at).time : new Date(log.created_at).toLocaleTimeString()}
              </span><br>
              <span style="font-size: 0.72rem; color: var(--text-muted);">
                ${typeof formatVenezuelaDateTime === 'function' ? formatVenezuelaDateTime(log.created_at).date : new Date(log.created_at).toLocaleDateString()}
              </span>
            </td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      tableBody.innerHTML = `<tr><td colspan="6" style="color: var(--status-danger); text-align: center;">Error: ${err.message}</td></tr>`;
    }
  },

  // ----------------------------------------------------------------------------
  // ASIGNACIÓN DE HORARIOS POR DOCENTE CON CASCADA PENSUM (Requerimiento 2.A.3)
  // ----------------------------------------------------------------------------
  async abrirGestionHorario(docenteId) {
    this.currentDocenteHorarioId = docenteId;
    const modal = document.getElementById('modal-docente-horario');
    if (!modal) return;

    modal.style.display = 'flex';
    await this.cargarHorarioDocenteDetalle(docenteId);
  },

  cerrarGestionHorario() {
    const modal = document.getElementById('modal-docente-horario');
    if (modal) modal.style.display = 'none';
    this.currentDocenteHorarioId = null;
    this.loadDocentes();
  },

  async cargarHorarioDocenteDetalle(docenteId) {
    const infoContainer = document.getElementById('horario-docente-info');
    const tableContainer = document.getElementById('horario-docente-table-wrapper');
    const cascadeContainer = document.getElementById('horario-cascade-selector-wrapper');

    if (infoContainer) {
      infoContainer.innerHTML = `<div style="text-align: center; padding: 1rem; color: var(--text-muted);">Cargando perfil del docente...</div>`;
    }

    try {
      const res = await this.fetchSecure(`/api/superroot/docentes/${docenteId}/horario`);
      const data = await res.json();

      if (!data.success) {
        showToast(data.error || 'Error al obtener horario.', 'error');
        return;
      }

      const doc = data.docente;
      const materiasGen = data.materiasGenerales || [];
      const horarios = data.horarios || [];
      const bloques = data.bloquesHorariosDisponibles || [];
      const dias = data.diasSemanaDisponibles || [];

      // 1. Mostrar información del docente y resumen
      infoContainer.innerHTML = `
        <div style="display: flex; align-items: center; gap: 1rem; background: #f8fafc; padding: 0.85rem; border-radius: var(--radius-md); border: 1px solid #e2e8f0; margin-bottom: 1rem;">
          <img src="${doc.foto_carnet_url}" alt="Foto" style="width: 54px; height: 64px; object-fit: cover; border-radius: 6px; border: 1px solid var(--unermb-blue-dark);" onerror="this.src='/multimedia/uner.png'">
          <div style="flex: 1; min-width: 0;">
            <h3 style="font-size: 1.15rem; color: var(--unermb-blue-dark); margin-bottom: 0.15rem;">${doc.nombre} ${doc.apellido}</h3>
            <div style="font-size: 0.8rem; color: var(--text-secondary); margin-bottom: 0.25rem;">C.I.: ${typeof formatCedula === 'function' ? formatCedula(doc.cedula) : doc.cedula} &bull; ${doc.email}</div>
            <div style="font-size: 0.75rem; color: var(--text-muted);">
              <strong>Materias Asignadas:</strong> ${materiasGen.length} / 5 &bull; <strong>Bloques Horario:</strong> ${horarios.length}
            </div>
          </div>
          <span class="badge badge-activo">${horarios.length} Bloques Asignados</span>
        </div>
      `;

      // 1.5. Renderizar Gestión Directa de Materias del Docente (Exclusivo Superroot)
      const materiasContainer = document.getElementById('horario-docente-materias-wrapper');
      if (materiasContainer) {
        let materiasHtml = `
          <div style="background: #ffffff; border: 1px solid #bfdbfe; border-radius: var(--radius-md); padding: 1rem; margin-bottom: 1.25rem; box-shadow: var(--shadow-sm);">
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.6rem; flex-wrap: wrap; gap: 0.5rem;">
              <h4 style="font-size: 0.95rem; color: var(--unermb-blue-dark); margin: 0;">
                Unidades Curriculares Asignadas al Docente (${materiasGen.length} / 5)
              </h4>
              <span class="badge" style="background: var(--unermb-blue-primary); color: #fff;">Gestión Superroot</span>
            </div>
        `;

        if (materiasGen.length === 0) {
          materiasHtml += `
            <div style="font-size: 0.82rem; color: var(--text-muted); padding: 0.75rem; background: #f8fafc; border-radius: var(--radius-sm); border: 1px dashed #cbd5e1; margin-bottom: 0.75rem;">
              Este docente aún no tiene materias asignadas. Use el selector en cascada para asignarle unidades curriculares del pensum oficial.
            </div>
          `;
        } else {
          materiasHtml += `
            <div style="display: flex; flex-wrap: wrap; gap: 0.5rem; margin-bottom: 0.85rem;">
              ${materiasGen.map(m => `
                <span class="materia-tag-editable" style="display: inline-flex; align-items: center; gap: 0.45rem; background: #eff6ff; color: #1e40af; padding: 0.4rem 0.7rem; border-radius: 6px; font-size: 0.8rem; font-weight: 600; border: 1px solid #bfdbfe;">
                  <span><strong>${m.codigo || 'PNF'}:</strong> ${m.nombre_materia} (${m.trayecto} - ${m.trimestre})</span>
                  <button type="button" onclick="SuperrootModule.eliminarMateriaDocente(${docenteId}, ${m.id}, '${m.nombre_materia.replace(/'/g, "\\'")}')" title="Quitar esta materia del docente" style="color: #dc2626; font-weight: 800; cursor: pointer; border: none; background: #fee2e2; border-radius: 50%; width: 20px; height: 20px; display: inline-flex; align-items: center; justify-content: center; font-size: 0.85rem; line-height: 1;">&times;</button>
                </span>
              `).join('')}
            </div>
          `;
        }

        if (materiasGen.length < 5) {
          materiasHtml += `
            <div style="border-top: 1px dashed #cbd5e1; padding-top: 0.75rem; margin-top: 0.5rem;">
              <div style="font-size: 0.78rem; font-weight: 700; color: var(--unermb-blue-dark); margin-bottom: 0.4rem; text-transform: uppercase;">
                + Asignar Materia del Pensum Oficial al Docente:
              </div>
              <div id="materia-direct-cascade-slot"></div>
              <button type="button" id="btn-asignar-materia-direct" class="btn btn-secondary btn-sm" style="margin-top: 0.6rem;">
                <span>+ Guardar Materia en Perfil del Docente</span>
              </button>
            </div>
          `;
        } else {
          materiasHtml += `
            <div style="font-size: 0.78rem; color: var(--status-pending); font-weight: 600; padding-top: 0.3rem;">
              Límite alcanzado: El docente ya tiene asignadas las 5 materias máximas permitidas por normativa.
            </div>
          `;
        }

        materiasHtml += `</div>`;
        materiasContainer.innerHTML = materiasHtml;

        // Renderizar fila de cascada para asignar materia directa
        if (materiasGen.length < 5 && window.PensumCascade) {
          let directSelectedMateriaId = null;
          const directSlot = document.getElementById('materia-direct-cascade-slot');
          if (directSlot) {
            const alreadyIds = materiasGen.map(m => m.id);
            const cascadeRow = window.PensumCascade.createCascadeRow({
              rowIndex: 99,
              selectedMateriaId: null,
              alreadySelectedIds: alreadyIds,
              canRemove: false,
              onChange: (mId) => {
                directSelectedMateriaId = mId;
              }
            });
            directSlot.appendChild(cascadeRow);

            const btnDirect = document.getElementById('btn-asignar-materia-direct');
            if (btnDirect) {
              btnDirect.addEventListener('click', async () => {
                if (!directSelectedMateriaId) {
                  showToast('Seleccione una materia mediante el selector en cascada.', 'warning');
                  return;
                }
                await SuperrootModule.asignarMateriaDocente(docenteId, directSelectedMateriaId);
              });
            }
          }
        }
      }

      // 2. Renderizar Cuadrícula Semanal (5 días x 5 bloques fijos)
      let tableHtml = `
        <div class="schedule-table-container">
          <table class="schedule-table">
            <thead>
              <tr>
                <th class="time-header-cell">Franja Horaria</th>
                ${dias.map(d => `<th>${d}</th>`).join('')}
              </tr>
            </thead>
            <tbody>
      `;

      bloques.forEach(bloque => {
        tableHtml += `
          <tr>
            <td class="time-header-cell">
              <span style="font-size: 0.75rem; font-weight: 700; color: var(--unermb-blue-dark); display: block;">${bloque.etiqueta}</span>
              <span style="font-size: 0.68rem; color: var(--text-muted);">Bloque ${bloque.numero}</span>
            </td>
        `;

        dias.forEach(dia => {
          const slot = horarios.find(h => h.dia_semana === dia && h.bloque_numero === bloque.numero);

          if (slot) {
            tableHtml += `
              <td>
                <div class="schedule-slot-card">
                  <div class="schedule-slot-subject">${slot.materia_nombre}</div>
                  <div class="schedule-slot-meta">
                    <strong>PNF:</strong> ${slot.pnf || 'Informática'}<br>
                    <strong>${slot.trayecto}</strong><br>
                    <strong>Trimestre:</strong> ${slot.trimestre}
                  </div>
                  <button type="button" class="schedule-slot-delete-btn" onclick="SuperrootModule.eliminarHorario(${slot.id})" title="Eliminar este bloque">
                    &times; Quitar
                  </button>
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

      tableHtml += `</tbody></table></div>`;
      tableContainer.innerHTML = tableHtml;

      // 3. Renderizar Selector en Cascada para Asignar Nueva Clase
      this.renderFormularioAsignarHorario(cascadeContainer, docenteId, dias, bloques);
    } catch (err) {
      console.error('Error cargando detalle de horario:', err);
    }
  },

  renderFormularioAsignarHorario(container, docenteId, dias, bloques) {
    if (!container || !window.PensumCascade) return;

    let selectedMateriaId = null;

    container.innerHTML = `
      <div style="background: #ffffff; border: 1px solid #bfdbfe; border-radius: var(--radius-md); padding: 1.15rem; margin-top: 1rem;">
        <h4 style="font-size: 0.95rem; color: var(--unermb-blue-dark); margin-bottom: 0.3rem;">
          Asignar Nueva Clase al Horario (Selector en Cascada pensum2026.txt)
        </h4>
        <p style="font-size: 0.78rem; color: var(--text-muted); margin-bottom: 0.85rem;">
          Siga la cascada: 1. Trayecto &rarr; 2. Trimestre &rarr; 3. Materia &rarr; Seleccione Día y Bloque Horario fijo.
        </p>
        
        <div id="modal-cascade-row-slot"></div>

        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; margin-top: 0.75rem;">
          <div class="cascade-group">
            <label>4. Día de la Semana (Lunes a Viernes)</label>
            <select id="horario-dia-select" class="form-select" required>
              <option value="">-- Seleccionar Día --</option>
              ${dias.map(d => `<option value="${d}">${d}</option>`).join('')}
            </select>
          </div>

          <div class="cascade-group">
            <label>5. Bloque Horario Fijo</label>
            <select id="horario-bloque-select" class="form-select" required>
              <option value="">-- Seleccionar Bloque Horario --</option>
              ${bloques.map(b => `<option value="${b.numero}">Bloque ${b.numero}: ${b.etiqueta}</option>`).join('')}
            </select>
          </div>
        </div>

        <button type="button" id="btn-submit-asignar-horario" class="btn btn-primary btn-block" style="margin-top: 1rem; background: var(--unermb-blue-accent);">
          <span>Asignar Clase a la Franja Horaria</span>
        </button>
      </div>
    `;

    // Insertar fila de cascada
    const slotCascade = document.getElementById('modal-cascade-row-slot');
    const cascadeRow = window.PensumCascade.createCascadeRow({
      rowIndex: 0,
      selectedMateriaId: null,
      alreadySelectedIds: [],
      canRemove: false,
      onChange: (mId) => {
        selectedMateriaId = mId;
      }
    });
    slotCascade.appendChild(cascadeRow);

    const btnSubmit = document.getElementById('btn-submit-asignar-horario');
    btnSubmit.addEventListener('click', async () => {
      const diaSemana = document.getElementById('horario-dia-select').value;
      const bloqueNumero = document.getElementById('horario-bloque-select').value;

      if (!selectedMateriaId) {
        showToast('Debe completar la selección en cascada de la Materia.', 'warning');
        return;
      }
      if (!diaSemana) {
        showToast('Seleccione el día de la semana (Lunes a Viernes).', 'warning');
        return;
      }
      if (!bloqueNumero) {
        showToast('Seleccione el bloque horario fijo (1 al 5).', 'warning');
        return;
      }

      btnSubmit.disabled = true;
      btnSubmit.innerHTML = `<span>Asignando...</span>`;

      try {
        const res = await this.fetchSecure(`/api/superroot/docentes/${docenteId}/horario`, {
          method: 'POST',
          body: JSON.stringify({
            materia_id: selectedMateriaId,
            dia_semana: diaSemana,
            bloque_numero: parseInt(bloqueNumero, 10)
          })
        });

        const data = await res.json();

        if (data.success) {
          showToast(data.message, 'success');
          await this.cargarHorarioDocenteDetalle(docenteId);
        } else {
          showToast(data.error || 'Conflicto al asignar horario.', 'error', 6000);
        }
      } catch (err) {
        showToast('Error de comunicación con el servidor.', 'error');
      } finally {
        btnSubmit.disabled = false;
        btnSubmit.innerHTML = `<span>Asignar Clase a la Franja Horaria</span>`;
      }
    });
  },

  async eliminarHorario(horarioId) {
    if (!confirm('¿Desea quitar esta clase del horario del docente?')) return;

    try {
      const res = await this.fetchSecure(`/api/superroot/horarios/${horarioId}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        if (this.currentDocenteHorarioId) {
          await this.cargarHorarioDocenteDetalle(this.currentDocenteHorarioId);
        }
      } else {
        showToast(data.error || 'Error al eliminar horario.', 'error');
      }
    } catch {
      showToast('Error de conexión.', 'error');
    }
  },

  async asignarMateriaDocente(docenteId, materiaId) {
    try {
      const res = await this.fetchSecure(`/api/superroot/docentes/${docenteId}/materias`, {
        method: 'POST',
        body: JSON.stringify({ materia_id: materiaId })
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        await this.cargarHorarioDocenteDetalle(docenteId);
        this.loadDocentes();
      } else {
        showToast(data.error || 'Error al asignar materia.', 'error');
      }
    } catch {
      showToast('Error de conexión al asignar materia.', 'error');
    }
  },

  async eliminarMateriaDocente(docenteId, materiaId, nombreMateria) {
    if (!confirm(`¿Confirma que desea quitar la materia "${nombreMateria}" de este docente?\nTambién se removerán las clases del horario asociadas a esta materia.`)) return;

    try {
      const res = await this.fetchSecure(`/api/superroot/docentes/${docenteId}/materias/${materiaId}`, {
        method: 'DELETE'
      });
      const data = await res.json();
      if (data.success) {
        showToast(data.message, 'success');
        await this.cargarHorarioDocenteDetalle(docenteId);
        this.loadDocentes();
      } else {
        showToast(data.error || 'Error al remover materia.', 'error');
      }
    } catch {
      showToast('Error de conexión al remover materia.', 'error');
    }
  },

  // ----------------------------------------------------------------------------
  // GENERADOR DE CÓDIGO QR OFICIAL
  // ----------------------------------------------------------------------------
  async loadQROficial() {
    const qrContainer = document.getElementById('qr-official-display');
    if (!qrContainer) return;

    try {
      const res = await this.fetchSecure('/api/superroot/generar-qr');
      const data = await res.json();

      if (data.success) {
        this.qrData = data;
        qrContainer.innerHTML = `
          <div class="qr-official-showcase">
            <div style="display: flex; align-items: center; justify-content: center; gap: 1rem; margin-bottom: 0.75rem;">
              <img src="/multimedia/uner.png" alt="UNERMB" style="height: 48px;">
              <div style="height: 36px; width: 1px; background: #cbd5e1;"></div>
              <img src="/multimedia/pnf.png" alt="PNF" style="height: 48px;">
            </div>
            
            <h3 style="font-size: 1.1rem; color: var(--unermb-blue-dark); text-transform: uppercase;">
              Universidad Nacional Experimental "Rafael María Baralt"
            </h3>
            <p style="font-size: 0.85rem; font-weight: 700; color: var(--unermb-blue-accent);">
              PNF EN INFORMÁTICA - PUNTO OFICIAL DE ASISTENCIA DOCENTE
            </p>

            <img src="${data.qrDataURL}" alt="Código QR Institucional" class="qr-code-img">

            <div style="font-size: 0.78rem; color: var(--text-secondary); background: #f8fafc; padding: 0.75rem; border-radius: var(--radius-md); border: 1px solid #e2e8f0; margin-top: 1rem;">
              <div><strong>Seguridad Criptográfica:</strong> HMAC-SHA256 Token Oficial Activo</div>
              <div><strong>Validez:</strong> Marcaje con Verificación Estricta de Horarios</div>
            </div>

            <div style="display: flex; justify-content: center; gap: 0.75rem; margin-top: 1.25rem;">
              <button class="btn btn-primary btn-sm" onclick="window.print()">
                <span>Imprimir Cartel Oficial</span>
              </button>
              <button class="btn btn-secondary btn-sm" onclick="SuperrootModule.loadQROficial()">
                <span>Regenerar Token</span>
              </button>
            </div>
          </div>
        `;
      }
    } catch (err) {
      qrContainer.innerHTML = `<p style="color: var(--status-danger);">Error generando QR: ${err.message}</p>`;
    }
  },

  // ----------------------------------------------------------------------------
  // LOGS Y AUDITORÍA DE ASISTENCIA CON PUNTUALIDAD
  // ----------------------------------------------------------------------------
  async loadAsistenciasLogs() {
    const tableBody = document.getElementById('asistencias-table-body');
    const filterFecha = document.getElementById('filter-asistencia-fecha')?.value || '';
    const filterTipo = document.getElementById('filter-asistencia-tipo')?.value || '';
    const filterPuntualidad = document.getElementById('filter-asistencia-puntualidad')?.value || '';

    if (!tableBody) return;
    tableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem;">Cargando registros de asistencia...</td></tr>`;

    try {
      const query = new URLSearchParams({ fecha: filterFecha, tipo: filterTipo, puntualidad: filterPuntualidad }).toString();
      const res = await this.fetchSecure(`/api/superroot/asistencias?${query}`);
      const data = await res.json();

      if (!data.success || data.logs.length === 0) {
        tableBody.innerHTML = `<tr><td colspan="7" style="text-align: center; padding: 2rem; color: var(--text-muted);">No hay marcajes registrados para los filtros seleccionados.</td></tr>`;
        return;
      }

      this.currentLogs = data.logs;

      tableBody.innerHTML = data.logs.map(log => {
        const isEntrada = log.tipo === 'ENTRADA';
        const badgeClass = isEntrada ? 'badge-entrada' : 'badge-salida';

        let puntualidadBadge = '';
        if (log.puntualidad_estado === 'A_TIEMPO') {
          puntualidadBadge = `<span class="badge badge-a-tiempo">A TIEMPO</span>`;
        } else if (log.puntualidad_estado === 'TARDE') {
          puntualidadBadge = `<span class="badge badge-tarde">TARDE (+${log.minutos_retraso} min)</span>`;
        } else if (log.puntualidad_estado === 'FUERA_DE_HORARIO') {
          puntualidadBadge = `<span class="badge badge-fuera-horario">FUERA HORARIO</span>`;
        } else if (log.puntualidad_estado === 'SIN_HORARIO') {
          puntualidadBadge = `<span class="badge badge-sin-horario">SIN HORARIO</span>`;
        }

        return `
          <tr>
            <td style="width: 48px;">
              <img src="${log.foto_carnet_url}" alt="Docente" style="width: 38px; height: 46px; object-fit: cover; border-radius: 4px; border: 1px solid var(--unermb-blue-dark);" onerror="this.src='/multimedia/uner.png'">
            </td>
            <td>
              <strong>${log.nombre} ${log.apellido}</strong><br>
              <span style="font-size: 0.75rem; color: var(--text-muted);">C.I.: ${typeof formatCedula === 'function' ? formatCedula(log.cedula) : log.cedula}</span>
            </td>
            <td>
              <strong>${typeof formatVenezuelaDateTime === 'function' ? formatVenezuelaDateTime(log.fecha_hora).date : new Date(log.fecha_hora).toLocaleDateString()}</strong><br>
              <span style="font-size: 0.85rem; color: var(--unermb-blue-dark); font-weight: 700;">
                ${typeof formatVenezuelaDateTime === 'function' ? formatVenezuelaDateTime(log.fecha_hora).time : new Date(log.fecha_hora).toLocaleTimeString()}
              </span>
            </td>
            <td><span class="badge ${badgeClass}">${log.tipo}</span></td>
            <td>
              ${puntualidadBadge}<br>
              <span style="font-size: 0.72rem; color: var(--text-muted);">
                ${log.materia_detectada ? log.materia_detectada : ''}
              </span>
            </td>
            <td><span style="font-size: 0.75rem; color: var(--text-muted); font-family: monospace;">${log.ip_address}</span></td>
          </tr>
        `;
      }).join('');
    } catch (err) {
      tableBody.innerHTML = `<tr><td colspan="7" style="color: var(--status-danger); text-align: center;">Error: ${err.message}</td></tr>`;
    }
  },

  exportarLogsCSV() {
    if (!this.currentLogs || this.currentLogs.length === 0) {
      showToast('No hay datos disponibles para exportar.', 'warning');
      return;
    }

    let csvContent = '\uFEFF';
    csvContent += 'ID,Docente,Cedula,Email,Fecha_Hora,Tipo,Puntualidad,Minutos_Retraso,Materia,IP\n';

    this.currentLogs.forEach(l => {
      csvContent += `"${l.id}","${l.nombre} ${l.apellido}","${l.cedula}","${l.email}","${l.fecha_hora}","${l.tipo}","${l.puntualidad_estado || ''}","${l.minutos_retraso || 0}","${l.materia_detectada || ''}","${l.ip_address}"\n`;
    });

    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `asistencias_unermb_pnf_${new Date().toISOString().split('T')[0]}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Reporte exportado exitosamente a CSV.', 'success');
  },

  // ----------------------------------------------------------------------------
  // GESTIÓN Y VISUALIZACIÓN DE CÓDIGO QR OFICIAL INSTITUCIONAL
  // ----------------------------------------------------------------------------
  async loadQROficial() {
    const container = document.getElementById('qr-official-display');
    if (!container) return;

    container.innerHTML = `
      <div style="text-align: center; padding: 3rem; color: var(--text-muted);">
        <p>Generando código QR oficial firmado institucionalmente...</p>
      </div>
    `;

    try {
      const res = await this.fetchSecure('/api/superroot/generar-qr');
      const data = await res.json();

      if (!data.success) {
        container.innerHTML = `
          <div class="card" style="text-align: center; padding: 2rem; color: var(--status-danger);">
            <p><strong>Error al obtener el Código QR:</strong> ${data.error || 'No se pudo generar el código institucional.'}</p>
          </div>
        `;
        return;
      }

      this.qrData = data;
      const validoDate = data.valido_hasta ? new Date(data.valido_hasta).toLocaleDateString() : 'Vigente';

      container.innerHTML = `
        <div class="card" style="max-width: 620px; margin: 0 auto; text-align: center; padding: 2.25rem 2rem;">
          <div style="display: flex; align-items: center; justify-content: center; gap: 0.75rem; margin-bottom: 0.5rem;">
            <img src="/multimedia/uner.png" alt="UNERMB" style="height: 48px; object-fit: contain;">
            <img src="/multimedia/pnf.png" alt="PNF" style="height: 48px; object-fit: contain;">
          </div>
          <h2 style="color: var(--unermb-blue-dark); font-size: 1.35rem; margin-bottom: 0.25rem;">UNERMB - PNF EN INFORMÁTICA</h2>
          <p style="color: var(--text-muted); font-size: 0.88rem; margin-bottom: 1.5rem;">Control Institucional de Asistencia y Puntualidad Docente</p>

          <div style="display: inline-block; padding: 1.25rem; background: #ffffff; border-radius: var(--radius-lg); border: 2px solid var(--unermb-blue-dark); box-shadow: 0 12px 30px -10px rgba(0, 43, 102, 0.2); margin-bottom: 1.5rem;">
            <img src="${data.qrDataUrl}" alt="Código QR Oficial" id="qr-image-render" style="width: 280px; height: 280px; display: block; border-radius: 6px; margin: 0 auto;">
          </div>

          <div style="display: flex; gap: 0.5rem; justify-content: center; align-items: center; flex-wrap: wrap; margin-bottom: 1.5rem;">
            <span class="badge" style="background: #eff6ff; color: var(--unermb-blue-dark); font-family: monospace; font-size: 0.82rem; padding: 0.4rem 0.75rem; border: 1px solid #bfdbfe;">
              Token: ${data.token}
            </span>
            <span class="badge badge-activo" style="padding: 0.4rem 0.75rem;">
              Activo hasta: ${validoDate}
            </span>
          </div>

          <div style="display: flex; gap: 0.75rem; justify-content: center; flex-wrap: wrap;">
            <button class="btn btn-primary" onclick="SuperrootModule.descargarQRPNG()">
              <span>Descargar Imagen (PNG)</span>
            </button>
            <button class="btn btn-secondary" onclick="SuperrootModule.imprimirQR()">
              <span>Imprimir Cartel</span>
            </button>
            <button class="btn btn-danger btn-sm" onclick="SuperrootModule.renovarQR()" title="Generar un nuevo token y revocar el anterior">
              <span>Renovar Token QR</span>
            </button>
          </div>
        </div>
      `;
    } catch (err) {
      container.innerHTML = `<div class="card" style="text-align: center; padding: 2rem; color: var(--status-danger);"><p>Error al conectar: ${err.message}</p></div>`;
    }
  },

  descargarQRPNG() {
    if (!this.qrData || !this.qrData.qrDataUrl) {
      showToast('No hay código QR disponible para descargar.', 'warning');
      return;
    }
    const link = document.createElement('a');
    link.href = this.qrData.qrDataUrl;
    link.download = `QR_OFICIAL_UNERMB_PNF_INFORMATICA_${new Date().toISOString().split('T')[0]}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    showToast('Código QR descargado exitosamente.', 'success');
  },

  imprimirQR() {
    if (!this.qrData || !this.qrData.qrDataUrl) {
      showToast('No hay código QR para imprimir.', 'warning');
      return;
    }
    const win = window.open('', '_blank');
    win.document.write(`
      <!DOCTYPE html>
      <html>
      <head>
        <title>Código QR Oficial - UNERMB PNF Informática</title>
        <style>
          body { font-family: Arial, sans-serif; text-align: center; padding: 40px; margin: 0; }
          .container { border: 4px solid #002b66; border-radius: 12px; padding: 30px; max-width: 600px; margin: 0 auto; }
          h1 { color: #002b66; font-size: 22px; margin-bottom: 5px; }
          h2 { color: #333; font-size: 16px; margin-top: 0; font-weight: normal; }
          img.qr { width: 340px; height: 340px; margin: 20px 0; }
          .footer { font-size: 14px; color: #555; margin-top: 15px; }
          .token { font-family: monospace; font-weight: bold; background: #eee; padding: 4px 8px; border-radius: 4px; }
        </style>
      </head>
      <body>
        <div class="container">
          <h1>UNIVERSIDAD NACIONAL EXPERIMENTAL "RAFAEL MARÍA BARALT"</h1>
          <h2>PROGRAMA NACIONAL DE FORMACIÓN (PNF) EN INFORMÁTICA</h2>
          <hr style="border: 0; border-top: 2px solid #002b66; margin: 20px 0;">
          <h3 style="margin: 0; color: #002b66;">CONTROL OFICIAL DE ASISTENCIA DOCENTE</h3>
          <p style="margin: 5px 0 20px; font-size: 14px; color: #666;">Escanee este código QR desde el portal docente para registrar su entrada o salida.</p>
          <img class="qr" src="${this.qrData.qrDataUrl}" alt="QR Asistencia">
          <div class="footer">
            <p>Token Institucional: <span class="token">${this.qrData.token}</span></p>
            <p style="font-size: 12px; color: #888;">Seguridad Criptográfica HMAC SHA-256 | Válido para el año lectivo 2026</p>
          </div>
        </div>
        <script>
          window.onload = function() { window.print(); }
        </script>
      </body>
      </html>
    `);
    win.document.close();
  },

  async renovarQR() {
    if (!confirm('¿Confirma que desea RENOVAR el Token QR? El código anterior quedará inválido y los docentes deberán escanear el nuevo.')) return;

    try {
      const res = await this.fetchSecure('/api/superroot/renovar-qr', { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        showToast('¡Token QR Institucional renovado exitosamente!', 'success');
        this.loadQROficial();
      } else {
        showToast(data.error || 'Error al renovar QR.', 'error');
      }
    } catch {
      showToast('Error de conexión al renovar QR.', 'error');
    }
  },

  setupFilters() {
    const filterEstado = document.getElementById('filter-docente-estado');
    const searchInput = document.getElementById('search-docente-input');
    const filterFecha = document.getElementById('filter-asistencia-fecha');
    const filterTipo = document.getElementById('filter-asistencia-tipo');
    const filterPunt = document.getElementById('filter-asistencia-puntualidad');
    const filterLogEvento = document.getElementById('filter-log-evento');
    const filterLogFecha = document.getElementById('filter-log-fecha');
    const searchLogInput = document.getElementById('search-log-input');

    if (filterEstado) filterEstado.addEventListener('change', () => this.loadDocentes());
    if (searchInput) {
      let debounce;
      searchInput.addEventListener('input', () => {
        clearTimeout(debounce);
        debounce = setTimeout(() => this.loadDocentes(), 300);
      });
    }

    if (filterFecha) filterFecha.addEventListener('change', () => this.loadAsistenciasLogs());
    if (filterTipo) filterTipo.addEventListener('change', () => this.loadAsistenciasLogs());
    if (filterPunt) filterPunt.addEventListener('change', () => this.loadAsistenciasLogs());

    if (filterLogEvento) filterLogEvento.addEventListener('change', () => this.loadSystemLogs());
    if (filterLogFecha) filterLogFecha.addEventListener('change', () => this.loadSystemLogs());
    if (searchLogInput) {
      let debounceLog;
      searchLogInput.addEventListener('input', () => {
        clearTimeout(debounceLog);
        debounceLog = setTimeout(() => this.loadSystemLogs(), 300);
      });
    }
  }
};

window.initSuperrootDashboard = () => SuperrootModule.init();
