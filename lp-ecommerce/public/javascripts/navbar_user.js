import { gen_searchBar } from './generator_searchBar.js';

// [IDF-0024] Genera una barra de navegacion para un Cliente.
function generateNavbarCliente() {
    var href_logo="#";
    fetch('/get_user_role')
            .then(response => response.json())
            .then(data => {
                if (data.role === 'Administrador') {
                    href_logo = 'admi_view.html';
                } else if (data.role === 'Cliente') {
                    href_logo = 'user_view.html';
                } else {
                    //alert("Sesión inválida o no identificada.");
                    //console.log("Sesión inválida o no identificada.");
                    window.location.href = '/';
                }
            })
            .catch(error => {
                console.error('Error al verificar rol:', error);
                //alert("Error al verificar tu rol.");
                window.location.href = '/';
    });    
    var header = document.createElement('header');
    var stylevar = document.createElement('link');
    stylevar.href = "/styles/navbar.css";
    stylevar.rel = "stylesheet"; 
    stylevar.type = "text/css";

    var stylevar2 = document.createElement('link');
    stylevar2.href = "/styles/recargas_admi.css";
    stylevar2.rel = "stylesheet"; 
    stylevar2.type = "text/css";

    var nav = document.createElement('nav');
    var ul = document.createElement('ul');

    var liLogo = document.createElement('li');
    var btnLogo = document.createElement('button');
    btnLogo.textContent = 'DownEz';
    btnLogo.id = 'logo-btn';
    btnLogo.addEventListener('click', function () {
        window.location.href = href_logo;
    });
    liLogo.appendChild(btnLogo);
    ul.appendChild(liLogo);

    let searchLi = gen_searchBar();
    ul.appendChild(searchLi);

    var liSaldo = document.createElement('li');
    liSaldo.id = 'user-balance';
    liSaldo.textContent = 'Saldo: Cargando...';
    ul.appendChild(liSaldo);

    var options = {"Cuenta":"user_account.html","Notificaciones":"#", "Sign out": "login.html"};
    for (var key in options) {
        var liOption = document.createElement('li');

        if (key === "Notificaciones") {
            var btn = document.createElement('button');
            btn.textContent = key;
            btn.id = "recargas-btn";
            liOption.appendChild(btn);
        } else if (key === "Sign out") {
            var signOutBtn = document.createElement('button');
            signOutBtn.id = "logout-btn";
            signOutBtn.textContent = key;
            signOutBtn.addEventListener('click', function () {
                fetch('/logout_account', {
                    method: 'GET',
                    headers: {
                        'Content-Type': 'application/json'
                    }
                })
                .then(response => {
                    if (response.ok) {
                        window.location.href = 'login.html';
                    } else {
                        //alert('Error al cerrar sesión');
                        window.location.href = '/';
                    }
                })
                .catch(error => {
                    console.error('Error durante logout:', error);
                    //alert('Error al cerrar sesión');
                    window.location.href = '/';
                });
            });
            liOption.appendChild(signOutBtn);
        } else {
            var aOption = document.createElement('a');
            aOption.href = options[key];
            aOption.textContent = key;
            liOption.appendChild(aOption);
        }

        ul.appendChild(liOption);
    }

    nav.appendChild(ul);
    header.appendChild(nav);
    header.appendChild(stylevar);
    header.appendChild(stylevar2);
    document.body.insertBefore(header, document.body.firstChild);

    obtenerSaldo();
}

document.addEventListener('DOMContentLoaded', function () {
    generateNavbarCliente();
});


document.addEventListener('DOMContentLoaded', function () {

    if (!document.getElementById('recargas-container')) {
        var recargasContainer = document.createElement('div');
        recargasContainer.id = 'recargas-container';
        recargasContainer.style.display = 'none';
        document.body.appendChild(recargasContainer);
    }

    // [IDF-0021] Solicita las notificaciones del cliente actualmente logueado, tanto de regalos o recargas.
    function obtenerNotificaciones() {
        fetch('/get_notificaciones') 
            .then(response => response.json())
            .then(data => {
                const recargasElement = document.getElementById('recargas-container');
                recargasElement.innerHTML = '<button id="close-recargas">X</button>';
    
                document.getElementById('close-recargas').addEventListener('click', function () {
                    recargasElement.style.display = 'none';
                });
    
                data.forEach(recarga => {
                    var recargaDiv = document.createElement('div');
                    recargaDiv.classList.add('recarga-item');
                    recargaDiv.innerHTML = `
                        <p>${recarga.messagge}</p>
                        <button class="aceptar-notifi" data-id="${recarga.id_notificacion}">Aceptar</button>
                        `;                        
                    recargasElement.appendChild(recargaDiv);
                });
    
                var aceptarButtons = document.querySelectorAll('.aceptar-notifi');
                aceptarButtons.forEach(button => {
                    button.addEventListener('click', function () {
                        const recargaId = button.getAttribute('data-id');
                        aceptarNotificacion(recargaId);
                    });
                });
            })
            .catch(error => {
                console.error('Error al obtener recargas:', error);
                window.location.href = '/';
            });
    }

    // [IDF-0022] Acepta la notificación y la marca como leida.
    function aceptarNotificacion(id_notificacion) {
        fetch(`/accept_notificacion`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({id_notificacion: id_notificacion})
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                obtenerNotificaciones(); 
            } else {
                alert('Error al aceptar la notificacion');
            }
        })
        .catch(error => {
            console.error('Error al aceptar recarga:', error);
        });
    }

    setTimeout(() => {
        const recargasBtn = document.getElementById('recargas-btn');
        const recargasContainer = document.getElementById('recargas-container');
        
        if (recargasBtn) {
            recargasBtn.addEventListener('click', function () {
                obtenerNotificaciones();
                if (recargasContainer) {
                    recargasContainer.style.display = 'block';
                }
            });
        }
    }, 100);

});

// [IDF-0018] solicita el saldo actual del cliente.
function obtenerSaldo() {
    fetch('/get_balance')
        .then(response => response.json())
        .then(data => {
            const saldoElement = document.getElementById('user-balance');
            if (data.success) {
                const saldoFormateado = parseFloat(data.saldo).toFixed(2);
                saldoElement.textContent = `Saldo: $ ${saldoFormateado}`;
            } else {
                saldoElement.textContent = "Saldo: Error al cargar";
            }
        })
        .catch(error => {
            console.error('Error al obtener el saldo:', error);
            const saldoElement = document.getElementById('user-balance');
            saldoElement.textContent = "Saldo: Error";
            window.location.href = '/';
        });
}