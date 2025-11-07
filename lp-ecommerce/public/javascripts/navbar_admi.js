import { gen_searchBar } from './generator_searchBar.js';

// ============================================================
// [IDF-0025] Genera la barra de navegación para un Administrador
// ============================================================
document.addEventListener('DOMContentLoaded', async function () {
  try {
    const res = await fetch('/get_user_role');
    const data = await res.json();

    if (!data.role || data.role === 'Invitado') {
      window.location.href = '/';
      return;
    }

    const href_logo = data.role === 'Administrador'
      ? 'admi_view.html'
      : data.role === 'Cliente'
        ? 'user_view.html'
        : '/';

    generateNavbarAdministrador(href_logo);

  } catch (error) {
    console.error('Error al verificar rol:', error);
    window.location.href = '/';
  }

  // ======================
  // Submódulo: Recargas
  // ======================
  initRecargas();
});


// ============================================================
// Construcción de Navbar
// ============================================================
function generateNavbarAdministrador(href_logo) {
  const header = document.createElement('header');
  header.innerHTML = `
    <link rel="stylesheet" href="/styles/navbar.css" type="text/css">
    <link rel="stylesheet" href="/styles/recargas_admi.css" type="text/css">
  `;

  const nav = document.createElement('nav');
  const ul = document.createElement('ul');

  // Logo
  const liLogo = document.createElement('li');
  const btnLogo = document.createElement('button');
  btnLogo.textContent = 'DownEz';
  btnLogo.id = 'logo-btn';
  btnLogo.addEventListener('click', () => (window.location.href = href_logo));
  liLogo.appendChild(btnLogo);
  ul.appendChild(liLogo);

  // SearchBar
  ul.appendChild(gen_searchBar());

  // Opciones de navegación
  const options = {
    Transacciones: 'transacciones.html',
    'Agregar Contenido': 'addContent.html',
    Recargas: '#',
    'Sign out': 'login.html',
  };

  for (const [key, link] of Object.entries(options)) {
    const li = document.createElement('li');

    if (key === 'Recargas') {
      const btn = document.createElement('button');
      btn.id = 'recargas-btn';
      btn.textContent = key;
      li.appendChild(btn);
    } else if (key === 'Sign out') {
      const btn = document.createElement('button');
      btn.id = 'logout-btn';
      btn.textContent = key;
      btn.addEventListener('click', handleLogout);
      li.appendChild(btn);
    } else {
      const a = document.createElement('a');
      a.href = link;
      a.textContent = key;
      li.appendChild(a);
    }

    ul.appendChild(li);
  }

  nav.appendChild(ul);
  header.appendChild(nav);
  document.body.insertBefore(header, document.body.firstChild);
}

// ============================================================
// [IDF-0011] Cerrar sesión del administrador
// ============================================================
function handleLogout() {
  fetch('/logout_account', { method: 'GET' })
    .then(res => {
      if (res.ok) window.location.href = 'login.html';
      else window.location.href = '/';
    })
    .catch(err => {
      console.error('Error durante logout:', err);
      alert('Error al cerrar sesión');
      window.location.href = '/';
    });
}

// ============================================================
// [IDF-0013 / IDF-0012] Gestión de recargas
// ============================================================
function initRecargas() {
  let recargasContainer = document.getElementById('recargas-container');
  if (!recargasContainer) {
    recargasContainer = document.createElement('div');
    recargasContainer.id = 'recargas-container';
    recargasContainer.style.display = 'none';
    document.body.appendChild(recargasContainer);
  }

  const btn = document.getElementById('recargas-btn');
  if (btn) {
    btn.addEventListener('click', () => {
      recargasContainer.style.display = 'block';
      obtenerRecargas();
    });
  }

  async function obtenerRecargas() {
    try {
      const res = await fetch('/get_recargas');
      const data = await res.json();

      recargasContainer.innerHTML = `
        <button id="close-recargas">✖</button>
        <h3>Solicitudes de Recarga</h3>
      `;

      document.getElementById('close-recargas').onclick = () => {
        recargasContainer.style.display = 'none';
      };

      if (!data.length) {
        recargasContainer.innerHTML += `
          <p class="text-muted text-center mt-3">No hay solicitudes pendientes.</p>
        `;
        return;
      }

      data.forEach(recarga => {
        const div = document.createElement('div');
        div.classList.add('recarga-item');
        div.innerHTML = `
          <p><strong>Usuario:</strong> ${recarga.usuario}</p>
          <p><strong>Monto:</strong> $${recarga.monto}</p>
          <button class="aceptar-recarga" data-id="${recarga.id_recarga}">Aceptar</button>
        `;
        recargasContainer.appendChild(div);
      });

      recargasContainer.querySelectorAll('.aceptar-recarga').forEach(btn => {
        btn.addEventListener('click', async () => {
          await aceptarRecarga(btn.dataset.id);
        });
      });

    } catch (err) {
      console.error('Error al obtener recargas:', err);
      recargasContainer.innerHTML = `
        <p class="text-danger">Error al cargar recargas.</p>
      `;
    }
  }

  async function aceptarRecarga(id_recarga) {
    try {
      const res = await fetch('/accept_recarga', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id_recarga }),
      });
      const data = await res.json();

      if (data.success) {
        alert('✅ Recarga aceptada con éxito');
        obtenerRecargas();
      } else {
        alert('⚠️ Error al aceptar la recarga');
      }
    } catch (err) {
      console.error('Error al aceptar recarga:', err);
      alert('Error de conexión con el servidor.');
    }
  }
}
