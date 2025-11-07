import {createContentType} from './create_item.js';

// [IDF-0031] Crea la parte gráfica del interfaz compra.
document.addEventListener('DOMContentLoaded', function () {
    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    const isGift = params.get('gift') === '1';

    const itemDetails = document.querySelector('.container');

    if (!id) {
        itemDetails.innerHTML = "<p>Error: No se proporcionó un ID válido.</p>";
        return;
    }

    var buyButton = document.createElement('button');
    buyButton.className = 'buy-button';
    buyButton.textContent = 'Comprar';

    fetch('/get_content_by_id', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ id: id })
    })
        .then(response => response.json())
        .then(data => {
            if(data) {
                itemDetails.innerHTML = '';
                createContentType({data:data,linked:false});

                buyButton.addEventListener('click', function () {
                    fetch('/pagarContenido', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ id: data.id })
                    })
                    .then(response => response.json())
                    .then(respuesta => {
                        if (respuesta.success) {
                            alert("CONTENIDO COMPRADO EXITOSAMENTE :D");
                            window.location.href = `item_view.html?id=${data.id}`;
                        } else {
                            alert("NO TIENE SALDO SUFICIENTE D:");
                        }
                    })
                    .catch(error => {
                        console.error('Error:', error);
                    });
                });
            } else {
                itemDetails.innerHTML = "<p>No se encontró el item.</p>";
            }

            if (isGift) {
                genRegaloBar(id);
            }
            else{
                var Div = document.querySelector(".media-item");
                Div.appendChild(buyButton);
            }
        })
        .catch(error => {
            console.error('Error obteniendo el item:', error);
            itemDetails.innerHTML = "<p>Error cargando el contenido.</p>";
        });
});

// [IDF-0007] envia la peticón al server para verificar y enviar un regalo a un cliente existente.
function genRegaloBar(id){
    const container = document.querySelector('.media-item');
    const label = document.createElement('label');
    label.textContent = 'Usuario destinatario: ';
    const input = document.createElement('input');
    input.type = 'text';
    input.id = 'recipient';
    input.placeholder = 'Nombre de usuario o correo';
    input.style.margin = '10px';

    const sendBtn = document.createElement('button');
    sendBtn.textContent = 'Enviar regalo';
    sendBtn.className = 'send-gift';
    sendBtn.addEventListener('click', () => {
        const recipient = input.value.trim();
        if (!recipient) {
            alert('Debes ingresar un destinatario.');
            return;
        }
        fetch('/gift_content', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                id: id,
                destinatario: recipient
            })
        })
        .then(res => res.json())
        .then(response => {
            if (response.success) {
                alert("El regalo ha sido enviado con éxito.");
                window.location.href = `user_view.html`;
            } 
            else{
                alert(response.msg);
            }
        })
        .catch(err => {
            console.error('Error al enviar regalo:', err);
            alert("Error al conectar con el servidor.");
        });
    });

    container.appendChild(label);
    container.appendChild(input);
    container.appendChild(sendBtn);
}