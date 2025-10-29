import {createContentType} from './create_item.js';

var data_cache = [];
var current_option = 'imagen';

// [RNF-0017] funcion que envia la renderización de un contenido para renderizarlo en imagen, video, o audio.
function showContent(contentType, current_role) {
    document.querySelector('.container').innerHTML = '';
    current_option = contentType;
    data_cache.forEach(element => {
        if(element.type==contentType){
            createContentType({data:element, current_role:current_role});
        }
    });
}

// [IDF-0016] solicita al servidor los contenidos más descargados.
document.addEventListener('DOMContentLoaded', function () {
    var current_role = "usuario";

    fetch('/get_user_role')
        .then(response => response.json())
        .then(data => {
            if (data.role === 'Administrador') {
                current_role = "Administrador";
            } else if (data.role === 'Cliente') {
                current_role = "Cliente";
            }

            const tops = ['imagen','audio','video'];
            tops.forEach(i => {
                const boton = document.getElementById(i + "-top");
                boton.addEventListener('click', () => showContent(i, current_role));
            });

            const select = document.getElementById("content-filter");

            function loadTopContent() {
                const parameter = select.value;

                fetch('/top_content_downloaded', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ parameter: parameter })
                })
                .then(response => {
                    if (!response.ok) {
                        throw new Error('Network response was not ok');
                    }
                    return response.json();
                })
                .then(data => {
                    data_cache = data;
                    showContent(current_option, current_role);
                })
                .catch(error => {
                    console.error('Error:', error);
                });
            }

            // Cargar por defecto
            loadTopContent();

            // Recargar cuando cambie el filtro
            select.addEventListener("change", loadTopContent);
        })
        .catch(error => {
            console.error('Error al verificar rol:', error);
        });
});
