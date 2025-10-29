
// [IDF-0029] Función que renderiza la información de un contenido, ya sea de video, audio, o imagen.
export function createMediaInfo({data=null, Div=null}) {
    var infoDiv = document.createElement('div');
    infoDiv.className = 'info';
    
    var author = document.createElement('p');
    author.textContent = 'Autor: ' + data.author;

    var price = document.createElement('p');
    price.innerHTML = `Precio: ${data.price}`;

    var extension = document.createElement('p');
    extension.textContent = 'Extensión de archivo: ' + data.extension;

    var category = document.createElement('p');
    category.textContent = 'Categoría: ' + data.category;

    var rating = document.createElement('p');
    rating.textContent = 'Nota promedio: ' + data.rating;

    var description = document.createElement('p');
    description.textContent = 'Descripción: ' + data.description;

    var downloaded = document.createElement('p');
    downloaded.textContent = 'Descargas: ' + data.downloaded;
    
    //var buyButton = document.createElement('button');
    //buyButton.className = 'buy-button';

    //buyButton.dataset.id = data.id;

    infoDiv.appendChild(author);
    infoDiv.appendChild(price);
    infoDiv.appendChild(extension);
    infoDiv.appendChild(category);
    infoDiv.appendChild(rating);
    infoDiv.appendChild(description);
    infoDiv.appendChild(downloaded);
    Div.appendChild(infoDiv);
}

// [IDF-0030] Renderización el tipo de media de cierto contenido.
export function createContentType({data = null, current_role = null, linked=true}) {
    var Div = document.createElement('div');
    Div.className = 'media-item' + (linked ? ' linked' : '');

    var title = document.createElement('h2');
    title.textContent = data.title;
    Div.appendChild(title);

    if (data.type == "imagen") {
        var img = document.createElement('img');
        img.src = data.src;
        img.className = "media";
        Div.appendChild(img);
    } else if (data.type == "audio") {    
        var au = document.createElement('audio');
        au.src = data.src;
        au.controls = true;
        au.className = "media";
        Div.appendChild(au);
    } else {
        var video = document.createElement('video');
        video.controls = true;
        video.src = data.src;
        video.type = "video/mp4";
        video.textContent = 'Your browser does not support the video element.';
        Div.appendChild(video);
    }


    createMediaInfo({data:data, Div:Div});

    if(linked){
        Div.style.cursor = 'pointer';
        Div.addEventListener('click', function () {
            // [IDF-0005] envia al servidor la verificaion si puede decargar cierto contenido.
            fetch('/verificate_downloaded_content', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ id: data.id })
            })
            .then(response => response.json())
            .then(respuesta => {
                if (respuesta.success) {
                    window.location.href = `item_view.html?id=${data.id}`;
                } 
                else {
                    if(current_role=='Cliente'){
                        window.location.href = `item_shop.html?id=${data.id}`;
                    }
                    else{
                        window.location.href = `register.html`;
                    }
                }
            })
            .catch(error => {
                //alert('Error:', error);
                window.location.href = `register.html`;
            });
        });
    }
    var Content = document.querySelector('.container');
    Content.appendChild(Div);
}