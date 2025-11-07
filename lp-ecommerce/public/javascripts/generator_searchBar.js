// ================================================================
// [IDF-0014] Envía una cadena de texto y filtros al servidor
// ================================================================
export async function realizarBusqueda(textoBusqueda, resultsContainer, filterContainer) {
  const query = textoBusqueda.trim().toLowerCase();
  const selectedFilters = Array.from(filterContainer.querySelectorAll(".filter-checkbox"))
    .filter(cb => cb.checked)
    .map(cb => cb.dataset.filter);

  if (!query) {
    resultsContainer.innerHTML = "<p class='text-warning'>Ingrese una búsqueda.</p>";
    return;
  }

  try {
    const res = await fetch("/search_content", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ query, filters: selectedFilters })
    });

    if (!res.ok) throw new Error("Error HTTP: " + res.status);
    const data = await res.json();

    // Renderizado de resultados
    if (data.data?.length > 0) {
      let html = '<div class="result-cards">';
      data.data.forEach(item => {
        const itemUrl = data.auth
          ? `item_view_admi.html?id=${item.id}`
          : `item_view.html?id=${item.id}`;

        html += `
          <div class="result-card border border-secondary rounded p-3 mb-2 bg-dark text-light shadow-sm">
            <a href="${itemUrl}" class="text-info text-decoration-none">
              <h5 class="mb-1">${item.title}</h5>
            </a>
            <p class="small mb-0 text-muted">
              <strong>Tipo:</strong> ${item.type} &nbsp;|&nbsp;
              <strong>Autor:</strong> ${item.author} &nbsp;|&nbsp;
              <strong>Categoría:</strong> ${item.category}
            </p>
          </div>
        `;
      });
      html += "</div>";
      resultsContainer.innerHTML = html;
    } else {
      resultsContainer.innerHTML = "<p class='text-muted'>No se encontraron resultados.</p>";
    }

  } catch (error) {
    console.error("Error buscando:", error);
    resultsContainer.innerHTML = "<p class='text-danger'>Error al realizar la búsqueda.</p>";
  }
}


// ================================================================
// [IDF-0036] Genera la barra de búsqueda dinámica (clientes / admin)
// ================================================================
export function gen_searchBar() {
  const resultsContainer = document.getElementById("search-results");
  const searchLi = document.createElement("li");
  searchLi.classList.add("search-layout");

  const searchContainer = document.createElement("div");
  searchContainer.classList.add("search-container");

  // === Formulario ===
  const searchForm = document.createElement("form");
  searchForm.classList.add("search-form", "d-flex", "align-items-center", "gap-2");
  searchForm.setAttribute("action", "#");
  searchForm.setAttribute("method", "get");

  // Input de texto
  const searchInput = document.createElement("input");
  searchInput.type = "text";
  searchInput.name = "search";
  searchInput.placeholder = "🔍 Escribe lo que buscas...";
  searchInput.classList.add("form-control", "bg-dark", "text-light");
  searchForm.appendChild(searchInput);

  // Botón buscar
  const searchButton = document.createElement("button");
  searchButton.type = "submit";
  searchButton.textContent = "Buscar";
  searchButton.classList.add("btn", "btn-primary");
  searchForm.appendChild(searchButton);

  // Botón limpiar
  const clearButton = document.createElement("button");
  clearButton.type = "button";
  clearButton.textContent = "✖";
  clearButton.classList.add("btn", "btn-outline-danger", "clear-search-btn");
  searchForm.appendChild(clearButton);

  // === Filtros ===
  const filterContainer = document.createElement("div");
  filterContainer.classList.add("filter-checkboxes", "mt-3");

  const filtros = ["video", "audio", "imagen", "autor", "categorias"];
  filtros.forEach(tipo => {
    const label = document.createElement("label");
    label.classList.add("filter-label", "me-3", "text-light");

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.classList.add("filter-checkbox", "form-check-input", "me-1");
    checkbox.dataset.filter = tipo;

    label.appendChild(checkbox);
    label.appendChild(document.createTextNode(tipo.charAt(0).toUpperCase() + tipo.slice(1)));
    filterContainer.appendChild(label);
  });

  // === Acciones ===
  clearButton.addEventListener("click", () => {
    searchInput.value = "";
    resultsContainer.innerHTML = "";
    filterContainer.querySelectorAll(".filter-checkbox").forEach(cb => (cb.checked = false));
  });

  searchForm.addEventListener("submit", e => {
    e.preventDefault();
    realizarBusqueda(searchInput.value, resultsContainer, filterContainer);
  });

  // === Ensamblar elementos ===
  searchContainer.appendChild(searchForm);
  searchContainer.appendChild(filterContainer);
  searchLi.appendChild(searchContainer);

  return searchLi;
}
