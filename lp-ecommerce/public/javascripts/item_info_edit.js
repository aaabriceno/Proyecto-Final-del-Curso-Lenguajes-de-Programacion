// ============================================================
// [IDF-0010] Editar y actualizar un contenido multimedia
// ============================================================
document.addEventListener("DOMContentLoaded", () => {
  const fileInput = document.getElementById("fileInput");
  const fileNameSpan = document.getElementById("file-name");
  const previewContainer = document.getElementById("preview-container");
  const params = new URLSearchParams(window.location.search);
  const id = params.get("id");

  // ------------------------------------------------------------
  // Cargar datos del contenido si hay un ID
  // ------------------------------------------------------------
  if (id) loadContentData(id);

  // ------------------------------------------------------------
  // Vista previa de archivo nuevo
  // ------------------------------------------------------------
  fileInput.addEventListener("change", handleFilePreview);

  // ------------------------------------------------------------
  // Enviar datos del formulario
  // ------------------------------------------------------------
  document.getElementById("content-form").addEventListener("submit", e => {
    e.preventDefault();
    enviarFormulario(id);
  });

  // ------------------------------------------------------------
  // Configurar botones de categorías y promociones
  // ------------------------------------------------------------
  setupPromoControls(id);
  setupCategoryControls(id);
  del_button(id);
});

// ============================================================
// [IDF-0010-A] Cargar información del contenido por ID
// ============================================================
function loadContentData(id) {
  fetch("/get_content_by_id", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id })
  })
    .then(res => res.json())
    .then(data => {
      if (!data) {
        previewContainer.innerHTML = "<p>No se encontró el item.</p>";
        return;
      }

      document.getElementById("content-type").value = data.type;
      document.getElementById("content-title").value = data.title;
      document.getElementById("content-author").value = data.author;

      const priceStr = String(data.price);
      document.getElementById("content-price-view").innerHTML = `Precio actual: ${priceStr}`;

      const match = priceStr.match(/<s>(.*?)<\/s>/);
      const cleanPrice = match && match[1] ? match[1] : priceStr.replace(/<[^>]*>/g, "");
      document.getElementById("content-price").value = cleanPrice;

      document.getElementById("content-category").value = data.category;
      document.getElementById("content-description").value = data.description;
      document.getElementById("send-eliminar").textContent = data.estado === "desactivado" ? "Restaurar" : "Eliminar";

      fileNameSpan.textContent = `Archivo cargado: ${data.title}`;
      renderPreview(data);
    })
    .catch(err => {
      console.error("Error obteniendo el item:", err);
      previewContainer.innerHTML = "<p>Error cargando el contenido.</p>";
    });
}

// ============================================================
// [IDF-0010-B] Renderiza vista previa del contenido
// ============================================================
function renderPreview(data) {
  previewContainer.innerHTML = "";
  const { type, src } = data;
  const media = document.createElement(type === "imagen" ? "img" : type);

  media.src = src;
  media.controls = type !== "imagen";
  media.style.maxWidth = "300px";
  media.style.maxHeight = "300px";
  previewContainer.appendChild(media);
}

// ============================================================
// [IDF-0010-C] Vista previa al seleccionar archivo nuevo
// ============================================================
function handleFilePreview() {
  const file = this.files[0];
  previewContainer.innerHTML = "";

  if (!file) {
    fileNameSpan.textContent = "Ningún archivo seleccionado";
    return;
  }

  const fileType = file.type;
  const url = URL.createObjectURL(file);
  fileNameSpan.textContent = file.name;
  document.getElementById("content-title").value = file.name.trim();

  const contentTypeInput = document.getElementById("content-type");
  const createMedia = (tag, controls = false) => {
    const el = document.createElement(tag);
    el.src = url;
    el.controls = controls;
    el.style.maxWidth = "300px";
    el.style.maxHeight = "300px";
    previewContainer.appendChild(el);
  };

  if (fileType.startsWith("image/")) {
    contentTypeInput.value = "imagen";
    createMedia("img");
  } else if (fileType.startsWith("video/")) {
    contentTypeInput.value = "video";
    createMedia("video", true);
  } else if (fileType.startsWith("audio/")) {
    contentTypeInput.value = "audio";
    createMedia("audio", true);
  } else {
    previewContainer.textContent = "Tipo de archivo no compatible para vista previa.";
  }
}

// ============================================================
// [IDF-0010-D] Enviar datos editados al servidor
// ============================================================
function enviarFormulario(id) {
  const fields = ["content-type", "content-title", "content-author", "content-price", "content-description"];
  const values = Object.fromEntries(fields.map(f => [f, document.getElementById(f).value.trim()]));

  if (!id || Object.values(values).some(v => !v)) {
    alert("Por favor, completa todos los campos sin dejar espacios en blanco.");
    return;
  }

  if (isNaN(values["content-price"]) || Number(values["content-price"]) < 0) {
    alert("Por favor, ingresa un precio válido (número positivo).");
    return;
  }

  const formData = new FormData(document.getElementById("content-form"));
  formData.append("id", id);

  fetch("/update_content", { method: "POST", body: formData })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        alert("Contenido guardado correctamente.");
        window.location.href = "admi_view.html";
      } else {
        alert(data.message || "Error al guardar el contenido.");
      }
    })
    .catch(err => console.error("Error:", err));
}

// ============================================================
// [IDF-0150] Eliminar / restaurar contenido
// ============================================================
function del_button(id) {
  const btn = document.getElementById("send-eliminar");
  if (!btn) return;

  btn.addEventListener("click", () => {
    if (!id) {
      alert("No hay un contenido cargado para eliminar/restaurar.");
      return;
    }

    fetch("/update_content_state", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id })
    })
      .then(res => res.json())
      .then(data => {
        if (data.success) {
          btn.textContent = data.estado ? "Restaurar" : "Eliminar";
          alert(data.message || (data.estado ? "Contenido eliminado" : "Contenido restaurado"));
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

// ============================================================
// [Promociones]
// ============================================================
function setupPromoControls(id) {
  const promoBtn = document.getElementById("send-promocion");
  const promoContainer = document.getElementById("promocion-container");

  promoBtn.addEventListener("click", () => {
    promoContainer.style.display = promoContainer.style.display === "none" ? "block" : "none";
    button_info_promos();
  });

  document.getElementById("btn-agregar-promo").addEventListener("click", () => {
    const form = document.getElementById("form-agregar-promo");
    form.style.display = form.style.display === "none" ? "block" : "none";
  });

  document.getElementById("btn-guardar-promo").addEventListener("click", enviar_nueva_promocion);
  document.getElementById("btn-usar-promo").addEventListener("click", () => designar_promocion(id));
}

function button_info_promos() {
  const promoSelect = document.getElementById("promo-select");

  fetch("/get_promociones")
    .then(res => res.json())
    .then(data => {
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

function enviar_nueva_promocion() {
  const titulo = document.getElementById("promo-title").value.trim();
  const descuento = parseFloat(document.getElementById("promo-descuento").value);
  const dias = parseInt(document.getElementById("promo-dias").value);

  if (!titulo || isNaN(descuento) || isNaN(dias) || descuento < 0 || descuento > 100 || dias <= 0) {
    alert("Completa correctamente todos los campos de promoción.");
    return;
  }

  fetch("/crear_promocion", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ titulo, descuento, dias })
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        alert("Promoción creada con éxito");
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

function designar_promocion(id) {
  const promoSelect = document.getElementById("promo-select");
  const selectedPromoId = promoSelect.value;
  if (!selectedPromoId) return alert("Selecciona una promoción para usar.");
  if (!id) return alert("Guarda el contenido antes de asignar una promoción.");

  fetch("/asignar_promocion", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id_contenido: id, id_promocion: selectedPromoId })
  })
    .then(res => res.json())
    .then(data => alert(data.success ? "Promoción asignada correctamente." : data.message || "Error al asignar promoción."))
    .catch(err => console.error("Error:", err))
    .finally(() => location.reload());
}

// ============================================================
// [Categorías]
// ============================================================
function setupCategoryControls(id) {
  const catBtn = document.getElementById("send-category");
  const catContainer = document.getElementById("category-container");

  catBtn.addEventListener("click", () => {
    catContainer.style.display = catContainer.style.display === "none" ? "block" : "none";
    mostrar_categorias_disponibles();
  });

  document.getElementById("btn-agregar-category").addEventListener("click", () => {
    const form = document.getElementById("form-agregar-category");
    form.style.display = form.style.display === "none" ? "block" : "none";
  });

  document.getElementById("btn-guardar-category").addEventListener("click", enviar_nueva_categoria);
  document.getElementById("btn-usar-category").addEventListener("click", () => designar_categoria(id));
}

function mostrar_categorias_disponibles() {
  const categorySelect = document.getElementById("category-select");

  fetch("/get_categorys")
    .then(res => res.json())
    .then(data => {
      categorySelect.innerHTML = `<option value="">Selecciona una Categoría</option>`;
      data.forEach(cat => {
        const option = document.createElement("option");
        option.value = cat.id;
        option.textContent = cat.category;
        categorySelect.appendChild(option);
      });
    })
    .catch(err => {
      console.error("Error cargando categorias:", err);
      alert("Error al obtener las categorias.");
    });
}

async function enviar_nueva_categoria() {
  const titulo = document.getElementById("category-title").value.trim();
  const categorySelect = document.getElementById("category-select");
  const id_padre = categorySelect.value || null;

  if (!titulo) return alert("Completa correctamente el campo.");

  try {
    const res = await fetch("/crear_category", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ titulo, id_padre })
    });

    const data = await res.json();
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

function designar_categoria(id) {
  const select = document.getElementById("category-select");
  const selectedId = select.value;
  if (!selectedId) return alert("Selecciona una categoría para usar.");
  if (!id) return alert("Guarda el contenido antes de asignar la categoría.");

  fetch("/asignar_category", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id_contenido: id, id_categoria: selectedId })
  })
    .then(res => res.json())
    .then(data => {
      if (data.success) {
        alert("Categoría asignada correctamente.");
        location.reload();
      } else {
        alert(data.message || "Error al asignar la categoría.");
      }
    })
    .catch(err => {
      console.error("Error:", err);
      alert("Error al comunicar con el servidor.");
    });
}
