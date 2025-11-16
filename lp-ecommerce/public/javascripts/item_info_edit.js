// ============================================================
// Editar producto - Selector en cascada de categorías
// ============================================================

let allCategories = [];
let currentProduct = null;

document.addEventListener("DOMContentLoaded", () => {
  // Extraer ID de la URL: /admin/media/3/edit -> 3
  const pathParts = window.location.pathname.split('/');
  const productId = pathParts[pathParts.length - 2]; // El ID está antes de "edit"

  if (!productId || isNaN(productId)) {
    alert("Error: No se especificó un ID de producto válido");
    window.location.href = "/admin/media";
    return;
  }

  // Cargar categorías primero
  loadAllCategories().then(() => {
    // Luego cargar datos del producto
    loadProductData(productId);
    // Configurar eventos de cascada
    setupCascadeSelectors();
    // Configurar evento de cambio de tipo de producto
    setupProductTypeSelector();
  });

  // Manejar envío del formulario
  document.getElementById("edit-form").addEventListener("submit", (e) => {
    e.preventDefault();
    saveProduct(productId);
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
  
  productTypeSelect.addEventListener("change", (e) => {
    const selectedType = e.target.value;
    console.log(`🔄 Tipo de producto cambiado a: ${selectedType}`);
    
    if (selectedType === "digital") {
      loadCategoriesByType("digital");
    } else if (selectedType === "hardware") {
      loadCategoriesByType("hardware");
    } else {
      // Limpiar todos los selectores
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
    option.dataset.level = cat.level;
    level1Select.appendChild(option);
  });
  
  console.log(`📁 Cargadas ${level1Categories.length} categorías raíz en nivel 1`);
}

// ============================================================
// Limpiar todos los selectores de categoría
// ============================================================
function clearAllSelectors() {
  // Limpiar nivel 1
  const level1Select = document.getElementById("level1-select");
  level1Select.innerHTML = '<option value="">-- Selecciona nivel 1 --</option>';
  
  // Limpiar y ocultar nivel 2
  const level2Select = document.getElementById("level2-select");
  level2Select.innerHTML = '<option value="">-- Selecciona nivel 2 --</option>';
  hideLevel("level2-container");
  
  // Limpiar y ocultar nivel 3
  const level3Select = document.getElementById("level3-select");
  level3Select.innerHTML = '<option value="">-- Selecciona nivel 3 --</option>';
  hideLevel("level3-container");
  
  // Limpiar y ocultar nivel 4
  const level4Select = document.getElementById("level4-select");
  level4Select.innerHTML = '<option value="">-- Selecciona categoría final --</option>';
  hideLevel("level4-container");
  
  // Limpiar campo oculto
  document.getElementById("final-category-id").value = "";
}

// ============================================================
// Cargar todas las categorías (DEPRECADO - usar loadAllCategories + loadCategoriesByType)
// ============================================================
async function loadCategories() {
  try {
    const response = await fetch("/api/categories");
    const data = await response.json();
    allCategories = data.categories;
    
    // Llenar nivel 1 (raíz)
    const level1Select = document.getElementById("level1-select");
    const level1Categories = allCategories.filter(cat => cat.level === 0);
    
    level1Categories.forEach(cat => {
      const option = document.createElement("option");
      option.value = cat.id;
      option.textContent = cat.name;
      option.dataset.level = cat.level;
      level1Select.appendChild(option);
    });
    
  } catch (error) {
    console.error("Error cargando categorías:", error);
    alert("Error al cargar las categorías");
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
        // No hay nivel 3, esta es la categoría final
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
        // No hay nivel 4, esta es la categoría final
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
  
  // Limpiar opciones anteriores
  select.innerHTML = '<option value="">-- Selecciona --</option>';
  
  // Buscar categorías hijas
  const children = allCategories.filter(cat => cat.parentId === parentId);
  
  if (children.length > 0) {
    children.forEach(cat => {
      const option = document.createElement("option");
      option.value = cat.id;
      option.textContent = cat.name;
      select.appendChild(option);
    });
    
    // Mostrar contenedor
    container.style.display = "block";
    return true; // Tiene hijos
  } else {
    // No tiene hijos, ocultar contenedor
    container.style.display = "none";
    return false; // No tiene hijos
  }
}

// ============================================================
// Ocultar nivel
// ============================================================
function hideLevel(containerId) {
  const container = document.getElementById(containerId);
  container.style.display = "none";
  
  // Limpiar select dentro del contenedor
  const select = container.querySelector("select");
  if (select) {
    select.innerHTML = '<option value="">-- Selecciona --</option>';
  }
}

// ============================================================
// Cargar datos del producto y preseleccionar categoría
// ============================================================
async function loadProductData(productId) {
  try {
    const response = await fetch("/api/media");
    const data = await response.json();
    
    const product = data.products.find(p => p.id === parseInt(productId));
    
    if (!product) {
      alert("Producto no encontrado");
      window.location.href = "/admin/media";
      return;
    }
    
    currentProduct = product;
    
    // Rellenar formulario
    document.getElementById("product-id").value = product.id;
    document.getElementById("title").value = product.title;
    document.getElementById("price").value = product.price;
    document.getElementById("stock").value = product.stock;
    document.getElementById("url").value = product.assetPath || "";
    document.getElementById("description").value = product.description;
    
    // 🔥 NUEVO: Establecer productType y disparar evento para cargar categorías
    const productType = product.productType || "digital"; // Default a digital si no existe
    
    document.getElementById("productType").value = productType;
    console.log(`📦 Producto cargado con tipo: ${productType}`);
    
    // Cargar categorías filtradas por tipo
    loadCategoriesByType(productType);
    
    // Mostrar categoría actual
    document.getElementById("current-category-path").textContent = product.categoryPath || "Sin categoría";
    
    // Preseleccionar categoría en cascada (con delay para que las categorías se carguen)
    if (product.categoryId) {
      setTimeout(() => {
        preselectCategory(product.categoryId);
      }, 300);
    }
    
  } catch (error) {
    console.error("Error cargando producto:", error);
    alert("Error al cargar los datos del producto");
  }
}

// ============================================================
// Preseleccionar categoría siguiendo la jerarquía
// ============================================================
function preselectCategory(categoryId) {
  // Obtener breadcrumb de la categoría
  const category = allCategories.find(cat => cat.id === categoryId);
  if (!category) return;
  
  const breadcrumb = getBreadcrumb(categoryId);
  
  // Seleccionar nivel por nivel
  if (breadcrumb.length > 0) {
    // Nivel 1
    document.getElementById("level1-select").value = breadcrumb[0].id;
    document.getElementById("level1-select").dispatchEvent(new Event("change"));
    
    setTimeout(() => {
      if (breadcrumb.length > 1) {
        // Nivel 2
        document.getElementById("level2-select").value = breadcrumb[1].id;
        document.getElementById("level2-select").dispatchEvent(new Event("change"));
        
        setTimeout(() => {
          if (breadcrumb.length > 2) {
            // Nivel 3
            document.getElementById("level3-select").value = breadcrumb[2].id;
            document.getElementById("level3-select").dispatchEvent(new Event("change"));
            
            setTimeout(() => {
              if (breadcrumb.length > 3) {
                // Nivel 4
                document.getElementById("level4-select").value = breadcrumb[3].id;
                document.getElementById("final-category-id").value = breadcrumb[3].id;
              }
            }, 100);
          }
        }, 100);
      }
    }, 100);
  }
}

// ============================================================
// Obtener breadcrumb de una categoría
// ============================================================
function getBreadcrumb(categoryId) {
  const breadcrumb = [];
  let current = allCategories.find(cat => cat.id === categoryId);
  
  while (current) {
    breadcrumb.unshift(current);
    if (current.parentId) {
      current = allCategories.find(cat => cat.id === current.parentId);
    } else {
      break;
    }
  }
  
  return breadcrumb;
}

// ============================================================
// Guardar cambios del producto
// ============================================================
async function saveProduct(productId) {
  const categoryId = document.getElementById("final-category-id").value;
  
  console.log("🔍 [DEBUG] Valores del formulario ANTES de validar:");
  console.log(`  categoryId (desde final-category-id): '${categoryId}'`);
  
  if (!categoryId) {
    alert("Por favor selecciona una categoría completa");
    return;
  }
  
  // Obtener valores del formulario
  const title = document.getElementById("title").value.trim();
  const price = document.getElementById("price").value;
  const stock = document.getElementById("stock").value;
  const productType = document.getElementById("productType").value;
  const url = document.getElementById("url").value.trim();
  const description = document.getElementById("description").value.trim();
  
  // Crear URLSearchParams para enviar como form data normal
  const formData = new URLSearchParams();
  formData.append("title", title);
  formData.append("price", price);
  formData.append("stock", stock);
  formData.append("productType", productType);
  formData.append("url", url);
  formData.append("description", description);
  formData.append("categoryId", categoryId); // ✅ AGREGADO
  
  console.log("📤 Actualizando producto...");
  console.log(`  title: ${title}`);
  console.log(`  price: ${price}`);
  console.log(`  stock: ${stock}`);
  console.log(`  productType: ${productType}`);
  console.log(`  categoryId: ${categoryId}`);
  console.log(`  url: ${url}`);
  
  try {
    const response = await fetch(`/admin/media/${productId}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded"
      },
      body: formData.toString()
    });
    
    if (response.ok) {
      alert("✅ Producto actualizado correctamente");
      window.location.href = "/admin/media";
    } else {
      alert("❌ Error al actualizar el producto");
    }
    
  } catch (error) {
    console.error("Error guardando producto:", error);
    alert("Error de conexión al guardar");
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
  document.getElementById("url").value = fullPath;
  
  // Cerrar modal
  const modal = bootstrap.Modal.getInstance(document.getElementById("fileBrowserModal"));
  modal.hide();
  
  console.log(`📁 Archivo seleccionado: ${fullPath}`);
}