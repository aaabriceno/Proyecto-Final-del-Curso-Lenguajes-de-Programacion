// [IDF-0027] Solicita información completa de un cliente (datos, compras, descargas, recargas)
document.addEventListener('DOMContentLoaded', function () {
  const params = new URLSearchParams(window.location.search);
  const id = params.get('id');
  const userInfo = document.querySelector('#user-info');
  const downloadsList = document.getElementById('downloads-list');
  const downloadsTime = document.getElementById('downloads-list-time');
  const recargasList = document.getElementById('recargas-list');

  if (!id) {
    userInfo.innerHTML = "<p>Error: No se proporcionó un ID válido.</p>";
    return;
  }

  // === Función genérica para peticiones ===
  async function request(url, body = {}) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    return await res.json();
  }

  // === Renderización segura de listas ===
  function renderList(container, items, renderItem, emptyMsg) {
    container.innerHTML = '';
    if (!items || items.length === 0) {
      container.innerHTML = `<li>${emptyMsg}</li>`;
      return;
    }
    const fragment = document.createDocumentFragment();
    items.forEach(item => fragment.appendChild(renderItem(item)));
    container.appendChild(fragment);
  }

  // === [IDF-0027] Información general del usuario ===
  request('/get_user_by_id', { id })
    .then(data => {
      if (!data) throw new Error('Usuario no encontrado');
      userInfo.innerHTML = `
        <p><strong>Usuario:</strong> ${data.username}</p>
        <p><strong>Nombre Completo:</strong> ${data.fullname}</p>
        <p><strong>Email:</strong> ${data.email}</p>
        <p><strong>ID:</strong> ${id}</p>
        <p><strong>Saldo:</strong> $${parseFloat(data.saldo).toFixed(2)}</p>
        <p><strong>Estado de cuenta:</strong> ${data.estado}</p>
      `;
    })
    .catch(err => {
      console.error('Error cargando info del usuario:', err);
      userInfo.innerHTML = '<p style="color:red">Error al cargar datos del usuario.</p>';
    });

  // === [IDF-0020] Contenidos comprados ===
  request('/get_user_downloads_info', { id })
    .then(data => {
      renderList(downloadsList, data, item => {
        const li = document.createElement('li');
        li.classList.add('recarga-item');

        const h4 = document.createElement('h4');
        const a = document.createElement('a');
        a.href = `item_view_admi.html?id=${item.id}`;
        a.textContent = item.title;
        h4.appendChild(a);
        h4.insertAdjacentText('beforeend', ` (${item.type})`);

        const p = document.createElement('p');
        p.textContent = `Autor: ${item.author} | Puntuación: ${item.rating}`;

        li.appendChild(h4);
        li.appendChild(p);
        return li;
      }, 'No hay contenidos comprados.');
    })
    .catch(err => {
      downloadsList.innerHTML = '<li style="color:red">Error al cargar contenidos.</li>';
      console.error(err);
    });

  // === [IDF-0020b] Contenidos descargados con fecha ===
  request('/get_user_downloads_info_time', { id })
    .then(data => {
      renderList(downloadsTime, data, item => {
        const li = document.createElement('li');
        li.classList.add('recarga-item');

        const h4 = document.createElement('h4');
        const a = document.createElement('a');
        a.href = `item_view_admi.html?id=${item.id}`;
        a.textContent = item.title;
        h4.appendChild(a);
        h4.insertAdjacentText('beforeend', ` (${item.type})`);

        const p = document.createElement('p');
        p.textContent = `Autor: ${item.author} | Puntuación: ${item.rating}`;
        li.appendChild(h4);
        li.appendChild(p);
        return li;
      }, 'No hay contenidos descargados.');
    })
    .catch(err => {
      downloadsTime.innerHTML = '<li style="color:red">Error al cargar descargas.</li>';
      console.error(err);
    });

  // === [IDF-0012] Recargas solicitadas por el cliente ===
  request('/get_user_refills_info', { id })
    .then(data => {
      renderList(recargasList, data, item => {
        const li = document.createElement('li');
        li.classList.add('recarga-item');

        const p = document.createElement('p');
        p.innerHTML = `
          <strong>Monto:</strong> $${item.monto} |
          <strong>Estado:</strong> ${item.estado} |
          <strong>Fecha:</strong> ${item.fecha}
        `;

        if (item.estado === 'pendiente') {
          const btn = document.createElement('button');
          btn.className = 'aceptar-recarga';
          btn.dataset.id = item.id_recarga;
          btn.textContent = 'Aceptar';
          btn.addEventListener('click', () => aceptarRecarga(item.id_recarga));
          p.appendChild(document.createTextNode(' | '));
          p.appendChild(btn);
        }

        li.appendChild(p);
        return li;
      }, 'No hay recargas solicitadas.');
    })
    .catch(err => {
      recargasList.innerHTML = '<li style="color:red">Error al cargar recargas.</li>';
      console.error(err);
    });
});

// [IDF-0012] El Administrador aprueba una solicitud de saldo
function aceptarRecarga(id_recarga) {
  fetch('/accept_recarga', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id_recarga })
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        alert('✅ Recarga aceptada con éxito.');
        location.reload(); // refresca para actualizar estado
      } else {
        alert('❌ Error al aceptar la recarga.');
      }
    })
    .catch(err => {
      console.error('Error al aceptar recarga:', err);
      alert('Error al conectar con el servidor.');
    });
}
