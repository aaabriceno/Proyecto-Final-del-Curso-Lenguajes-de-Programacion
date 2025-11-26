// ============================================================
// Crear producto - Selector en cascada de categorías
// ============================================================

let allCategories = [];

document.addEventListener("DOMContentLoaded", () => {
  // Cargar todas las categorías al inicio
  loadAllCategories().then(() => {
    setupProductTypeSelector();
    setupCascadeSelectors();
  });

  // Manejar envío del formulario
  document.getElementById("content-form").addEventListener("submit", (e) => {
    e.preventDefault();
    saveProduct();
  });
});

// ============================================================
// Cargar todas las categorías (sin filtrar)
// ============================================================
async function loadAllCategories() {
  try {
    const response = await fetch("/api/categories");
    const data = await response.json();
    allCategories = data.categories;
    console.log(`📦 Cargadas ${allCategories.length} categorías totales`);
  } catch (error) {
    console.error("Error cargando categorías:", error);
    alert("Error al cargar las categorías");
  }
}

// ============================================================
// Configurar selector de tipo de producto
// ============================================================
function setupProductTypeSelector() {
  const productTypeSelect = document.getElementById("productType");
  toggleStockField(productTypeSelect.value);
  
  productTypeSelect.addEventListener("change", (e) => {
    const selectedType = e.target.value;
    toggleStockField(selectedType);
    console.log(`🔄 Tipo de producto cambiado a: ${selectedType}`);
    
    if (selectedType === "digital" || selectedType === "hardware") {
      loadCategoriesByType(selectedType);
    } else {
      clearAllSelectors();
    }
  });
}

// ============================================================
// Cargar categorías filtradas por tipo de producto
// ============================================================
function loadCategoriesByType(productType) {
  console.log(`🔍 Filtrando categorías por tipo: ${productType}`);
  
  // Filtrar categorías por productType
  const filteredCategories = allCategories.filter(cat => cat.productType === productType);
  console.log(`✅ Encontradas ${filteredCategories.length} categorías de tipo ${productType}`);
  
  // Limpiar todos los selectores primero
  clearAllSelectors();
  
  // Llenar nivel 1 solo con categorías raíz del tipo seleccionado
  const level1Select = document.getElementById("level1-select");
  const level1Categories = filteredCategories.filter(cat => cat.level === 0);
  
  level1Categories.forEach(cat => {
    const option = document.createElement("option");
    option.value = cat.id;
    option.textContent = cat.name;
    level1Select.appendChild(option);
  });
  
  console.log(`📁 Cargadas ${level1Categories.length} categorías raíz en nivel 1`);
}

// ============================================================
// Limpiar todos los selectores de categoría
// ============================================================
function clearAllSelectors() {
  const level1Select = document.getElementById("level1-select");
  level1Select.innerHTML = '<option value="">-- Selecciona nivel 1 --</option>';
  
  const level2Select = document.getElementById("level2-select");
  level2Select.innerHTML = '<option value="">-- Selecciona nivel 2 --</option>';
  hideLevel("level2-container");
  
  const level3Select = document.getElementById("level3-select");
  level3Select.innerHTML = '<option value="">-- Selecciona nivel 3 --</option>';
  hideLevel("level3-container");
  
  const level4Select = document.getElementById("level4-select");
  level4Select.innerHTML = '<option value="">-- Selecciona categoría final --</option>';
  hideLevel("level4-container");
  
  document.getElementById("final-category-id").value = "";
}

// ============================================================
// Mostrar/Ocultar campo de stock segun el tipo
// ============================================================
function toggleStockField(productType) {
  const stockWrapper = document.getElementById("stock-field");
  const stockInput = document.getElementById("content-stock");
  if (!stockWrapper || !stockInput) return;
  
  const isHardware = productType === "hardware";
  stockWrapper.style.display = isHardware ? "block" : "none";
  
  if (isHardware) {
    stockInput.removeAttribute("disabled");
    stockInput.setAttribute("required", "required");
    if (!stockInput.value || Number(stockInput.value) < 0) {
      stockInput.value = 0;
    }
  } else {
    stockInput.value = 0;
    stockInput.setAttribute("disabled", "disabled");
    stockInput.removeAttribute("required");
  }
}

// ============================================================
// Configurar selectores en cascada
// ============================================================
function setupCascadeSelectors() {
  // Nivel 1 → Nivel 2
  document.getElementById("level1-select").addEventListener("change", (e) => {
    const parentId = parseInt(e.target.value);
    if (parentId) {
      fillLevelSelect("level2-select", parentId, "level2-container");
      hideLevel("level3-container");
      hideLevel("level4-container");
      document.getElementById("final-category-id").value = "";
    } else {
      hideLevel("level2-container");
      hideLevel("level3-container");
      hideLevel("level4-container");
      document.getElementById("final-category-id").value = "";
    }
  });
  
  // Nivel 2 → Nivel 3
  document.getElementById("level2-select").addEventListener("change", (e) => {
    const parentId = parseInt(e.target.value);
    if (parentId) {
      const hasChildren = fillLevelSelect("level3-select", parentId, "level3-container");
      if (!hasChildren) {
        document.getElementById("final-category-id").value = parentId;
        hideLevel("level3-container");
        hideLevel("level4-container");
      } else {
        hideLevel("level4-container");
        document.getElementById("final-category-id").value = "";
      }
    } else {
      hideLevel("level3-container");
      hideLevel("level4-container");
      document.getElementById("final-category-id").value = "";
    }
  });
  
  // Nivel 3 → Nivel 4
  document.getElementById("level3-select").addEventListener("change", (e) => {
    const parentId = parseInt(e.target.value);
    if (parentId) {
      const hasChildren = fillLevelSelect("level4-select", parentId, "level4-container");
      if (!hasChildren) {
        document.getElementById("final-category-id").value = parentId;
        hideLevel("level4-container");
      } else {
        document.getElementById("final-category-id").value = "";
      }
    } else {
      hideLevel("level4-container");
      document.getElementById("final-category-id").value = "";
    }
  });
  
  // Nivel 4 (final)
  document.getElementById("level4-select").addEventListener("change", (e) => {
    const categoryId = e.target.value;
    document.getElementById("final-category-id").value = categoryId;
  });
}

// ============================================================
// Llenar selector de nivel con hijos del padre
// ============================================================
function fillLevelSelect(selectId, parentId, containerId) {
  const select = document.getElementById(selectId);
  const container = document.getElementById(containerId);
  
  select.innerHTML = '<option value="">-- Selecciona --</option>';
  
  const children = allCategories.filter(cat => cat.parentId === parentId);
  
  if (children.length > 0) {
    children.forEach(cat => {
      const option = document.createElement("option");
      option.value = cat.id;
      option.textContent = cat.name;
      select.appendChild(option);
    });
    
    container.style.display = "block";
    return true;
  } else {
    container.style.display = "none";
    return false;
  }
}

// ============================================================
// Ocultar nivel de selector
// ============================================================
function hideLevel(containerId) {
  const container = document.getElementById(containerId);
  container.style.display = "none";
  
  const select = container.querySelector("select");
  if (select) {
    select.innerHTML = '<option value="">-- Selecciona --</option>';
  }
}

// ============================================================
// Guardar producto
// ============================================================
async function saveProduct() {
  // Validar que se haya seleccionado una categoría
  const categoryId = document.getElementById("final-category-id").value;
  if (!categoryId) {
    alert("Por favor, selecciona una categoría completa");
    return;
  }
  
  // Obtener valores del formulario
  const title = document.getElementById("content-title").value.trim();
  const price = document.getElementById("content-price").value;
  const stockInput = document.getElementById("content-stock");
  const productType = document.getElementById("productType").value;
  const stock = productType === "hardware" ? stockInput.value : "0";
  const url = document.getElementById("content-url").value.trim();
  const description = document.getElementById("content-description").value.trim();
  
  // Crear URLSearchParams para enviar como form data normal
  const formData = new URLSearchParams();
  formData.append("title", title);
  formData.append("price", price);
  formData.append("stock", stock);
  formData.append("productType", productType);
  formData.append("url", url);
  formData.append("description", description);
  formData.append("categoryId", categoryId);
  
  console.log("📤 Enviando producto...");
  console.log(`  title: ${title}`);
  console.log(`  price: ${price}`);
  console.log(`  stock: ${stock}`);
  console.log(`  productType: ${productType}`);
  console.log(`  categoryId: ${categoryId}`);
  
  try {
    const response = await fetch("/admin/media", {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: formData.toString()
    });
    
    if (response.status === 302 || response.ok) {
      alert("✅ Producto creado exitosamente");
      window.location.href = "/admin/media";
    } else {
      alert("❌ Error al crear el producto");
    }
  } catch (error) {
    console.error("Error:", error);
    alert("❌ Error de conexión");
  }
}

// ============================================================
// EXPLORADOR DE ARCHIVOS
// ============================================================

// Variable para rastrear la ruta actual
let currentPath = [];

// ============================================================
// Cargar archivos al abrir el modal
// ============================================================
document.getElementById("browse-btn").addEventListener("click", () => {
  loadFiles([]);
});

// ============================================================
// Cargar lista de archivos
// ============================================================
async function loadFiles(path) {
  currentPath = path;
  
  try {
    const response = await fetch("/api/files/list");
    const data = await response.json();
    
    const fileList = document.getElementById("file-list");
    fileList.innerHTML = "";
    
    // Filtrar archivos por la ruta actual
    const filteredFiles = data.files.filter(file => {
      const filePathParts = file.path.split("/");
      return filePathParts.length === (path.length + 1) && 
             filePathParts.slice(0, path.length).join("/") === path.join("/");
    });
    
    // Agregar botón "atrás" si no estamos en raíz
    if (path.length > 0) {
      const backItem = document.createElement("button");
      backItem.className = "list-group-item list-group-item-action d-flex align-items-center";
      backItem.innerHTML = '<i class="bi bi-arrow-left me-2"></i> ..';
      backItem.onclick = () => loadFiles(path.slice(0, -1));
      fileList.appendChild(backItem);
    }
    
    // Agregar archivos/carpetas
    filteredFiles.forEach(file => {
      const item = document.createElement("button");
      item.className = "list-group-item list-group-item-action d-flex align-items-center";
      
      const icon = file.type === "folder" ? 
        '<i class="bi bi-folder me-2 text-warning"></i>' : 
        '<i class="bi bi-file-earmark-image me-2 text-success"></i>';
      
      item.innerHTML = `${icon} ${file.name}`;
      
      if (file.type === "folder") {
        item.onclick = () => loadFiles([...path, file.name]);
      } else {
        item.onclick = () => selectFile(file.path);
      }
      
      fileList.appendChild(item);
    });
    
    if (filteredFiles.length === 0) {
      fileList.innerHTML = '<div class="text-center text-muted py-4">No hay archivos en esta carpeta</div>';
    }
    
  } catch (error) {
    console.error("Error cargando archivos:", error);
    document.getElementById("file-list").innerHTML = 
      '<div class="text-center text-danger py-4">Error al cargar archivos</div>';
  }
}

// ============================================================
// Seleccionar archivo
// ============================================================
function selectFile(filePath) {
  const fullPath = `/assets/images/${filePath}`;
  document.getElementById("content-url").value = fullPath;
  
  // Cerrar modal
  const modal = bootstrap.Modal.getInstance(document.getElementById("fileBrowserModal"));
  modal.hide();
  
  console.log(`📁 Archivo seleccionado: ${fullPath}`);
}
