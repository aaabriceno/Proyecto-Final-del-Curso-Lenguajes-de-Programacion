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
  loadCategories().then(() => {
    // Luego cargar datos del producto
    loadProductData(productId);
    // Configurar eventos de cascada
    setupCascadeSelectors();
  });

  // Manejar envío del formulario
  document.getElementById("edit-form").addEventListener("submit", (e) => {
    e.preventDefault();
    saveProduct(productId);
  });
});

// ============================================================
// Cargar todas las categorías
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
    
    // Mostrar categoría actual
    document.getElementById("current-category-path").textContent = product.categoryPath || "Sin categoría";
    
    // Preseleccionar categoría en cascada
    if (product.categoryId) {
      preselectCategory(product.categoryId);
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
  
  if (!categoryId) {
    alert("Por favor selecciona una categoría completa");
    return;
  }
  
  const formData = new FormData();
  formData.append("title", document.getElementById("title").value);
  formData.append("price", document.getElementById("price").value);
  formData.append("stock", document.getElementById("stock").value);
  formData.append("url", document.getElementById("url").value);
  formData.append("description", document.getElementById("description").value);
  formData.append("categoryId", categoryId);
  formData.append("mediaType", "video"); // Valor por defecto
  
  try {
    const response = await fetch(`/admin/media/${productId}`, {
      method: "POST",
      body: formData
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