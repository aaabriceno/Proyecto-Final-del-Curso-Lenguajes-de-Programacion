
// [IDF-0014] envia una cadena de texto para buscarla en los contenidos existentes.
export function realizarBusqueda(textoBusqueda, resultsContainer,filterContainer) {
    const query = textoBusqueda.trim().toLowerCase();
    const selectedFilters = Array.from(filterContainer.querySelectorAll('.filter-checkbox'))
        .filter(checkbox => checkbox.checked)
        .map(checkbox => checkbox.dataset.filter);

    fetch('/search_content', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: query, filters: selectedFilters })
    })
    .then(response => response.json())
    .then(data => {
        if (data.data.length > 0) {
            var html = '<div class="result-cards">';
            data.data.forEach(item => {
                const itemUrl =data.auth === true
                    ? `item_view_admi.html?id=${item.id}`
                    : `item_view.html?id=${item.id}`;

                html += `
                <div class="result-card">
                    <a href="${itemUrl}"><h4>${item.title}</a> (${item.type})</h4>
                    <p><strong>Autor:</strong> ${item.author} - 
                    <strong>Category:</strong> ${item.category}</p>
                </div>`;
            });
            html += '</div>';
            resultsContainer.innerHTML = html;
        } else {
            resultsContainer.innerHTML = "<p>No se encontraron resultados.</p>";
        }
    })
    .catch(error => {
        console.error('Error buscando:', error);
        resultsContainer.innerHTML = "<p>Error al buscar.</p>";
    });
}

// [IDF-0036] Renderiza la barra de busqueda para los clientes y administrador, una busqueda de contenidos.
export function gen_searchBar(){
    var resultsContainer =  document.getElementById("search-results");
    // var resultsContainer = document.createElement('div');
    // resultsContainer.setAttribute('id', 'search-results');
    // document.body.insertBefore(resultsContainer, header.nextSibling);

    var searchLi = document.createElement('li');
    searchLi.classList.add('search-layout');

    var searchContainer = document.createElement('div');
    searchContainer.classList.add('search-container');

    var searchForm = document.createElement('form');
    searchForm.setAttribute('action', '#'); 
    searchForm.setAttribute('method', 'get');
    searchForm.classList.add('search-form');

    var searchInput = document.createElement('input');
    searchInput.setAttribute('type', 'text');
    searchInput.setAttribute('name', 'search');
    searchInput.setAttribute('placeholder', 'Escribe lo que buscas...');
    searchForm.appendChild(searchInput);

    var searchButton = document.createElement('button');
    searchButton.setAttribute('type', 'submit');
    searchButton.textContent = 'Buscar';
    searchForm.appendChild(searchButton);

    var clearButton = document.createElement('button');
    clearButton.setAttribute('type', 'button');
    clearButton.textContent = '✖';
    clearButton.classList.add('clear-search-btn');
    searchForm.appendChild(clearButton);
    clearButton.addEventListener('click', function () {
        searchInput.value = '';
        resultsContainer.innerHTML = '';
        filterContainer.querySelectorAll('.filter-checkbox').forEach(cb => cb.checked = false);
    });

    var filterContainer = document.createElement('div');
    filterContainer.classList.add('filter-checkboxes');

    var filtros = ['video', 'audio', 'imagen','autor', 'categorias'];
    filtros.forEach(tipo => {
        var label = document.createElement('label');
        label.classList.add('filter-label');

        var checkbox = document.createElement('input');
        checkbox.setAttribute('type', 'checkbox');
        checkbox.classList.add('filter-checkbox');
        checkbox.dataset.filter = tipo;

        label.appendChild(checkbox);
        label.appendChild(document.createTextNode(tipo.charAt(0).toUpperCase() + tipo.slice(1)));
        filterContainer.appendChild(label);
    });

    searchContainer.appendChild(searchForm);
    searchContainer.appendChild(filterContainer);
    searchLi.appendChild(searchContainer);

    searchForm.addEventListener('submit', function(event) {
        event.preventDefault();
        const texto = searchInput.value;

        if (texto.trim() === '') {
            resultsContainer.innerHTML = "<p>Ingrese búsqueda.</p>";
            return;
        }
        
        realizarBusqueda(texto, resultsContainer, filterContainer);
    });

    return searchLi;
}