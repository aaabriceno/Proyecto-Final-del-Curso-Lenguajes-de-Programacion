document.addEventListener('DOMContentLoaded', () => {
  const form = document.querySelector('form');
  const btnSubmit = form.querySelector('button[type="submit"]');

  // ============================================================
  // [IDF-0001] Envío de datos para inicio de sesión
  // ============================================================
  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const name = document.getElementById('name').value.trim();
    const password = document.getElementById('password').value.trim();

    if (!name || !password) {
      alert('⚠️ Por favor, completa todos los campos.');
      return;
    }

    // Desactivar botón temporalmente
    btnSubmit.disabled = true;
    btnSubmit.textContent = 'Iniciando sesión...';

    try {
      const res = await fetch('/signin', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, password })
      });

      if (!res.ok) {
        throw new Error(`Error HTTP: ${res.status}`);
      }

      const data = await res.json();

      if (data.success) {
        // Redirección según el rol o URL recibida
        window.location.href = data.url;
      } else {
        // Limpia los campos y alerta el mensaje
        form.reset();
        alert(`❌ ${data.message || 'Credenciales incorrectas.'}`);
      }

    } catch (error) {
      console.error('Error en inicio de sesión:', error);
      alert('Error al conectar con el servidor.');
    } finally {
      // Reactivar botón
      btnSubmit.disabled = false;
      btnSubmit.textContent = 'Iniciar Sesión';
    }
  });
});
