document.addEventListener('DOMContentLoaded', function () {
    const usernameInput = document.getElementById('username');
    const nombreInput = document.getElementById('nombre');
    const apellidoPInput = document.getElementById('apellido-paterno');
    const apellidoMInput = document.getElementById('apellido-materno');
    const emailInput = document.getElementById('email');
    const password = document.getElementById('password');
    const repeatPassword = document.getElementById('repeat-password');
    const errorMessage = document.getElementById('error-message');
    const submitButton = document.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    
    // [IDF-0211] Valida los campos de registro para ver si no estan vacios.
    function checkEmptyFields() {
        if (
            usernameInput.value === "" ||
            nombreInput.value === "" ||
            apellidoPInput.value === "" ||
            apellidoMInput.value === "" ||
            emailInput.value === "" ||
            password.value === "" ||
            repeatPassword.value === ""
        ) {
            submitButton.disabled = true;
        } else {
            submitButton.disabled = false;
        }
    }

    // [IDF-0210] Valida el fullname para ver si es valido en la parte del registro.
    function validateNameCampo(input) {
        const namePattern = /^[a-zA-ZáéíóúÁÉÍÓÚñÑ\s]+$/;
        if (!namePattern.test(input.value)) {
            errorMessage.textContent = "Nombres y apellidos solo deben contener letras";
            submitButton.disabled = true;
        } else {
            errorMessage.textContent = "";
            checkEmptyFields();
        }
    }
    
    // [IDF-0209] Valida el email con regex para ver si es valido en la parte del registro.
    function validateEmail() {
        const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailPattern.test(emailInput.value)) {
            errorMessage.textContent = "Correo electrónico inválido";
            submitButton.disabled = true;
        } else {
            errorMessage.textContent = "";
            checkEmptyFields();
        }
    }

    // [IDF-0208] Valida la password y la confirmacion de esta, ingresada para el registro.
    function validatePasswords() {
        if (password.value !== repeatPassword.value) {
            errorMessage.textContent = "Las contraseñas no coinciden";
            submitButton.disabled = true;
        } else {
            errorMessage.textContent = "";
            checkEmptyFields();
        }
    }

    // Validaciones
    nombreInput.addEventListener('input', () => validateNameCampo(nombreInput));
    apellidoPInput.addEventListener('input', () => validateNameCampo(apellidoPInput));
    apellidoMInput.addEventListener('input', () => validateNameCampo(apellidoMInput));
    emailInput.addEventListener('input', validateEmail);
    repeatPassword.addEventListener('input', validatePasswords);
    usernameInput.addEventListener('input', checkEmptyFields);

    checkEmptyFields();

    const form = document.querySelector('form');

    // [IDF-0002] Envía los datos de un usuario para registrarlo.
    form.addEventListener('submit', function (event) {
        event.preventDefault();

        const requestData = {
            username: usernameInput.value,
            nombre: nombreInput.value,
            apellido_paterno: apellidoPInput.value,
            apellido_materno: apellidoMInput.value,
            password: password.value,
            email: emailInput.value
        };

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/register');
        xhr.setRequestHeader('Content-Type', 'application/json');

        xhr.onload = function () {
            if (xhr.status === 200) {
                const responseData = JSON.parse(xhr.responseText);
                if (responseData.success) {
                    window.location.href = "login.html";
                } else {
                    errorMessage.textContent = "Ya existe un usuario con ese nombre de cuenta.";
                }
            } else {
                alert('Hubo un error al enviar la solicitud.');
            }
        };

        xhr.onerror = function () {
            console.error('Error de red al intentar enviar la solicitud.');
        };

        xhr.send(JSON.stringify(requestData));
    });
});
