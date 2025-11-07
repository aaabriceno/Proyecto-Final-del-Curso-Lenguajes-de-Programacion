import { createContentType } from './create_item.js';

// =============================================================
// [IDF-0023] Determina rol de usuario y muestra vista de contenido
// =============================================================
document.addEventListener('DOMContentLoaded', async () => {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  if (!id) {
    document.querySelector('.container').innerHTML = "<p class='text-danger'>Error: No se proporcionó un ID válido.</p>";
    return;
  }

  try {
    // Verificar si el usuario puede acceder al contenido
    const verifRes = await fetch('/verificate_downloaded_content', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id })
    });
    const verifData = await verifRes.json();

    if (!verifData.success) {
      window.location.href = `item_shop.html?id=${id}`;
      return;
    }

    const hasRated = verifData.hasRated || false;

    // Obtener rol actual
    const roleRes = await fetch('/get_user_role');
    const roleData = await roleRes.json();

    const current_role = roleData.role || "Usuario";
    console.log("Rol actual:", current_role);

    itemGen(current_role, id, hasRated);
  } catch (error) {
    console.error('Error verificando rol o contenido:', error);
    alert("Error al verificar el acceso al contenido.");
  }
});

// =============================================================
// [IDF-0028] Renderiza la información del contenido adquirido
// =============================================================
async function itemGen(current_role, id, hasRated) {
  const itemDetails = document.querySelector('.container');
  if (!id) {
    itemDetails.innerHTML = "<p>Error: No se proporcionó un ID válido.</p>";
    return;
  }

  try {
    const res = await fetch('/get_content_by_id', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id })
    });
    const data = await res.json();

    if (!data) {
      itemDetails.innerHTML = "<p class='text-warning'>No se encontró el contenido solicitado.</p>";
      return;
    }

    itemDetails.innerHTML = '';
    createContentType({ data, current_role, linked: false });

    // Botón de descarga
    const btnDownload = document.createElement('button');
    btnDownload.className = 'btn btn-success mt-3';
    btnDownload.textContent = '⬇ Descargar';
    btnDownload.addEventListener('click', () => descargarContenido(data.id, data.title));

    const mediaDiv = document.querySelector('.media-item');
    mediaDiv.appendChild(btnDownload);

    // Botón de regalo (solo clientes)
    if (current_role === "Cliente") {
      const btnGift = document.createElement('button');
      btnGift.className = 'btn btn-primary mt-3 ms-2';
      btnGift.textContent = '🎁 Regalar';
      btnGift.addEventListener('click', () => {
        window.location.href = `item_shop.html?id=${data.id}&gift=1`;
      });
      mediaDiv.appendChild(btnGift);
    }

    // Botón de calificación (si aún no ha calificado)
    if (current_role === "Cliente" && hasRated === true) {
      const btnRate = document.createElement('button');
      btnRate.className = 'btn btn-warning mt-3 ms-2';
      btnRate.textContent = '⭐ Calificar contenido';
      btnRate.addEventListener('click', () => showRatingPrompt(data.id));
      mediaDiv.appendChild(btnRate);
    }

  } catch (error) {
    console.error('Error obteniendo el item:', error);
    itemDetails.innerHTML = "<p class='text-danger'>Error cargando el contenido.</p>";
  }
}

// =============================================================
// [IDF-0006] Descarga el contenido desde el servidor
// =============================================================
function descargarContenido(id, name) {
  fetch('/download_content', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id })
  })
    .then(res => {
      if (!res.ok) throw new Error("No se pudo descargar el archivo.");
      return res.blob();
    })
    .then(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = name || `contenido_${id}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      alert('✅ Descarga completada.');
    })
    .catch(err => {
      console.error('Error al descargar:', err);
      alert('Error al descargar el archivo.');
    })
    .finally(() => location.reload());
}

// =============================================================
// [IDF-0008] Muestra un modal para calificar contenido
// =============================================================
function showRatingPrompt(contentId) {
  const overlay = document.createElement('div');
  Object.assign(overlay.style, {
    position: 'fixed',
    top: 0, left: 0, width: '100vw', height: '100vh',
    background: 'rgba(0,0,0,0.6)',
    display: 'flex', justifyContent: 'center', alignItems: 'center',
    zIndex: 1000
  });

  const modal = document.createElement('div');
  Object.assign(modal.style, {
    background: '#fff',
    padding: '20px',
    borderRadius: '10px',
    boxShadow: '0 0 10px rgba(0,0,0,0.3)',
    textAlign: 'center',
    width: '320px'
  });

  modal.innerHTML = `
    <p class="fw-semibold">¿Deseas calificar este contenido?</p>
    <input type="number" id="rating-input" class="form-control mb-3" min="1" max="10" placeholder="Puntuación (1–10)">
    <div class="d-flex justify-content-center gap-2">
      <button class="btn btn-success">Enviar</button>
      <button class="btn btn-secondary">Cancelar</button>
    </div>
  `;

  const [sendBtn, cancelBtn] = modal.querySelectorAll('button');
  const input = modal.querySelector('#rating-input');

  sendBtn.addEventListener('click', async () => {
    const score = parseFloat(input.value);
    if (isNaN(score) || score < 1 || score > 10) {
      alert('⚠️ La puntuación debe estar entre 1 y 10.');
      return;
    }

    try {
      const res = await fetch('/rate_content', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: contentId, score })
      });
      const data = await res.json();
      if (data.success) {
        alert('⭐ ¡Gracias por tu calificación!');
        location.reload();
      } else {
        alert('Error al enviar la puntuación.');
      }
    } catch (err) {
      console.error('Error al calificar:', err);
      alert('No se pudo enviar la calificación.');
    } finally {
      document.body.removeChild(overlay);
    }
  });

  cancelBtn.addEventListener('click', () => {
    document.body.removeChild(overlay);
  });

  overlay.appendChild(modal);
  document.body.appendChild(overlay);
}
