// [IDF-0010] Se envia datos editados de un contenido, en forma de tipo binario y json de un contenido al servidor.
document.addEventListener("DOMContentLoaded", function () {
    const fileInput = document.getElementById("fileInput");
    const fileNameSpan = document.getElementById("file-name");
    const previewContainer = document.getElementById("preview-container");

    const params = new URLSearchParams(window.location.search);
    const id = params.get("id");

    // Si hay ID, obtener datos del contenido para edición
    if (id) {
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
                document.getElementById('content-type').value = data.type;
                document.getElementById('content-title').value = data.title;
                document.getElementById('content-author').value = data.author;

                const priceStr = String(data.price);
                document.getElementById('content-price-view').innerHTML = `Precio actual: ${priceStr}`;

                // Extraer el precio dentro de <s>...</s>
                const match = priceStr.match(/<s>(.*?)<\/s>/);
                if (match && match[1]) {
                    document.getElementById('content-price').value = match[1];
                } else {
                    // Si no hay <s>, asignar el precio limpio (sin HTML)
                    document.getElementById('content-price').value = priceStr.replace(/<[^>]*>/g, '');
                }
                
                document.getElementById('content-category').value = data.category;
                document.getElementById('content-description').value = data.description;
                console.log(data.estado);

                document.getElementById("send-eliminar").textContent = data.estado=="desactivado" ? "Restaurar" : "Eliminar";

                // Mostrar preview del contenido existente
                previewContainer.innerHTML = "";
                const contentType = data.type;
                if (contentType === "imagen") {
                    const img = document.createElement("img");
                    img.src = data.src;
                    img.style.maxWidth = "300px";
                    img.style.maxHeight = "300px";
                    previewContainer.appendChild(img);
                } else if (contentType === "video") {
                    const video = document.createElement("video");
                    video.src = data.src;
                    video.controls = true;
                    video.style.maxWidth = "300px";
                    previewContainer.appendChild(video);
                } else if (contentType === "audio") {
                    const audio = document.createElement("audio");
                    audio.src = data.src;
                    audio.controls = true;
                    previewContainer.appendChild(audio);
                }

                // Opcional: mostrar nombre del archivo cargado
                fileNameSpan.textContent = "Archivo cargado: " + data.title;
            } else {
                previewContainer.innerHTML = "<p>No se encontró el item.</p>";
            }
        })
        .catch(error => {
            console.error('Error obteniendo el item:', error);
            previewContainer.innerHTML = "<p>Error cargando el contenido.</p>";
        });
    }

    // Mostrar vista previa cuando se selecciona un archivo nuevo
    fileInput.addEventListener("change", function () {
        const file = this.files[0];
        previewContainer.innerHTML = "";
        const contentType = document.getElementById("content-type");

        if (file) {
            fileNameSpan.textContent = file.name;
            document.getElementById("content-title").value = file.name.trim();
            const fileType = file.type;
            const url = URL.createObjectURL(file);

            if (fileType.startsWith("image/")) {
                contentType.value = "imagen";
                const img = document.createElement("img");
                img.src = url;
                img.style.maxWidth = "300px";
                img.style.maxHeight = "300px";
                previewContainer.appendChild(img);

            } else if (fileType.startsWith("video/")) {
                contentType.value = "video";
                const video = document.createElement("video");
                video.src = url;
                video.controls = true;
                video.style.maxWidth = "300px";
                previewContainer.appendChild(video);

            } else if (fileType.startsWith("audio/")) {
                contentType.value = "audio";
                const audio = document.createElement("audio");
                audio.src = url;
                audio.controls = true;
                previewContainer.appendChild(audio);

            } else {
                previewContainer.textContent = "Tipo de archivo no compatible para vista previa.";
            }
        } else {
            fileNameSpan.textContent = "Ningún archivo seleccionado";
        }
    });

    // Enviar formulario
    document.getElementById("content-form").addEventListener("submit", function (e) {
        e.preventDefault();

        const contentType = document.getElementById("content-type").value.trim();
        const fileInput = document.getElementById("fileInput").files[0];
        const title = document.getElementById("content-title").value.trim();
        const author = document.getElementById("content-author").value.trim();
        const price = document.getElementById("content-price").value.trim();
        //const category = document.getElementById("content-category").value.trim();
        const description = document.getElementById("content-description").value.trim();
        const fileInputV = document.getElementById("fileInput");

        if (!id || !title || !author || !price || !description) {
            alert("Por favor, completa todos los campos sin dejar espacios en blanco.");
            return;
        }

        if (isNaN(price) || Number(price) < 0) {
            alert("Por favor, ingresa un precio válido (número positivo).");
            return;
        }

        const formData = new FormData(this);
        if (id) {
            formData.append("id", id);
        }

        if (!fileInputV.files || fileInputV.files.length === 0) {
            console.log("No se ha subido ningún archivo.");
            //alert(1);
            //formData.append("changes", false);
        }
        // else{
        //     formData.append("changes", true);
        // }

        fetch("/update_content", {
            method: "POST",
            body: formData
        })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                alert("Contenido guardado");
                window.location.href = "admi_view.html";
            } else {
                alert(data.message || "Error al guardar el contenido.");
            }
        })
        .catch(err => console.error("Error:", err));
    });

    del_button(id);

    const promoBtn = document.getElementById("send-promocion");
    const promoContainer = document.getElementById("promocion-container");

    promoBtn.addEventListener("click", function () {
        promoContainer.style.display = promoContainer.style.display === "none" ? "block" : "none";
        button_info_promos();
    });

    // Agregar promoción (redireccionar o mostrar un modal, tú eliges)
    document.getElementById("btn-agregar-promo").addEventListener("click", () => {
        const form = document.getElementById("form-agregar-promo");
        form.style.display = form.style.display === "none" ? "block" : "none";
    });

    document.getElementById("btn-guardar-promo").addEventListener("click", () => {
        enviar_nueva_promocion();
    });

    // Usar promoción
    document.getElementById("btn-usar-promo").addEventListener("click", function () {
        designar_promocion(id);
    });


    const catBtn = document.getElementById("send-category");
    const catContainer = document.getElementById("category-container");

    catBtn.addEventListener("click", function () {
        catContainer.style.display = catContainer.style.display === "none" ? "block" : "none";
        mostrar_categorias_disponibles();
    });

    document.getElementById("btn-agregar-category").addEventListener("click", () => {
        const form = document.getElementById("form-agregar-category");
        form.style.display = form.style.display === "none" ? "block" : "none";
    });

    document.getElementById("btn-guardar-category").addEventListener("click", () => {
        enviar_nueva_categoria();
    });

    document.getElementById("btn-usar-category").addEventListener("click", function () {
        designar_categoria(id);
    });
});


// [IDF-0150] Función que controla el estado del botón eliminar o restaurar contenido.
function del_button(id){
    let isDeleted = false;

    document.getElementById("send-eliminar").addEventListener("click", function () {
        if (!id) {
            alert("No hay un contenido cargado para eliminar/restaurar.");
            return;
        }

        fetch("/update_content_state", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ id: id})
        })
        .then(res => res.json())
        .then(data => {
            if (data.success) {
                isDeleted = data.estado || false;
                document.getElementById("send-eliminar").textContent = isDeleted ? "Restaurar" : "Eliminar";
                alert(data.message || (isDeleted ? "Contenido eliminado" : "Contenido restaurado"));
            } else {
                alert(data.message || "Error en la operación.");
            }
        })
        .catch(err => {
            console.error("Error:", err);
            alert("Error de conexión con el servidor.");
        });
    });    
}

// [IDF-0156] Funcion que hace un get de todas las promociones disponibles.
function button_info_promos(){
    const promoSelect = document.getElementById("promo-select");
    fetch("/get_promociones")
    .then(res => res.json())
    .then(data => {
        // Limpiar y rellenar el select
        promoSelect.innerHTML = `<option value="">Selecciona una promoción</option>`;
        data.forEach(promo => {
            const option = document.createElement("option");
            option.value = promo.id;
            option.textContent = `${promo.titulo_de_descuento} - Descuento: ${Math.round(promo.descuento * 100)}%`;
            promoSelect.appendChild(option);
        });
    })
    .catch(err => {
        console.error("Error cargando promociones:", err);
        alert("Error al obtener promociones.");
    });
}

// [IDF-0167] Función que asigna cierta promoción a un contenido.
function designar_promocion(id){
    const promoSelect = document.getElementById("promo-select");
    const selectedPromoId = promoSelect.value;
    if (!selectedPromoId) {
        alert("Selecciona una promoción para usar.");
        return;
    }

    if (!id) {
        alert("Primero guarda el contenido para poder asignarle una promoción.");
        return;
    }

    fetch("/asignar_promocion", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ id_contenido: id, id_promocion: selectedPromoId })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            alert("Promoción asignada correctamente.");
        } else {
            alert(data.message || "Error al asignar promoción.");
        }
    })
    .catch(err => {
        console.error("Error:", err);
        alert("Error al comunicar con el servidor.");
    })
    .finally(() => {
        location.reload();
    });    
}


// [IDF-0172] Función que envia y guarda los datos de una nueva categoria.
function enviar_nueva_promocion(){
    const titulo = document.getElementById("promo-title").value.trim();
    const descuento = parseFloat(document.getElementById("promo-descuento").value);
    const dias = parseInt(document.getElementById("promo-dias").value);

    if (!titulo || isNaN(descuento) || isNaN(dias) || descuento < 0 || descuento > 100 || dias <= 0) {
        alert("Completa correctamente todos los campos de promoción.");
        return;
    }

    fetch("/crear_promocion", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({
            titulo: titulo,
            descuento: descuento,
            dias: dias
        })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            alert("Promoción creada con éxito");
            //location.reload(); // o actualiza el select dinámicamente
            button_info_promos();
        } else {
            alert(data.message || "Error al crear promoción");
        }
    })
    .catch(err => {
        console.error("Error:", err);
        alert("Error de conexión al guardar la promoción.");
    });    
}


// [IDF-0193] Ruta que retorna todas las categorias al administrador.
function mostrar_categorias_disponibles(){
    const promoSelect = document.getElementById("category-select");

    // Solicitar promociones actuales
    fetch("/get_categorys")  // Tu endpoint debe devolver un JSON con una lista de promociones
        .then(res => res.json())
        .then(data => {
            // Limpiar y rellenar el select
            promoSelect.innerHTML = `<option value="">Selecciona una Categoría</option>`;
            data.forEach(category => {
                const option = document.createElement("option");
                option.value = category.id;
                option.textContent = `${category.category}`;
                promoSelect.appendChild(option);
            });
        })
        .catch(err => {
            console.error("Error cargando categorias:", err);
            alert("Error al obtener las categorias.");
        });    
}

// [IDF-0194] Ruta que asignar una categoria a un contenido, solo permitida por el administrador.
function designar_categoria(id){
    const promoSelect = document.getElementById("category-select");
    const selectedPromoId = promoSelect.value;
    if (!selectedPromoId) {
        alert("Selecciona una categoria para usar.");
        return;
    }

    if (!id) {
        alert("Primero guarda el contenido para poder asignarle una categoria.");
        return;
    }

    fetch("/asignar_category", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ id_contenido: id, id_categoria: selectedPromoId })
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            alert("Categoria asignada correctamente.");
            location.reload();
        } else {
            alert(data.message || "Error al asignar una categoria.");
        }
    })
    .catch(err => {
        console.error("Error:", err);
        alert("Error al comunicar con el servidor.");
    });    
}


// [IDF-0195] Ruta que crea una nueva categoría, solo permitida por el administrador.
async function enviar_nueva_categoria() {
    const titulo = document.getElementById("category-title").value.trim();
    const promoSelect = document.getElementById("category-select");
    const selectedPromoIdRaw = promoSelect.value;
    const selectedPromoId = selectedPromoIdRaw === '' ? null : selectedPromoIdRaw;

    if (!titulo) {
        alert("Completa correctamente el campo.");
        return;
    }

    try {
        const response = await fetch("/crear_category", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                titulo: titulo,
                id_padre: selectedPromoId
            })
        });

        const data = await response.json();

        if (data.success) {
            alert("Categoría creada con éxito");

            mostrar_categorias_disponibles();

        } else {
            alert(data.message || "Error al crear la categoría.");
        }

    } catch (err) {
        console.error("Error:", err);
        alert("Error de conexión al guardar la categoría.");
    }
}
