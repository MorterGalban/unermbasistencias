// ==============================================================================
// UNERMB - PNF EN INFORMÁTICA | PENSUM 2026 OFICIAL
// COMPONENTE UNIVERSAL DE SELECCIÓN EN CASCADA (TRAYECTO -> TRIMESTRE -> MATERIA)
// ==============================================================================

const PensumCascade = {
  estructura: null,
  bloquesHorarios: [],
  diasSemana: [],
  isLoaded: false,

  async init() {
    if (this.isLoaded && this.estructura) return this.estructura;

    try {
      const apiBase = typeof API_BASE !== 'undefined' ? API_BASE : '';
      const res = await fetch(`${apiBase}/api/auth/pensum-estructura`);
      const data = await res.json();

      if (data.success) {
        this.estructura = data.estructura;
        this.bloquesHorarios = data.bloquesHorarios;
        this.diasSemana = data.diasSemana;
        this.isLoaded = true;
        window.dispatchEvent(new CustomEvent('pensumEstructuraLoaded', { detail: data }));
        return this.estructura;
      }
    } catch (err) {
      console.error('Error cargando estructura de pensum:', err);
    }
    return null;
  },

  getTrayectos() {
    if (!this.estructura) return [];
    return this.estructura.map(t => t.nombre);
  },

  getTrimestres(trayectoNombre) {
    if (!this.estructura || !trayectoNombre) return [];
    const trayecto = this.estructura.find(t => t.nombre === trayectoNombre);
    return trayecto ? trayecto.trimestres.map(tr => tr.nombre) : [];
  },

  getMaterias(trayectoNombre, trimestreNombre) {
    if (!this.estructura || !trayectoNombre || !trimestreNombre) return [];
    const trayecto = this.estructura.find(t => t.nombre === trayectoNombre);
    if (!trayecto) return [];
    const trimestre = trayecto.trimestres.find(tr => tr.nombre === trimestreNombre);
    return trimestre ? trimestre.materias : [];
  },

  findMateriaById(materiaId) {
    if (!this.estructura || !materiaId) return null;
    const mId = parseInt(materiaId, 10);
    for (const t of this.estructura) {
      for (const tr of t.trimestres) {
        for (const m of tr.materias) {
          if (m.id === mId) {
            return {
              id: m.id,
              codigo: m.codigo,
              nombre: m.nombre,
              pnf: m.pnf,
              trayecto: t.nombre,
              trimestre: tr.nombre
            };
          }
        }
      }
    }
    return null;
  },

  /**
   * Renderiza una fila individual de selector en cascada:
   * 1. Trayecto -> 2. Trimestre -> 3. Materia
   */
  createCascadeRow({
    rowIndex = 0,
    selectedMateriaId = null,
    alreadySelectedIds = [],
    onChange = () => {},
    onRemove = null,
    canRemove = false
  }) {
    const row = document.createElement('div');
    row.className = 'cascade-row';
    row.id = `cascade-row-${rowIndex}`;

    const infoPrevia = selectedMateriaId ? this.findMateriaById(selectedMateriaId) : null;

    // 1. Selector de Trayecto
    const colTrayecto = document.createElement('div');
    colTrayecto.className = 'cascade-group';
    colTrayecto.innerHTML = `<label>1. Trayecto</label>`;
    const selectTrayecto = document.createElement('select');
    selectTrayecto.className = 'form-select';
    selectTrayecto.innerHTML = `<option value="">-- Seleccionar Trayecto --</option>`;
    this.getTrayectos().forEach(t => {
      const opt = document.createElement('option');
      opt.value = t;
      opt.textContent = t;
      if (infoPrevia && infoPrevia.trayecto === t) opt.selected = true;
      selectTrayecto.appendChild(opt);
    });
    colTrayecto.appendChild(selectTrayecto);

    // 2. Selector de Trimestre
    const colTrimestre = document.createElement('div');
    colTrimestre.className = 'cascade-group';
    colTrimestre.innerHTML = `<label>2. Trimestre</label>`;
    const selectTrimestre = document.createElement('select');
    selectTrimestre.className = 'form-select';
    selectTrimestre.disabled = !infoPrevia;
    selectTrimestre.innerHTML = `<option value="">-- Seleccionar Trimestre --</option>`;
    if (infoPrevia) {
      this.getTrimestres(infoPrevia.trayecto).forEach(tr => {
        const opt = document.createElement('option');
        opt.value = tr;
        opt.textContent = tr;
        if (infoPrevia.trimestre === tr) opt.selected = true;
        selectTrimestre.appendChild(opt);
      });
    }
    colTrimestre.appendChild(selectTrimestre);

    // 3. Selector de Materia
    const colMateria = document.createElement('div');
    colMateria.className = 'cascade-group';
    colMateria.innerHTML = `<label>3. Materia Oficial (pensum2026.txt)</label>`;
    const selectMateria = document.createElement('select');
    selectMateria.className = 'form-select';
    selectMateria.required = true;
    selectMateria.disabled = !infoPrevia;
    selectMateria.innerHTML = `<option value="">-- Seleccionar Materia --</option>`;
    if (infoPrevia) {
      this.getMaterias(infoPrevia.trayecto, infoPrevia.trimestre).forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.id;
        opt.textContent = `${m.codigo ? m.codigo + ' - ' : ''}${m.nombre}`;
        const isSelectedElsewhere = alreadySelectedIds.includes(m.id) && m.id !== selectedMateriaId;
        if (isSelectedElsewhere) {
          opt.disabled = true;
          opt.textContent += ' (Ya elegida)';
        }
        if (infoPrevia.id === m.id) opt.selected = true;
        selectMateria.appendChild(opt);
      });
    }
    colMateria.appendChild(selectMateria);

    // Eventos encadenados
    selectTrayecto.addEventListener('change', () => {
      const trayectoVal = selectTrayecto.value;
      selectTrimestre.innerHTML = `<option value="">-- Seleccionar Trimestre --</option>`;
      selectMateria.innerHTML = `<option value="">-- Seleccionar Materia --</option>`;
      selectMateria.disabled = true;

      if (!trayectoVal) {
        selectTrimestre.disabled = true;
        onChange(null);
        return;
      }

      selectTrimestre.disabled = false;
      this.getTrimestres(trayectoVal).forEach(tr => {
        const opt = document.createElement('option');
        opt.value = tr;
        opt.textContent = tr;
        selectTrimestre.appendChild(opt);
      });
      onChange(null);
    });

    selectTrimestre.addEventListener('change', () => {
      const trayectoVal = selectTrayecto.value;
      const trimestreVal = selectTrimestre.value;
      selectMateria.innerHTML = `<option value="">-- Seleccionar Materia --</option>`;

      if (!trimestreVal) {
        selectMateria.disabled = true;
        onChange(null);
        return;
      }

      selectMateria.disabled = false;
      const materiasDisponibles = this.getMaterias(trayectoVal, trimestreVal);
      materiasDisponibles.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.id;
        opt.textContent = `${m.codigo ? m.codigo + ' - ' : ''}${m.nombre}`;
        if (alreadySelectedIds.includes(m.id)) {
          opt.disabled = true;
          opt.textContent += ' (Ya elegida)';
        }
        selectMateria.appendChild(opt);
      });
      onChange(null);
    });

    selectMateria.addEventListener('change', () => {
      const val = parseInt(selectMateria.value, 10) || null;
      onChange(val);
    });

    row.appendChild(colTrayecto);
    row.appendChild(colTrimestre);
    row.appendChild(colMateria);

    if (canRemove && onRemove) {
      const colBtn = document.createElement('div');
      colBtn.style.display = 'flex';
      colBtn.style.alignItems = 'flex-end';
      const removeBtn = document.createElement('button');
      removeBtn.type = 'button';
      removeBtn.className = 'cascade-btn-remove';
      removeBtn.innerHTML = `<span>&times; Quitar</span>`;
      removeBtn.title = 'Quitar esta materia';
      removeBtn.addEventListener('click', onRemove);
      colBtn.appendChild(removeBtn);
      row.appendChild(colBtn);
    }

    return row;
  }
};

window.PensumCascade = PensumCascade;
document.addEventListener('DOMContentLoaded', () => {
  PensumCascade.init();
});
