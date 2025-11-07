import { createContentType } from './create_item.js';

let data_cache = [];
let current_option = 'imagen';

// ================================================================
// [RNF-0017] Renderiza contenido multimedia según el tipo (imagen/video/audio)
// ================================================================
function showContent(contentType, current_role) {
  const container = document.querySelector('.container');
  container.innerHTML = '';

  current_option = contentType;

  // Filtrar resultados por tipo
  const filtered = data_cache.filter(item => item.type === contentType);

  if (filtered.length === 0) {
    container.innerHTML = `
      <div class="text-center py-5 text-muted">
        <i class="bi bi-exclamation-circle display-6"></i>
        <p class="mt-2">No hay ${contentType}s disponibles en este momento.</p>
      </div>
    `;
    return;
  }

  filtered.forEach(element => {
    createContentType({ data: element, current_role });
  });
}

// ================================================================
// [IDF-0016] Solicita al servidor los contenidos más descargados
// ================================================================
document.addEventListener('DOMContentLoaded', async () => {
  let current_role = "Usuario";
  const select = document.getElementById("content-filter");
  const tops = ['imagen', 'audio', 'video'];

  try {
    // Determinar rol del usuario
    const res = await fetch('/get_user_role');
    const data = await res.json();

    if (data.role === 'Administrador') current_role = 'Administrador';
    else if (data.role === 'Cliente') current_role = 'Cliente';

    // Asociar botones (imagen/audio/video)
    tops.forEach(type => {
      const btn = document.getElementById(`${type}-top`);
      if (btn) {
        btn.addEventListener('click', () => showContent(type, current_role));
      }
    });

    // Cargar contenidos más descargados (filtro)
    async function loadTopContent() {
      const parameter = select?.value || 'global';

      try {
        const response = await fetch('/top_content_downloaded', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ parameter })
        });

        if (!response.ok) throw new Error(`HTTP ${response.status}`);

        const data = await response.json();
        data_cache = Array.isArray(data) ? data : [];

        // Mostrar inicialmente el tipo actual
        showContent(current_option, current_role);

      } catch (err) {
        console.error('Error al obtener contenidos:', err);
        const container = document.querySelector('.container');
        container.innerHTML = `
          <div class="alert alert-danger text-center mt-4">
            <i class="bi bi-wifi-off me-2"></i>Error al conectar con el servidor.
          </div>
        `;
      }
    }

    // Carga inicial
    loadTopContent();

    // Recarga dinámica al cambiar filtro
    if (select) {
      select.addEventListener('change', loadTopContent);
    }

  } catch (error) {
    console.error('Error al verificar rol:', error);
    const container = document.querySelector('.container');
    container.innerHTML = `
      <div class="alert alert-warning text-center mt-4">
        <i class="bi bi-person-x me-2"></i>Error al obtener el rol del usuario.
      </div>
    `;
  }
});
