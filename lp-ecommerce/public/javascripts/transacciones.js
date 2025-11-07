document.addEventListener('DOMContentLoaded', function () {
  const searchResults = document.getElementById('search-results-admi');
  const actionButton = document.getElementById('option-action');
  const selectOption = document.getElementById('content-filter');

  // [IDF-0212] Solicita y renderiza datos genéricos de tablas según tipo seleccionado de tabla.
  actionButton.addEventListener('click', () => {
    const selected = selectOption.value;
    if (!selected) return;

    searchResults.innerHTML = '<p class="text-info">Cargando datos...</p>';

    fetch('/get_transacciones_generales', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tipo: selected })
    })
      .then(res => res.json())
      .then(data => renderizarGenerico(data))
      .catch(err => {
        console.error('Error en transacción general:', err);
        searchResults.innerHTML = "<p class='text-danger'>Error al obtener datos del servidor.</p>";
      });
  });

  // [IDF-0212] Renderiza los datos en grillas en el frontend.
  function renderizarGenerico(data) {
    searchResults.innerHTML = '';

    if (!data || !data.labels || !data.rows || data.rows.length === 0) {
      searchResults.innerHTML = '<p>No hay datos disponibles.</p>';
      return;
    }

    const labels = data.labels;
    const rows = data.rows;

    // Configuración de columnas responsive
    const columnStyle = `repeat(auto-fit, minmax(150px, 1fr))`;

    // === HEADER ===
    const header = document.createElement('div');
    header.className = 'result-header bg-secondary text-light fw-semibold py-2';
    header.style.display = 'grid';
    header.style.gridTemplateColumns = columnStyle;

    labels.forEach(label => {
      const span = document.createElement('span');
      span.textContent = label;
      span.classList.add('text-center', 'border-end');
      header.appendChild(span);
    });

    // === FILAS ===
    const fragment = document.createDocumentFragment();

    rows.forEach(fila => {
      const row = document.createElement('div');
      row.className = 'result-row border-bottom py-2';
      row.style.display = 'grid';
      row.style.gridTemplateColumns = columnStyle;
      row.style.alignItems = 'center';

      fila.forEach(cell => {
        const span = document.createElement('span');
        span.textContent = cell; // seguro contra XSS
        span.classList.add('text-center');
        row.appendChild(span);
      });

      fragment.appendChild(row);
    });

    // === APPEND FINAL ===
    searchResults.appendChild(header);
    searchResults.appendChild(fragment);
  }
});
