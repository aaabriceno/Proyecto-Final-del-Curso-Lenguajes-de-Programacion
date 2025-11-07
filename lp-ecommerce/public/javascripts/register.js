document.addEventListener('DOMContentLoaded', function () {
  const usernameInput = document.getElementById('username');
  const nombreInput = document.getElementById('nombre');
  const apellidoPInput = document.getElementById('apellido-paterno');
  const apellidoMInput = document.getElementById('apellido-materno');
  const emailInput = document.getElementById('email');
  const passwordInput = document.getElementById('password');
  const repeatPasswordInput = document.getElementById('repeat-password');
  const errorMessage = document.getElementById('error-message');
  const submitButton = document.querySelector('button[type="submit"]');
  const form = document.querySelector('form');

  // Inicialmente deshabilitar
  submitButton.disabled = true;

  // === VALIDACIONES ===

  // [IDF-0211] Validar campos vacíos
  function checkEmptyFields() {
    const inputs = [
      usernameInput, nombreInput, apellidoPInput, apellidoMInput,
      emailInput, passwordInput, repeatPasswordInput
    ];
    const hasEmpty = inputs.some(input => input.value.trim() === '');
    submitButton.disabled = hasEmpty;
  }

  // [IDF-0210] Validar nombres/apellidos
  function validateNameCampo(input) {
    const pattern = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]+$/;
    if (!pattern.test(input.value.trim())) {
      showError("Nombres y apellidos solo deben contener letras.");
      submitButton.disabled = true;
      return false;
    }
    clearError();
    checkEmptyFields();
    return true;
  }

  // [IDF-0209] Validar email
  function validateEmail() {
    const pattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!pattern.test(emailInput.value.trim())) {
      showError("Correo electrónico inválido.");
      submitButton.disabled = true;
      return false;
    }
    clearError();
    checkEmptyFields();
    return true;
  }

  // [IDF-0208] Validar contraseñas
  function validatePasswords() {
    const pass = passwordInput.value;
    const repeat = repeatPasswordInput.value;

    if (pass.length < 6) {
      showError("La contraseña debe tener al menos 6 caracteres.");
      submitButton.disabled = true;
      return false;
    }

    if (pass !== repeat) {
      showError("Las contraseñas no coinciden.");
      submitButton.disabled = true;
      return false;
    }

    clearError();
    checkEmptyFields();
    return true;
  }

  // === EVENTOS ===
  [nombreInput, apellidoPInput, apellidoMInput].forEach(input =>
    input.addEventListener('input', () => validateNameCampo(input))
  );
  emailInput.addEventListener('input', validateEmail);
  passwordInput.addEventListener('input', validatePasswords);
  repeatPasswordInput.addEventListener('input', validatePasswords);
  [usernameInput, nombreInput, apellidoPInput, apellidoMInput, emailInput, passwordInput, repeatPasswordInput]
    .forEach(input => input.addEventListener('input', checkEmptyFields));

  // === FUNCIONES DE ERROR ===
  function showError(msg) {
    errorMessage.textContent = msg;
    errorMessage.style.display = 'block';
  }

  function clearError() {
    errorMessage.textContent = '';
    errorMessage.style.display = 'none';
  }

  // === ENVÍO DEL FORMULARIO ===
  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const requestData = {
      username: usernameInput.value.trim(),
      nombre: nombreInput.value.trim(),
      apellido_paterno: apellidoPInput.value.trim(),
      apellido_materno: apellidoMInput.value.trim(),
      password: passwordInput.value.trim(),
      email: emailInput.value.trim()
    };

    // Validación final antes del envío
    if (!validateNameCampo(nombreInput) || !validateEmail() || !validatePasswords()) {
      return;
    }

    submitButton.disabled = true;
    submitButton.textContent = "Registrando...";

    try {
      const res = await fetch('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestData)
      });

      const data = await res.json();

      if (res.ok && data.success) {
        window.location.href = "login.html";
      } else {
        showError(data.message || "Ya existe un usuario con ese nombre o correo.");
        submitButton.disabled = false;
        submitButton.textContent = "Registrarse";
      }

    } catch (err) {
      console.error('Error al registrar usuario:', err);
      showError("Error de conexión con el servidor.");
      submitButton.disabled = false;
      submitButton.textContent = "Registrarse";
    }
  });
});
