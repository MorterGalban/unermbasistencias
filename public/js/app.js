// ==============================================================================
// UNERMB - PNF EN INFORMÁTICA | ASISTENCIA DOCENTE QR
// APLICACIÓN PRINCIPAL: ESTADO GLOBAL, SESIÓN, RUTAS Y FEEDBACK
// ==============================================================================

// Detección inteligente del host de la API (soporte para servidor Express, Live Server y producción en Railway)
function resolveApiBase() {
  if (window.location.protocol === 'file:') {
    return 'http://localhost:3000';
  }
  // Si se abre desde Live Server u otro puerto de desarrollo frontend
  if ((window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') &&
      window.location.port && window.location.port !== '3000') {
    return `http://${window.location.hostname}:3000`;
  }
  // Servidor Express local (puerto 3000) o producción en la nube (Railway HTTPS)
  return '';
}

const API_BASE = resolveApiBase();
window.API_BASE = API_BASE;

const AppState = {
  token: localStorage.getItem('unermb_token') || null,
  user: JSON.parse(localStorage.getItem('unermb_user') || 'null'),
  materiasCatalog: []
};

// Generador de Feedback Sonoro Seguro (Web Audio API)
const AudioFeedback = {
  playSuccess: () => {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
      osc.frequency.setValueAtTime(880, ctx.currentTime + 0.1); // A5
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.35);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.35);
    } catch (e) {
      console.warn('Audio feedback no disponible');
    }
  },
  playError: () => {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = 'sawtooth';
      osc.frequency.setValueAtTime(220, ctx.currentTime);
      osc.frequency.setValueAtTime(160, ctx.currentTime + 0.15);
      gain.gain.setValueAtTime(0.3, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.01, ctx.currentTime + 0.4);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + 0.4);
    } catch (e) {}
  }
};

// Sistema de Notificaciones Toast con Iconografía Vectorial Profesional (Sin Emojis)
function showToast(message, type = 'info', duration = 4000) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  
  let iconSvg = '';
  if (type === 'success') {
    iconSvg = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>';
  } else if (type === 'error') {
    iconSvg = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>';
  } else if (type === 'warning') {
    iconSvg = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>';
  } else {
    iconSvg = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>';
  }

  toast.innerHTML = `
    <span style="display: flex; align-items: center; justify-content: center; font-size: 1.15rem;">${iconSvg}</span>
    <div style="flex: 1; word-break: break-word;">${message}</div>
    <button onclick="this.parentElement.remove()" style="color: inherit; opacity: 0.7; font-size: 1.1rem; padding: 2px; background: transparent; border: none; cursor: pointer;">&times;</button>
  `;

  container.appendChild(toast);

  if (type === 'success') AudioFeedback.playSuccess();
  if (type === 'error') AudioFeedback.playError();

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(40px)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// Cambiar de vista activa
function navigateTo(viewId) {
  document.querySelectorAll('.view-section').forEach(view => {
    view.classList.remove('active');
  });

  const target = document.getElementById(viewId);
  if (target) {
    target.classList.add('active');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  updateHeaderNav();
}

// Actualizar barra de navegación superior según sesión
function updateHeaderNav() {
  const userHeaderContainer = document.getElementById('user-header-actions');
  if (!userHeaderContainer) return;

  if (AppState.token && AppState.user) {
    const rolText = AppState.user.rol === 'SUPERROOT' ? 'SUPERROOT (ADMIN)' : 'DOCENTE PNF';
    const photoUrl = AppState.user.foto_carnet_url ? (AppState.user.foto_carnet_url.startsWith('http') ? AppState.user.foto_carnet_url : (API_BASE + AppState.user.foto_carnet_url)) : 'multimedia/uner.png';
    userHeaderContainer.innerHTML = `
      <div class="user-badge-header">
        <img src="${photoUrl}" alt="Avatar" class="user-avatar-mini" onerror="this.src='multimedia/uner.png'">
        <div style="display: flex; flex-direction: column; text-align: left;">
          <span style="font-size: 0.78rem; font-weight: 700;">${AppState.user.nombre} ${AppState.user.apellido}</span>
          <span style="font-size: 0.65rem; color: #93c5fd;">${rolText}</span>
        </div>
      </div>
      <button class="btn btn-secondary btn-sm" style="padding: 0.35rem 0.75rem; font-size: 0.78rem;" onclick="logout()">
        Cerrar Sesión
      </button>
    `;
  } else {
    userHeaderContainer.innerHTML = `
      <button class="btn btn-outline-primary btn-sm" style="color: #ffffff; border-color: rgba(255,255,255,0.4);" onclick="navigateTo('view-login')">
        Iniciar Sesión
      </button>
      <button class="btn btn-primary btn-sm" style="background: var(--unermb-blue-accent);" onclick="navigateTo('view-register')">
        Registro Docente
      </button>
    `;
  }
}

// Función global de cerrar sesión
function logout() {
  localStorage.removeItem('unermb_token');
  localStorage.removeItem('unermb_user');
  AppState.token = null;
  AppState.user = null;

  // Detener escáner si estaba activo
  if (window.html5QrCodeScannerInstance) {
    window.html5QrCodeScannerInstance.stop().catch(() => {});
  }

  showToast('Ha cerrado sesión correctamente.', 'info');
  navigateTo('view-login');
}

// Inicialización de la aplicación
document.addEventListener('DOMContentLoaded', async () => {
  // Comprobar si se abrió como archivo local file://
  if (window.location.protocol === 'file:') {
    const warningEl = document.getElementById('file-protocol-warning');
    if (warningEl) warningEl.style.display = 'block';
  }

  // Cargar catálogo oficial de materias y estructura jerárquica en cascada (pensum2026.txt)
  try {
    const res = await fetch(`${API_BASE}/api/auth/materias`);
    const data = await res.json();
    if (data.success) {
      AppState.materiasCatalog = data.materias;
      window.dispatchEvent(new CustomEvent('materiasCatalogLoaded', { detail: data.materias }));
    }
  } catch (err) {
    console.error('Error cargando materias:', err);
  }

  try {
    if (window.PensumCascade) {
      await window.PensumCascade.init();
    }
  } catch (err) {
    console.error('Error cargando estructura en cascada de pensum:', err);
  }

  // Verificar si hay sesión persistente
  if (AppState.token && AppState.user) {
    try {
      const res = await fetch(`${API_BASE}/api/auth/me`, {
        headers: { 'Authorization': `Bearer ${AppState.token}` }
      });
      const data = await res.json();

      if (data.success) {
        AppState.user = data.usuario;
        localStorage.setItem('unermb_user', JSON.stringify(data.usuario));
        
        if (AppState.user.rol === 'SUPERROOT') {
          navigateTo('view-superroot');
          if (window.initSuperrootDashboard) window.initSuperrootDashboard();
        } else {
          navigateTo('view-docente');
          if (window.initDocenteDashboard) window.initDocenteDashboard();
        }
      } else {
        logout();
      }
    } catch {
      logout();
    }
  } else {
    navigateTo('view-login');
  }

  // Configurar envío del formulario de login
  const loginForm = document.getElementById('login-form');
  if (loginForm) {
    loginForm.addEventListener('submit', handleLoginSubmit);
  }
});

// Manejo de Inicio de Sesión
async function handleLoginSubmit(e) {
  e.preventDefault();
  const identificador = document.getElementById('login-identificador').value.trim();
  const password = document.getElementById('login-password').value;
  const submitBtn = document.getElementById('btn-login-submit');

  if (!identificador || !password) {
    showToast('Debe ingresar su correo o cédula y su contraseña.', 'error');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.innerHTML = `<span>Verificando credenciales...</span>`;

  try {
    const res = await fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ identificador, password })
    });

    let data;
    try {
      data = await res.json();
    } catch (parseErr) {
      throw new Error(`Respuesta no compatible del servidor (${res.status} ${res.statusText})`);
    }

    if (data.success) {
      AppState.token = data.token;
      AppState.user = data.usuario;
      localStorage.setItem('unermb_token', data.token);
      localStorage.setItem('unermb_user', JSON.stringify(data.usuario));

      showToast(`¡Bienvenido(a), ${data.usuario.nombre}!`, 'success');

      if (data.usuario.rol === 'SUPERROOT') {
        navigateTo('view-superroot');
        if (window.initSuperrootDashboard) window.initSuperrootDashboard();
      } else {
        navigateTo('view-docente');
        if (window.initDocenteDashboard) window.initDocenteDashboard();
      }
    } else {
      if (data.codigo_estado === 'PENDIENTE') {
        showToast(data.error, 'warning', 7000);
      } else {
        showToast(data.error || 'Credenciales incorrectas.', 'error');
      }
    }
  } catch (err) {
    console.error('Fallo en login:', err);
    showToast('No se pudo conectar con el servidor backend. Verifique que el servicio esté ejecutándose ("npm start") en http://localhost:3000.', 'error', 6000);
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerHTML = `<span>Ingresar al Sistema</span>`;
  }
}

// Función Global para Mostrar / Ocultar Contraseña con Iconos Vectoriales SVG
window.togglePasswordVisibility = function(inputId, btn) {
  const input = document.getElementById(inputId);
  if (!input || !btn) return;

  const svgEye = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>';
  const svgEyeOff = '<svg class="icon-svg icon-svg-stroke" viewBox="0 0 24 24"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"></path><line x1="1" y1="1" x2="23" y2="23"></line></svg>';

  if (input.type === 'password') {
    input.type = 'text';
    btn.innerHTML = svgEyeOff;
    btn.setAttribute('title', 'Ocultar contraseña');
    btn.setAttribute('aria-label', 'Ocultar contraseña');
  } else {
    input.type = 'password';
    btn.innerHTML = svgEye;
    btn.setAttribute('title', 'Mostrar contraseña');
    btn.setAttribute('aria-label', 'Mostrar contraseña');
  }
};

// Evaluación Criptográfica de Fortaleza de Contraseña
window.evaluatePasswordSecurity = function(password) {
  const pwd = String(password || '');
  const criteria = {
    length: pwd.length >= 8,
    casing: /[A-Z]/.test(pwd) && /[a-z]/.test(pwd),
    number: /\d/.test(pwd),
    symbol: /[^a-zA-Z0-9]/.test(pwd)
  };

  const metCount = Object.values(criteria).filter(Boolean).length;
  
  let label = 'Sin ingresar';
  let percent = 0;
  let color = '#cbd5e1';

  if (pwd.length === 0) {
    label = 'Sin ingresar';
    percent = 0;
    color = '#cbd5e1';
  } else if (!criteria.length) {
    label = `Mínimo 8 car. (${pwd.length}/8)`;
    percent = 25;
    color = '#dc2626'; // Rojo
  } else if (metCount <= 2) {
    label = 'Débil';
    percent = 50;
    color = '#ea580c'; // Naranja
  } else if (metCount === 3) {
    label = 'Aceptable';
    percent = 75;
    color = '#0266cc'; // Azul
  } else {
    label = 'Segura / Fuerte';
    percent = 100;
    color = '#059669'; // Verde
  }

  return { criteria, metCount, label, percent, color, isValid: criteria.length && metCount >= 2 };
};

// Actualizador Visual del Componente de Seguridad de Contraseña
window.updatePasswordSecurityUI = function(prefix, password) {
  const security = window.evaluatePasswordSecurity(password);
  
  const labelEl = document.getElementById(`${prefix}-strength-label`);
  const barEl = document.getElementById(`${prefix}-strength-bar`);
  const critLength = document.getElementById(`${prefix}-crit-length`);
  const critCasing = document.getElementById(`${prefix}-crit-casing`);
  const critNumber = document.getElementById(`${prefix}-crit-number`);
  const critSymbol = document.getElementById(`${prefix}-crit-symbol`);

  if (labelEl) {
    labelEl.textContent = security.label;
    labelEl.style.color = security.color;
  }

  if (barEl) {
    barEl.style.width = `${security.percent}%`;
    barEl.style.backgroundColor = security.color;
  }

  const updateItem = (itemEl, isValid) => {
    if (!itemEl) return;
    const icon = itemEl.querySelector('.checklist-icon');
    if (isValid) {
      itemEl.classList.add('valid');
      itemEl.classList.remove('invalid');
      if (icon) icon.textContent = '✓';
    } else {
      itemEl.classList.remove('valid');
      itemEl.classList.add('invalid');
      if (icon) icon.textContent = '●';
    }
  };

  updateItem(critLength, security.criteria.length);
  updateItem(critCasing, security.criteria.casing);
  updateItem(critNumber, security.criteria.number);
  updateItem(critSymbol, security.criteria.symbol);

  return security;
};

// Modal de Restablecimiento de Contraseña
window.openResetPasswordModal = function() {
  const modal = document.getElementById('modal-reset-password');
  if (modal) {
    modal.style.display = 'flex';
    window.updatePasswordSecurityUI('reset', '');
  }
};

window.closeResetPasswordModal = function() {
  const modal = document.getElementById('modal-reset-password');
  if (modal) modal.style.display = 'none';
};

// Envío de Formulario de Restablecimiento de Contraseña
async function handleResetPasswordSubmit(e) {
  e.preventDefault();
  const cedula = document.getElementById('reset-cedula').value.trim();
  const email = document.getElementById('reset-email').value.trim();
  const new_password = document.getElementById('reset-new-password').value;
  const confirm_password = document.getElementById('reset-confirm-password').value;
  const submitBtn = document.getElementById('btn-reset-password-submit');

  if (!cedula || !email || !new_password) {
    showToast('Debe completar la cédula, correo y su nueva clave.', 'error');
    return;
  }

  if (new_password.length < 8) {
    showToast('La nueva contraseña debe tener al menos 8 caracteres.', 'error');
    return;
  }

  if (new_password !== confirm_password) {
    showToast('Las contraseñas no coinciden.', 'error');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.innerHTML = `<span>Actualizando contraseña...</span>`;

  try {
    const res = await fetch(`${API_BASE}/api/auth/reset-password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cedula, email, new_password, confirm_password })
    });
    
    let data;
    try {
      data = await res.json();
    } catch {
      throw new Error(`Respuesta no válida del servidor (${res.status})`);
    }

    if (data.success) {
      showToast(data.message, 'success', 6000);
      document.getElementById('reset-password-form').reset();
      window.updatePasswordSecurityUI('reset', '');
      closeResetPasswordModal();
      
      // Pre-cargar credenciales en el login
      document.getElementById('login-identificador').value = cedula;
      document.getElementById('login-password').value = new_password;
      navigateTo('view-login');
    } else {
      showToast(data.error || 'No se pudo actualizar la contraseña.', 'error', 6000);
    }
  } catch (err) {
    console.error('Error al actualizar contraseña:', err);
    showToast('No se pudo conectar con el servidor backend. Verifique que el servicio esté ejecutándose ("npm start").', 'error', 6000);
  } finally {
    submitBtn.disabled = false;
    submitBtn.innerHTML = `<span>Guardar Nueva Contraseña</span>`;
  }
}

// Inicializar listeners del formulario de restablecimiento
document.addEventListener('DOMContentLoaded', () => {
  const resetForm = document.getElementById('reset-password-form');
  const resetPwdInput = document.getElementById('reset-new-password');
  const resetConfirmInput = document.getElementById('reset-confirm-password');
  const errConfirmEl = document.getElementById('error-reset-confirm-password');

  if (resetPwdInput) {
    resetPwdInput.addEventListener('input', () => {
      const val = resetPwdInput.value;
      window.updatePasswordSecurityUI('reset', val);

      if (resetConfirmInput && resetConfirmInput.value) {
        if (val !== resetConfirmInput.value) {
          errConfirmEl.textContent = 'Las contraseñas no coinciden.';
          errConfirmEl.className = 'validation-error';
          resetConfirmInput.classList.add('is-invalid');
          resetConfirmInput.classList.remove('is-valid');
        } else {
          errConfirmEl.textContent = 'Las contraseñas coinciden correctamente.';
          errConfirmEl.className = 'validation-success';
          resetConfirmInput.classList.remove('is-invalid');
          resetConfirmInput.classList.add('is-valid');
        }
      }
    });
  }

  if (resetConfirmInput) {
    resetConfirmInput.addEventListener('input', () => {
      const p1 = resetPwdInput ? resetPwdInput.value : '';
      const p2 = resetConfirmInput.value;

      if (!p2) {
        errConfirmEl.textContent = '';
        errConfirmEl.className = 'validation-error';
        resetConfirmInput.classList.remove('is-invalid', 'is-valid');
        return;
      }

      if (p1 !== p2) {
        errConfirmEl.textContent = 'Las contraseñas no coinciden.';
        errConfirmEl.className = 'validation-error';
        resetConfirmInput.classList.add('is-invalid');
        resetConfirmInput.classList.remove('is-valid');
      } else {
        errConfirmEl.textContent = 'Las contraseñas coinciden correctamente.';
        errConfirmEl.className = 'validation-success';
        resetConfirmInput.classList.remove('is-invalid');
        resetConfirmInput.classList.add('is-valid');
      }
    });
  }

  if (resetForm) {
    resetForm.addEventListener('submit', handleResetPasswordSubmit);
  }
});
