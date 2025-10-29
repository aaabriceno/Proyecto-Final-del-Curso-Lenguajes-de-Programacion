import {createContentType} from './create_item.js';

// [IDF-0023] retorna el role del inicio de sesion, si es cliente, administrador, o un usuario.
document.addEventListener('DOMContentLoaded', function () {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    let hasRated = false;
    
    fetch('/verificate_downloaded_content', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: id })
        })
        .then(response => response.json())
        .then(respuesta => {
            if (!respuesta.success) {
                window.location.href = `item_shop.html?id=${id}`;
            }
            else{
                hasRated = respuesta.hasRated || false;
            }
        })
        .catch(error => {
            console.error('Error:', error);
    });

    var header = document.createElement('header');
    var scriptAdmi = document.createElement('script');
    var current_role = "Usuario";

    fetch('/get_user_role')
        .then(response => response.json())
        .then(data => {
            console.log(data.role);
                if (data.role == "Administrador") {
                    current_role = "Administrador";
                } else if (data.role == "Cliente") {
                    current_role = "Cliente";
                }
                console.log(current_role);
                itemGen(current_role, id, hasRated);
            })
    .catch(error => {
        console.error('Error al verificar rol:', error);
        alert("Error al verificar tu rol.");
    });
    header.appendChild(scriptAdmi);
    console.log(current_role);

});

// [IDF-0028] Solicita información de cierto contenido al servidor.
function itemGen(current_role, id, hasRated){
    const itemDetails = document.querySelector('.container');

    if (!id) {
        itemDetails.innerHTML = "<p>Error: No se proporcionó un ID válido.</p>";
        return;        
    }

    var buyButton = document.createElement('button');
    buyButton.className = 'buy-button';
    buyButton.textContent = 'Descargar';

    fetch('/get_content_by_id', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
    .then(response => response.json())
    .then(data => {
            if (data) {
                itemDetails.innerHTML = '';
                
                createContentType({data:data,current_role:current_role, linked:false});

                buyButton.addEventListener('click', function () {
                    alert("CONTENIDO DESCARGADO :D");
                    descargarContenido(data.id, data.title);
                    //itemGen(current_role,id, hasRated);
                });

                var Div = document.querySelector(".media-item");
                Div.appendChild(buyButton);

                if(current_role === "Cliente"){
                    var giftButton = document.createElement('button');
                    giftButton.textContent = 'Regalar';
                    giftButton.className = 'gift-button';
                    giftButton.style.marginLeft = '10px';
                    giftButton.addEventListener('click', function () {
                        window.location.href = `item_shop.html?id=${data.id}&gift=1`;
                    });
                    console.log("gift");
                    Div.appendChild(giftButton);
                }

                if (current_role === "Cliente" && hasRated === true) {
                    var rateButton = document.createElement('button');
                    rateButton.textContent = 'Calificar contenido';
                    rateButton.className = 'rate-button';
                    rateButton.style.marginLeft = '10px';
                    rateButton.addEventListener('click', function () {
                        showRatingPrompt(data.id);
                        //itemGen(current_role,id, hasRated);
                    });
                    Div.appendChild(rateButton);
                }                
            
            } else {
                itemDetails.innerHTML = "<p>No se encontró el item.</p>";
            }
        })
        .catch(error => {
            console.error('Error obteniendo el item:', error);
            itemDetails.innerHTML = "<p>Error cargando el contenido.</p>";
        });
}

// [IDF-0006] pide el contenido al servidor y descarga el contenido en el dispositivo.
function descargarContenido(id,name) {
    fetch('/download_content', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
    .then(response => {
        if (!response.ok) {
            throw new Error("No se pudo descargar el archivo");
        }
        return response.blob();
    })
    .then(blob => {
        const downloadUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = downloadUrl;

        // Esto es solo temporal si no tienes el nombre desde JS.
        //a.download = "contenido_" + id;
        a.download = name;

        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(downloadUrl);
    })
    .catch(error => {
        console.error("Error:", error);
        alert("Error al descargar el archivo");
    })
    .finally(() => {
        location.reload();
    });
}

// [IDF-0008] solicita y registra una puntuación para cierto contenido.
function showRatingPrompt(contentId) {
    const overlay = document.createElement('div');
    overlay.style.position = 'fixed';
    overlay.style.top = 0;
    overlay.style.left = 0;
    overlay.style.width = '100vw';
    overlay.style.height = '100vh';
    overlay.style.background = 'rgba(0, 0, 0, 0.6)';
    overlay.style.display = 'flex';
    overlay.style.justifyContent = 'center';
    overlay.style.alignItems = 'center';
    overlay.style.zIndex = 1000;

    const modal = document.createElement('div');
    modal.style.background = 'white';
    modal.style.padding = '20px';
    modal.style.borderRadius = '10px';
    modal.style.boxShadow = '0 0 10px rgba(0,0,0,0.3)';
    modal.style.textAlign = 'center';

    const message = document.createElement('p');
    message.textContent = '¿Deseas calificar este contenido? (Opcional)';
    
    const input = document.createElement('input');
    input.type = 'number';
    input.min = 1;
    input.max = 10;
    input.placeholder = 'Ingresa una puntuación del 1 al 10';
    input.style.margin = '10px';

    const sendButton = document.createElement('button');
    sendButton.textContent = 'Enviar';
    sendButton.style.margin = '5px';

    const cancelButton = document.createElement('button');
    cancelButton.textContent = 'Cancelar';
    cancelButton.style.margin = '5px';

    sendButton.addEventListener('click', () => {
        const score = parseFloat(input.value);
        if (isNaN(score) || score < 1 || score > 10) {
            alert("La puntuación debe ser un número entre 1 y 10.");
            return;
        }

        fetch('/rate_content', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ id: contentId, score: score })
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                alert("¡Gracias por tu puntuación!");
                location.reload();
            } else {
                alert("Hubo un error al enviar tu puntuación.");
            }
            document.body.removeChild(overlay);
            //window.location.href = `item_view.html?id=${contentId}`;
        })
        .catch(err => {
            alert("Error al calificar.");
            console.error(err);
        });
    });

    cancelButton.addEventListener('click', () => {
        document.body.removeChild(overlay);
        //window.location.href = `item_view.html?id=${contentId}`;
    });

    modal.appendChild(message);
    modal.appendChild(input);
    modal.appendChild(sendButton);
    modal.appendChild(cancelButton);
    overlay.appendChild(modal);
    document.body.appendChild(overlay);
}