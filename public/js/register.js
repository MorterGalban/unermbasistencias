// ==============================================================================
// UNERMB - PNF EN INFORMÁTICA | ASISTENCIA DOCENTE QR
// MÓDULO DE REGISTRO DOCENTE
// (Asignación de asignaturas reservada exclusivamente para el Superroot)
// ==============================================================================

const RegisterModule = {
  uploadedFotoFile: null,

  init() {
    this.setupFotoCarnetUpload();
    this.setupFormValidations();
  },

  // ----------------------------------------------------------------------------
  // GESTIÓN DE SUBIDA Y PREVIEW DE FOTOGRAFÍA TIPO CARNET
  // ----------------------------------------------------------------------------
  setupFotoCarnetUpload() {
    const fileInput = document.getElementById('reg-foto-carnet');
    const uploadBox = document.getElementById('carnet-upload-dropzone');
    const previewImg = document.getElementById('carnet-preview-img');
    const previewPlaceholder = document.getElementById('carnet-preview-placeholder');

    if (!fileInput || !uploadBox) return;

    uploadBox.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      this.handleFileSelected(file, previewImg, previewPlaceholder);
    });

    // Drag & Drop
    ['dragenter', 'dragover'].forEach(eventName => {
      uploadBox.addEventListener(eventName, (e) => {
        e.preventDefault();
        uploadBox.classList.add('dragover');
      }, false);
    });

    ['dragleave', 'drop'].forEach(eventName => {
      uploadBox.addEventListener(eventName, (e) => {
        e.preventDefault();
        uploadBox.classList.remove('dragover');
      }, false);
    });

    uploadBox.addEventListener('drop', (e) => {
      const dt = e.dataTransfer;
      const file = dt.files[0];
      if (file) {
        fileInput.files = dt.files;
        this.handleFileSelected(file, previewImg, previewPlaceholder);
      }
    });
  },

  handleFileSelected(file, previewImg, previewPlaceholder) {
    if (!file) return;

    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      showToast('Formato inválido. Solo se admiten fotos en formato JPG, PNG o WEBP.', 'error');
      this.uploadedFotoFile = null;
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      showToast('La fotografía excede el tamaño máximo permitido de 5 MB.', 'error');
      this.uploadedFotoFile = null;
      return;
    }

    this.uploadedFotoFile = file;

    const reader = new FileReader();
    reader.onload = (e) => {
      if (previewImg && previewPlaceholder) {
        previewImg.src = e.target.result;
        previewImg.style.display = 'block';
        previewPlaceholder.style.display = 'none';
      }
    };
    reader.readAsDataURL(file);
    showToast('Fotografía tipo carnet cargada correctamente.', 'success');
  },

  // ----------------------------------------------------------------------------
  // VALIDACIONES EN TIEMPO REAL Y ENVÍO DE FORMULARIO
  // ----------------------------------------------------------------------------
  setupFormValidations() {
    const regForm = document.getElementById('docente-registration-form');
    if (!regForm) return;

    const nombreInput = document.getElementById('reg-nombre');
    const apellidoInput = document.getElementById('reg-apellido');
    const cedulaInput = document.getElementById('reg-cedula');
    const emailInput = document.getElementById('reg-email');
    const passInput = document.getElementById('reg-password');
    const confirmPassInput = document.getElementById('reg-confirm-password');

    const regexAlpha = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]+$/;
    const regexCedula = /^\d{1,8}$/;
    const regexEmail = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

    const validateAlphaField = (input, errElId, fieldName) => {
      const val = input.value.trim();
      const errEl = document.getElementById(errElId);
      if (!val) {
        errEl.textContent = `El ${fieldName} es obligatorio.`;
        input.classList.add('is-invalid');
        return false;
      }
      if (!regexAlpha.test(val)) {
        errEl.textContent = `Solo debe contener caracteres alfabéticos (sin números ni símbolos).`;
        input.classList.add('is-invalid');
        return false;
      }
      if (val.length < 2 || val.length > 60) {
        errEl.textContent = `Debe tener entre 2 y 60 caracteres.`;
        input.classList.add('is-invalid');
        return false;
      }
      errEl.textContent = '';
      input.classList.remove('is-invalid');
      return true;
    };

    const checkConfirm = () => {
      const p1 = passInput ? passInput.value : '';
      const p2 = confirmPassInput ? confirmPassInput.value : '';
      const errEl = document.getElementById('error-reg-confirm-password');
      if (p2 && p1 !== p2) {
        errEl.textContent = 'Las contraseñas no coinciden.';
        confirmPassInput.classList.add('is-invalid');
      } else {
        errEl.textContent = '';
        confirmPassInput.classList.remove('is-invalid');
      }
    };

    if (nombreInput) {
      nombreInput.addEventListener('input', () => validateAlphaField(nombreInput, 'error-reg-nombre', 'nombre'));
    }

    if (apellidoInput) {
      apellidoInput.addEventListener('input', () => validateAlphaField(apellidoInput, 'error-reg-apellido', 'apellido'));
    }

    if (cedulaInput) {
      cedulaInput.addEventListener('input', () => {
        cedulaInput.value = cedulaInput.value.replace(/\D/g, '').slice(0, 8);
        const val = cedulaInput.value;
        const errEl = document.getElementById('error-reg-cedula');
        if (val && !regexCedula.test(val)) {
          errEl.textContent = 'Solo números (máximo 8 dígitos).';
          cedulaInput.classList.add('is-invalid');
        } else {
          errEl.textContent = '';
          cedulaInput.classList.remove('is-invalid');
        }
      });
    }

    if (emailInput) {
      emailInput.addEventListener('input', () => {
        const val = emailInput.value.trim();
        const errEl = document.getElementById('error-reg-email');
        if (val && !regexEmail.test(val)) {
          errEl.textContent = 'Formato de correo no válido.';
          emailInput.classList.add('is-invalid');
        } else {
          errEl.textContent = '';
          emailInput.classList.remove('is-invalid');
        }
      });
    }

    if (passInput) {
      passInput.addEventListener('input', () => {
        const val = passInput.value;
        if (window.updatePasswordSecurityUI) {
          window.updatePasswordSecurityUI('reg', val);
        }
        checkConfirm();
      });
    }

    if (confirmPassInput) {
      confirmPassInput.addEventListener('input', checkConfirm);
    }

    regForm.addEventListener('submit', (e) => this.handleRegistrationSubmit(e));
  },

  async handleRegistrationSubmit(e) {
    e.preventDefault();

    const nombre = document.getElementById('reg-nombre').value.trim();
    const apellido = document.getElementById('reg-apellido').value.trim();
    const tipoCedulaEl = document.getElementById('reg-tipo-cedula');
    const tipoCedula = tipoCedulaEl ? tipoCedulaEl.value : 'V';
    const rawCedula = document.getElementById('reg-cedula').value.trim();
    const cedulaDigitos = rawCedula.replace(/\D/g, '').slice(0, 8);
    const cedula = `${tipoCedula}-${cedulaDigitos}`;
    const email = document.getElementById('reg-email').value.trim();
    const password = document.getElementById('reg-password').value;
    const confirm_password = document.getElementById('reg-confirm-password').value;
    const submitBtn = document.getElementById('btn-submit-registration');

    // Validar cédula (hasta 8 dígitos)
    if (!cedulaDigitos || cedulaDigitos.length === 0) {
      showToast('Debe ingresar los dígitos de su Cédula de Identidad.', 'error');
      document.getElementById('reg-cedula').focus();
      return;
    }

    if (cedulaDigitos.length > 8) {
      showToast('La Cédula de Identidad no puede exceder los 8 caracteres numéricos.', 'error');
      return;
    }

    // Validar foto tipo carnet
    if (!this.uploadedFotoFile) {
      showToast('La Fotografía Tipo Carnet es obligatoria para el registro.', 'error');
      document.getElementById('carnet-upload-dropzone').scrollIntoView({ behavior: 'smooth' });
      return;
    }

    if (password !== confirm_password) {
      showToast('Las contraseñas no coinciden.', 'error');
      return;
    }

    if (password.length < 8) {
      showToast('La contraseña debe tener al menos 8 caracteres.', 'error');
      return;
    }

    submitBtn.disabled = true;
    submitBtn.innerHTML = `<span>Procesando registro seguro en base de datos...</span>`;

    try {
      const formData = new FormData();
      formData.append('nombre', nombre);
      formData.append('apellido', apellido);
      formData.append('tipo_cedula', tipoCedula);
      formData.append('cedula', cedula);
      formData.append('email', email);
      formData.append('password', password);
      formData.append('confirm_password', confirm_password);
      formData.append('foto_carnet', this.uploadedFotoFile);
      formData.append('materias', JSON.stringify([])); // Reservado para Superroot

      const apiBase = typeof API_BASE !== 'undefined' ? API_BASE : '';
      const res = await fetch(`${apiBase}/api/auth/registro`, {
        method: 'POST',
        body: formData
      });

      const data = await res.json();

      if (data.success) {
        showToast('¡Registro completado exitosamente!', 'success', 7000);
        
        // Cargar datos en pantalla de éxito
        const fullNameEl = document.getElementById('reg-success-fullname');
        const cedulaEl = document.getElementById('reg-success-cedula');
        const emailEl = document.getElementById('reg-success-email');
        const materiasEl = document.getElementById('reg-success-materias-count');

        if (fullNameEl) fullNameEl.textContent = `${nombre} ${apellido}`;
        if (cedulaEl) cedulaEl.textContent = cedula;
        if (emailEl) emailEl.textContent = email;
        if (materiasEl) materiasEl.textContent = 'Pendientes por asignar por el Superroot';

        // Limpiar formulario
        document.getElementById('docente-registration-form').reset();
        this.uploadedFotoFile = null;

        navigateTo('view-registration-success');
      } else {
        showToast(data.error || 'Ocurrió un error al registrarse.', 'error', 7000);
      }
    } catch (err) {
      console.error('Error enviando registro:', err);
      showToast('Error de conexión con el servidor. Verifique que esté activo.', 'error');
    } finally {
      submitBtn.disabled = false;
      submitBtn.innerHTML = `<span>Completar Registro Docente</span>`;
    }
  }
};

document.addEventListener('DOMContentLoaded', () => {
  RegisterModule.init();
});
