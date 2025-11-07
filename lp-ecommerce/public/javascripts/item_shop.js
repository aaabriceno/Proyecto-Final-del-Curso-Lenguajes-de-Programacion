import { createContentType } from './create_item.js';

// ================================================================
// [IDF-0031] Renderiza la interfaz gráfica de compra o regalo
// ================================================================
document.addEventListener('DOMContentLoaded', async () => {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  const isGift = params.get('gift') === '1';
  const itemDetails = document.querySelector('.container');

  if (!id) {
    itemDetails.innerHTML = "<p class='text-danger'>Error: No se proporcionó un ID válido.</p>";
    return;
  }

  try {
    const response = await fetch('/get_content_by_id', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id })
    });

    const data = await response.json();

    if (!data) {
      itemDetails.innerHTML = "<p class='text-warning'>No se encontró el contenido solicitado.</p>";
      return;
    }

    // Limpia contenedor y crea media
    itemDetails.innerHTML = '';
    createContentType({ data, linked: false });

    // Determina flujo (compra o regalo)
    if (isGift) {
      genRegaloBar(data.id);
    } else {
      addBuyButton(data);
    }

  } catch (error) {
    console.error('Error obteniendo el item:', error);
    itemDetails.innerHTML = "<p class='text-danger'>Error cargando el contenido.</p>";
  }
});

// ================================================================
// [IDF-0032] Crea y gestiona el botón de compra
// ================================================================
function addBuyButton(data) {
  const container = document.querySelector('.media-item');
  if (!container) return;

  const buyButton = document.createElement('button');
  buyButton.className = 'btn btn-success mt-3';
  buyButton.textContent = 'Comprar ahora 💰';

  buyButton.addEventListener('click', async () => {
    try {
      const res = await fetch('/pagarContenido', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: data.id })
      });
      const respuesta = await res.json();

      if (respuesta.success) {
        alert('✅ Contenido comprado exitosamente.');
        window.location.href = `item_view.html?id=${data.id}`;
      } else {
        alert('⚠️ No tiene saldo suficiente para esta compra.');
      }
    } catch (error) {
      console.error('Error al procesar compra:', error);
      alert('Error al conectar con el servidor.');
    }
  });

  container.appendChild(buyButton);
}

// ================================================================
// [IDF-0007] Envia al servidor la petición para regalar contenido
// ================================================================
function genRegaloBar(id) {
  const container = document.querySelector('.media-item');
  if (!container) return;

  const wrapper = document.createElement('div');
  wrapper.className = 'gift-bar mt-4 p-3 bg-dark border border-secondary rounded';

  const label = document.createElement('label');
  label.textContent = '🎁 Usuario destinatario:';
  label.className = 'form-label text-light d-block';

  const input = document.createElement('input');
  input.type = 'text';
  input.id = 'recipient';
  input.placeholder = 'Nombre de usuario o correo';
  input.className = 'form-control mb-2 bg-secondary text-light border-0';

  const sendBtn = document.createElement('button');
  sendBtn.textContent = 'Enviar regalo';
  sendBtn.className = 'btn btn-primary';

  // --- Acción del botón ---
  sendBtn.addEventListener('click', async () => {
    const recipient = input.value.trim();
    if (!recipient) {
      alert('⚠️ Debes ingresar un destinatario.');
      return;
    }

    try {
      const res = await fetch('/gift_content', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, destinatario: recipient })
      });

      const response = await res.json();
      if (response.success) {
        alert('🎉 El regalo ha sido enviado con éxito.');
        window.location.href = 'user_view.html';
      } else {
        alert(response.msg || 'No se pudo enviar el regalo.');
      }

    } catch (err) {
      console.error('Error al enviar regalo:', err);
      alert('Error al conectar con el servidor.');
    }
  });

  wrapper.appendChild(label);
  wrapper.appendChild(input);
  wrapper.appendChild(sendBtn);
  container.appendChild(wrapper);
}
