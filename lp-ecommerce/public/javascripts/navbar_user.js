import { gen_searchBar } from './generator_searchBar.js';

// ============================================================
// [IDF-0024] Genera la barra de navegación para un Cliente
// ============================================================
document.addEventListener('DOMContentLoaded', async () => {
  try {
    const res = await fetch('/get_user_role');
    const data = await res.json();

    if (!data.role || data.role === 'Invitado') {
      window.location.href = '/';
      return;
    }

    const href_logo =
      data.role === 'Administrador'
        ? 'admi_view.html'
        : data.role === 'Cliente'
        ? 'user_view.html'
        : '/';

    generateNavbarCliente(href_logo);
    initNotificaciones();
  } catch (error) {
    console.error('Error al verificar rol:', error);
    alert('Error al verificar tu sesión.');
  }
});

// ============================================================
// Construcción de Navbar
// ============================================================
function generateNavbarCliente(href_logo) {
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

  // Barra de búsqueda
  ul.appendChild(gen_searchBar());

  // Saldo
  const liSaldo = document.createElement('li');
  liSaldo.id = 'user-balance';
  liSaldo.textContent = 'Saldo: Cargando...';
  ul.appendChild(liSaldo);

  // Menú de opciones
  const options = {
    Cuenta: 'user_account.html',
    Notificaciones: '#',
    'Sign out': 'login.html',
  };

  for (const [key, link] of Object.entries(options)) {
    const li = document.createElement('li');

    if (key === 'Notificaciones') {
      const btn = document.createElement('button');
      btn.id = 'notificaciones-btn';
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

  obtenerSaldo();
}

// ============================================================
// [IDF-0018] Obtiene el saldo actual del cliente
// ============================================================
async function obtenerSaldo() {
  const saldoElement = document.getElementById('user-balance');
  try {
    const res = await fetch('/get_balance');
    const data = await res.json();
    if (data.success) {
      saldoElement.textContent = `Saldo: $${parseFloat(data.saldo).toFixed(2)}`;
    } else {
      saldoElement.textContent = 'Saldo: Error al cargar';
    }
  } catch (err) {
    console.error('Error al obtener el saldo:', err);
    saldoElement.textContent = 'Saldo: Error';
  }
}

// ============================================================
// [IDF-0021 / IDF-0022] Gestión de notificaciones del cliente
// ============================================================
function initNotificaciones() {
  let notiContainer = document.getElementById('recargas-container');
  if (!notiContainer) {
    notiContainer = document.createElement('div');
    notiContainer.id = 'recargas-container';
    notiContainer.style.display = 'none';
    document.body.appendChild(notiContainer);
  }

  const btn = document.getElementById('notificaciones-btn');
  if (btn) {
    btn.addEventListener('click', () => {
      notiContainer.style.display = 'block';
      obtenerNotificaciones();
    });
  }

  async function obtenerNotificaciones() {
    try {
      const res = await fetch('/get_notificaciones');
      const data = await res.json();

      notiContainer.innerHTML = `
        <button id="close-recargas">✖</button>
        <h3>Notificaciones</h3>
      `;

      document.getElementById('close-recargas').onclick = () => {
        notiContainer.style.display = 'none';
      };

      if (!data.length) {
        notiContainer.innerHTML += `
          <p class="text-muted text-center mt-3">No tienes notificaciones pendientes.</p>
        `;
        return;
      }

      data.forEach(notifi => {
        const div = document.createElement('div');
        div.classList.add('recarga-item');
        div.innerHTML = `
          <p>${notifi.messagge}</p>
          <button class="aceptar-notifi" data-id="${notifi.id_notificacion}">
            Aceptar
          </button>
        `;
        notiContainer.appendChild(div);
      });

      notiContainer.querySelectorAll('.aceptar-notifi').forEach(btn => {
        btn.addEventListener('click', async () => {
          await aceptarNotificacion(btn.dataset.id);
        });
      });
    } catch (err) {
      console.error('Error al obtener notificaciones:', err);
      notiContainer.innerHTML = `
        <p class="text-danger text-center">Error al cargar notificaciones.</p>
      `;
    }
  }

  async function aceptarNotificacion(id_notificacion) {
    try {
      const res = await fetch('/accept_notificacion', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id_notificacion }),
      });
      const data = await res.json();

      if (data.success) {
        obtenerNotificaciones();
      } else {
        alert('Error al aceptar la notificación.');
      }
    } catch (err) {
      console.error('Error al aceptar notificación:', err);
      alert('Error de conexión.');
    }
  }
}

// ============================================================
// [IDF-0011] Cerrar sesión
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
