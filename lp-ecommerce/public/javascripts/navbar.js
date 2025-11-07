// [IDF-0026] Genera una barra de navegación para un usuario sin cuenta.
function generateNavbar() {
  const header = document.createElement('header');
  header.setAttribute('role', 'banner');
  header.classList.add('navbar-guest');

  // === Cargar estilos globales ===
  const stylevar = document.createElement('link');
  stylevar.href = '/styles/navbar.css';
  stylevar.rel = 'stylesheet';
  stylevar.type = 'text/css';
  header.appendChild(stylevar);

  // === Contenedor de navegación ===
  const nav = document.createElement('nav');
  nav.setAttribute('role', 'navigation');

  const ul = document.createElement('ul');

  // === Logo principal ===
  const liLogo = document.createElement('li');
  const btnLogo = document.createElement('button');
  btnLogo.textContent = 'DownEz';
  btnLogo.id = 'logo-btn';
  btnLogo.className = 'logo-button';
  btnLogo.addEventListener('click', () => {
    window.location.href = '/';
  });
  liLogo.appendChild(btnLogo);
  ul.appendChild(liLogo);

  // === Opciones ===
  const options = {
    Register: 'register.html',
    'Sign in': 'login.html',
  };

  for (const [key, link] of Object.entries(options)) {
    const li = document.createElement('li');
    const a = document.createElement('a');
    a.href = link;
    a.textContent = key;
    a.setAttribute('aria-label', `Ir a ${key}`);
    li.appendChild(a);
    ul.appendChild(li);
  }

  // === Insertar en el DOM ===
  nav.appendChild(ul);
  header.appendChild(nav);
  document.body.insertBefore(header, document.body.firstChild);
}

// Inicialización
document.addEventListener('DOMContentLoaded', generateNavbar);
