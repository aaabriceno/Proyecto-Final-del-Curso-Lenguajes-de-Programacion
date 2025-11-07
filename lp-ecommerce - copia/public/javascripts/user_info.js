// [IDF-0027] solicita informacion como id, saldo, estado cuenta, etc. de un cliente.
document.addEventListener('DOMContentLoaded', function () {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');

    const itemDetails = document.querySelector('#user-info');

    if (!id) {
        itemDetails.innerHTML = "<p>Error: No se proporcionó un ID válido.</p>";
        return;
    }

    fetch('/get_user_by_id', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
        .then(response => response.json())
        .then(data => {
            if (data) {
                itemDetails.innerHTML = `
                <p><strong>Usuario:</strong> ${data.username}</p>
                <p><strong>Nombre Completo:</strong> ${data.fullname}</p>
                <p><strong>Email:</strong> ${data.email}</p>
                <p><strong>ID:</strong> ${id}</p>
                <p><strong>saldo:</strong> ${data.saldo}</p>
                <p><strong>Estado cuenta:</strong> ${data.estado}</p>
            `;
                
            } else {
                itemDetails.innerHTML = "<p>No se encontró el item.</p>";
            }
        })
        .catch(error => {
            console.error('Error obteniendo el item:', error);
            itemDetails.innerHTML = "<p>Error cargando el contenido.</p>";
        });


    const downloadsList = document.getElementById("downloads-list");
    downloadsList.innerHTML = '';

    fetch("/get_user_downloads_info", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
    .then(res => res.json())
    .then(data => {
        if (data.length === 0) {
            downloadsList.innerHTML = '<li>No hay contenidos Comprados.</li>';
            return;
        }

        data.forEach(item => {
            const li = document.createElement("li");
            li.classList.add("recarga-item");
            li.innerHTML = `
                        <a href=item_view_admi.html?id=${item.id}><h4>${item.title}</a>
                        (${item.type})</h4>
                        <p><strong>Autor:</strong> ${item.author} | <strong>Puntuación:</strong> ${item.rating}</p>
            `;
            downloadsList.appendChild(li);
        });
    })
    .catch(err => {
        downloadsList.innerHTML = '<li style="color:red">Error al cargar contenidos.</li>';
        console.error(err);
    });

    const downloadstime = document.getElementById("downloads-list-time");
    downloadsList.innerHTML = '';

    fetch("/get_user_downloads_info_time", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
    .then(res => res.json())
    .then(data => {
        if (data.length === 0) {
            downloadstime.innerHTML = '<li>No hay contenidos descargados.</li>';
            return;
        }

        data.forEach(item => {
            const li = document.createElement("li");
            li.classList.add("recarga-item");
            li.innerHTML = `
                        <a href=item_view_admi.html?id=${item.id}><h4>${item.title}</a>
                        (${item.type})</h4>
                        <p><strong>Autor:</strong> ${item.author} | <strong>Puntuación:</strong> ${item.rating}</p>
            `;
            downloadstime.appendChild(li);
        });
    })
    .catch(err => {
        downloadstime.innerHTML = '<li style="color:red">Error al cargar contenidos.</li>';
        console.error(err);
    });

    const recargaslist = document.getElementById("recargas-list");
    recargaslist.innerHTML = '';
    fetch("/get_user_refills_info", {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
    .then(res => res.json())
    .then(data => {
        if (data.length === 0) {
            recargaslist.innerHTML = '<li>No hay recargas solicitadas.</li>';
            return;
        }

        data.forEach(item => {
            const li = document.createElement("li");
            li.classList.add("recarga-item");

            li.innerHTML = `
                <p>
                <strong>Monto:</strong> ${item.monto} | 
                <strong> Estado:</strong> ${item.estado}| 
                <strong> Fecha:</strong> ${item.fecha} | `;
            if(item.estado=="pendiente"){
                li.innerHTML += `<button class="aceptar-recarga" data-id="${item.id_recarga}">Aceptar</button>
                    </p>
                `;
            }
            li.innerHTML += `</p>`;                
            recargaslist.appendChild(li);
        });

        var aceptarButtons = document.querySelectorAll('.aceptar-recarga');
            aceptarButtons.forEach(button => {
                button.addEventListener('click', function () {
                    const recargaId = button.getAttribute('data-id');
                    aceptarRecarga(recargaId);
                });
        });

    })
    .catch(err => {
        recargaslist.innerHTML = '<li style="color:red">Error al cargar contenidos.</li>';
        console.error(err);
    });

});

// [IDF-0012] El Administrador aprueba la solicitud de saldo de un cliente.
function aceptarRecarga(id_recarga) {
    fetch(`/accept_recarga`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({id_recarga})
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            alert('Recarga aceptada con éxito');
        } else {
            alert('Error al aceptar la recarga');
        }
    })
    .catch(error => {
        console.error('Error al aceptar recarga:', error);
    });
}