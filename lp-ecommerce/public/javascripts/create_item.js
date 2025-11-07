// ================================================================
// [IDF-0029] Renderiza información detallada de un contenido multimedia
// ================================================================
export function createMediaInfo({ data = null, Div = null }) {
  if (!data || !Div) return;

  const infoDiv = document.createElement("div");
  infoDiv.className = "info mt-3";

  const fields = [
    { label: "Autor", value: data.author },
    { label: "Precio", value: `$${parseFloat(data.price || 0).toFixed(2)}` },
    { label: "Extensión de archivo", value: data.extension },
    { label: "Categoría", value: data.category },
    { label: "Nota promedio", value: data.rating },
    { label: "Descargas", value: data.downloaded },
  ];

  fields.forEach(f => {
    const p = document.createElement("p");
    p.innerHTML = `<strong>${f.label}:</strong> ${f.value || "—"}`;
    infoDiv.appendChild(p);
  });

  // Descripción (más larga, separada visualmente)
  if (data.description) {
    const desc = document.createElement("p");
    desc.className = "mt-2";
    desc.innerHTML = `<strong>Descripción:</strong> ${data.description}`;
    infoDiv.appendChild(desc);
  }

  Div.appendChild(infoDiv);
}


// ================================================================
// [IDF-0030] Renderiza el tipo de media y enlaza según el rol y permisos
// ================================================================
export function createContentType({ data = null, current_role = null, linked = true }) {
  if (!data) return;

  const container = document.querySelector(".container");
  if (!container) {
    console.error("No se encontró el contenedor '.container'.");
    return;
  }

  const Div = document.createElement("div");
  Div.className = `media-item${linked ? " linked" : ""} bg-dark text-light p-3 rounded shadow-sm mb-4 border border-secondary`;

  // === Título ===
  const title = document.createElement("h2");
  title.className = "fw-bold text-info mb-3";
  title.textContent = data.title || "Contenido sin título";
  Div.appendChild(title);

  // === Tipo de contenido ===
  const mediaWrapper = document.createElement("div");
  mediaWrapper.className = "media-wrapper text-center";

  const src = data.src || "";
  let mediaElement;

  switch (data.type) {
    case "imagen":
      mediaElement = document.createElement("img");
      mediaElement.src = src;
      mediaElement.alt = data.title || "Imagen";
      mediaElement.className = "media img-fluid rounded";
      break;

    case "audio":
      mediaElement = document.createElement("audio");
      mediaElement.src = src;
      mediaElement.controls = true;
      mediaElement.className = "media w-100";
      break;

    case "video":
    default:
      mediaElement = document.createElement("video");
      mediaElement.src = src;
      mediaElement.controls = true;
      mediaElement.className = "media w-100 rounded";
      mediaElement.innerText = "Tu navegador no soporta el elemento de video.";
      break;
  }

  mediaWrapper.appendChild(mediaElement);
  Div.appendChild(mediaWrapper);

  // === Información ===
  createMediaInfo({ data, Div });

  // === Enlace o acción de click ===
  if (linked) {
    Div.style.cursor = "pointer";
    Div.addEventListener("click", () => {
      // [IDF-0005] Verifica si el usuario puede descargar cierto contenido
      fetch("/verificate_downloaded_content", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: data.id })
      })
        .then(res => res.json())
        .then(respuesta => {
          if (respuesta.success) {
            // Puede acceder al contenido
            window.location.href = `item_view.html?id=${data.id}`;
          } else {
            // Redirigir según el rol actual
            if (current_role === "Cliente") {
              window.location.href = `item_shop.html?id=${data.id}`;
            } else {
              window.location.href = `register.html`;
            }
          }
        })
        .catch(err => {
          console.error("Error verificando contenido:", err);
          window.location.href = `register.html`;
        });
    });
  }

  container.appendChild(Div);
}
