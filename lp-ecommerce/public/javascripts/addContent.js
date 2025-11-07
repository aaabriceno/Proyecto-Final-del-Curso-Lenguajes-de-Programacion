// ===============================================================
// [IDF-0009] Envío de datos binarios y JSON de un contenido al servidor
// ===============================================================
document.addEventListener("DOMContentLoaded", () => {

  const form = document.getElementById("content-form");
  const fileInput = document.getElementById("fileInput");
  const fileNameSpan = document.getElementById("file-name");
  const previewContainer = document.getElementById("preview-container");
  const promoSelect = document.getElementById("category-select");

  // === Subir contenido ===
  form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const contentType = document.getElementById("content-type").value.trim();
    const file = fileInput.files[0];
    const title = document.getElementById("content-title").value.trim();
    const author = document.getElementById("content-author").value.trim();
    const price = document.getElementById("content-price").value.trim();
    const category = promoSelect.value;
    const description = document.getElementById("content-description").value.trim();

    // Validación de campos obligatorios
    if (!file || !title || !author || !price || !category || !description) {
      alert("Por favor, completa todos los campos sin dejar espacios vacíos.");
      return;
    }

    // Validar que el precio sea un número positivo
    if (isNaN(price) || Number(price) < 0) {
      alert("Por favor, ingresa un precio válido (número positivo).");
      return;
    }

    // Enviar al servidor (binario + metadatos)
    const formData = new FormData(form);

    try {
      const res = await fetch("/save_content", {
        method: "POST",
        body: formData
      });

      const data = await res.json();

      if (data.success) {
        alert("✅ Contenido guardado exitosamente");
        window.location.href = "admi_view.html";
      } else {
        alert(`⚠️ ${data.message || "Error al guardar el contenido"}`);
      }

    } catch (err) {
      console.error("Error al enviar contenido:", err);
      alert("❌ Error de conexión al enviar el contenido.");
    }
  });

  // === Previsualización de archivo seleccionado ===
  fileInput.addEventListener("change", () => {
    const file = fileInput.files[0];
    previewContainer.innerHTML = ""; // Limpiar previas
    const contentTypeField = document.getElementById("content-type");

    if (file) {
      fileNameSpan.textContent = file.name;
      document.getElementById("content-title").value = file.name.trim();
      const url = URL.createObjectURL(file);
      const type = file.type;

      let previewElement;

      if (type.startsWith("image/")) {
        contentTypeField.value = "imagen";
        previewElement = document.createElement("img");
        previewElement.src = url;
        previewElement.alt = "Vista previa de imagen";
        previewElement.style.maxWidth = "300px";
        previewElement.style.maxHeight = "300px";
      } else if (type.startsWith("video/")) {
        contentTypeField.value = "video";
        previewElement = document.createElement("video");
        previewElement.src = url;
        previewElement.controls = true;
        previewElement.style.maxWidth = "300px";
      } else if (type.startsWith("audio/")) {
        contentTypeField.value = "audio";
        previewElement = document.createElement("audio");
        previewElement.src = url;
        previewElement.controls = true;
      } else {
        previewElement = document.createElement("p");
        previewElement.textContent = "Tipo de archivo no compatible para vista previa.";
      }

      previewContainer.appendChild(previewElement);

    } else {
      fileNameSpan.textContent = "Ningún archivo seleccionado";
    }
  });

  // === Mostrar categorías existentes ===
  mostrarCategoriasDisponibles();

  // === Mostrar formulario para nueva categoría ===
  document.getElementById("btn-agregar-category").addEventListener("click", () => {
    const formCat = document.getElementById("form-agregar-category");
    formCat.style.display = formCat.style.display === "none" ? "block" : "none";
  });

  // === Guardar nueva categoría ===
  document.getElementById("btn-guardar-category").addEventListener("click", enviarNuevaCategoria);
});


// ===============================================================
// [IDF-0193] Cargar categorías disponibles
// ===============================================================
function mostrarCategoriasDisponibles() {
  const select = document.getElementById("category-select");

  fetch("/get_categorys")
    .then(res => res.json())
    .then(data => {
      select.innerHTML = `<option value="">Selecciona una Categoría</option>`;
      data.forEach(cat => {
        const option = document.createElement("option");
        option.value = cat.id;
        option.textContent = cat.category;
        select.appendChild(option);
      });
    })
    .catch(err => {
      console.error("Error cargando categorías:", err);
      alert("Error al obtener las categorías del servidor.");
    });
}


// ===============================================================
// [IDF-0195] Crear nueva categoría (solo administrador)
// ===============================================================
async function enviarNuevaCategoria() {
  const titulo = document.getElementById("category-title").value.trim();
  const promoSelect = document.getElementById("category-select");
  const idPadre = promoSelect.value || null;

  if (!titulo) {
    alert("Completa correctamente el campo de título.");
    return;
  }

  try {
    const res = await fetch("/crear_category", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ titulo, id_padre: idPadre })
    });

    const data = await res.json();

    if (data.success) {
      alert("✅ Categoría creada con éxito");
      mostrarCategoriasDisponibles();
      document.getElementById("category-title").value = "";
    } else {
      alert(`⚠️ ${data.message || "Error al crear la categoría."}`);
    }

  } catch (err) {
    console.error("Error al crear categoría:", err);
    alert("❌ Error de conexión con el servidor al guardar la categoría.");
  }
}
