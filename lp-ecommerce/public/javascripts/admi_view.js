// ============================================================
// [IDF-0015] Búsqueda de contenidos y usuarios en el sistema
// [IDF-0197] Ranking de usuarios con más descargas
// ============================================================

document.addEventListener("DOMContentLoaded", () => {

  const formBusqueda = document.getElementById("form-busqueda");
  const searchInput = document.getElementById("search-input");
  const checkboxes = document.querySelectorAll(".filtro-tipo");
  const searchResults = document.getElementById("search-results-admi");
  const actionButton = document.getElementById("option-action");
  const selectOption = document.getElementById("content-filter");

  // ============================================================
  // [IDF-0015] Enviar búsqueda de texto
  // ============================================================
  formBusqueda?.addEventListener("submit", async (e) => {
    e.preventDefault();

    const query = searchInput?.value.trim();
    if (!query) {
      searchResults.innerHTML = "<p class='text-warning'>Ingrese un término de búsqueda.</p>";
      return;
    }

    const filters = Array.from(checkboxes)
      .filter(chk => chk.checked)
      .map(chk => chk.value);

    try {
      const res = await fetch("/search_info", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query, filters })
      });

      if (!res.ok) throw new Error("Error HTTP " + res.status);

      const data = await res.json();
      renderizarResultados(data);

    } catch (err) {
      console.error("Error en búsqueda:", err);
      searchResults.innerHTML = "<p class='text-danger'>Error al realizar la búsqueda.</p>";
    }
  });

  // ============================================================
  // [IDF-0197] Obtener ranking de usuarios con más descargas
  // ============================================================
  actionButton?.addEventListener("click", async () => {
    const selected = selectOption.value;

    if (selected === "ranking") {
      try {
        const res = await fetch("/get_downloads_ranking");
        if (!res.ok) throw new Error("Error HTTP " + res.status);

        const data = await res.json();
        renderizarResultadosRanking(data);

      } catch (err) {
        console.error("Error obteniendo ranking:", err);
        searchResults.innerHTML = "<p class='text-danger'>Error obteniendo ranking de usuarios.</p>";
      }
    } else {
      searchResults.innerHTML = "<p class='text-muted'>Seleccione una acción válida.</p>";
    }
  });

  // ============================================================
  // [IDF-0015] Renderizar resultados de búsqueda
  // ============================================================
  function renderizarResultados(data) {
    searchResults.innerHTML = "";

    if (!data || data.length === 0) {
      searchResults.innerHTML = "<p class='text-muted'>No se encontraron resultados.</p>";
      return;
    }

    const header = document.createElement("div");
    header.className = "result-header fw-bold border-bottom mb-2 pb-1";
    header.innerHTML = `
      <span>ID</span>
      <span>Nombre/Título</span>
      <span>Autor/Email</span>
      <span>Tipo/Estado</span>
    `;
    searchResults.appendChild(header);

    data.forEach(item => {
      const row = document.createElement("a");
      row.className = "result-row d-flex justify-content-between align-items-center border-bottom py-2 text-decoration-none text-light";

      const isUser = ["cliente", "ex-cliente", "administrador"].includes(item.type);
      row.href = isUser ? `user_info.html?id=${item.id}` : `item_info_edit.html?id=${item.id}`;

      row.innerHTML = `
        <span>${item.id}</span>
        <span>${item.title || "—"}</span>
        <span>${item.author || "—"}</span>
        <span>${item.type}</span>
      `;

      row.addEventListener("mouseenter", () => row.classList.add("bg-secondary"));
      row.addEventListener("mouseleave", () => row.classList.remove("bg-secondary"));

      searchResults.appendChild(row);
    });
  }

  // ============================================================
  // [IDF-0197] Renderizar ranking de usuarios con más descargas
  // ============================================================
  function renderizarResultadosRanking(data) {
    searchResults.innerHTML = "";

    if (!data || data.length === 0) {
      searchResults.innerHTML = "<p class='text-muted'>No hay usuarios con descargas registradas.</p>";
      return;
    }

    const header = document.createElement("div");
    header.className = "result-header fw-bold border-bottom mb-2 pb-1";
    header.innerHTML = `
      <span>ID</span>
      <span>Usuario</span>
      <span>Estado</span>
      <span>Total Descargas</span>
    `;
    searchResults.appendChild(header);

    data.forEach((user, index) => {
      const row = document.createElement("a");
      row.href = `user_info.html?id=${user.id}`;
      row.className = "result-row d-flex justify-content-between align-items-center border-bottom py-2 text-decoration-none text-light";

      // Clase adicional para top 3
      if (index === 0) row.classList.add("bg-success-subtle");
      else if (index === 1) row.classList.add("bg-warning-subtle");
      else if (index === 2) row.classList.add("bg-info-subtle");

      row.innerHTML = `
        <span>${user.id}</span>
        <span>${user.username}</span>
        <span>${user.estado_cuenta}</span>
        <span>${user.total_descargas}</span>
      `;

      row.addEventListener("mouseenter", () => row.classList.add("bg-secondary"));
      row.addEventListener("mouseleave", () => row.classList.remove("bg-secondary"));

      searchResults.appendChild(row);
    });
  }

});
