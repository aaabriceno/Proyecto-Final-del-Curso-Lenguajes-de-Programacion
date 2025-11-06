package models

case class Category(
  id: Long,
  name: String,
  parentId: Option[Long] = None,  // None = categoría raíz
  description: String = ""
)

object CategoryRepo {
  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }
  
  // Seed data con categorías de ejemplo
  private var data: Vector[Category] = Vector(
    // Categorías raíz
    Category(nextId(), "Música", None, "Contenido musical de todo tipo"),
    Category(nextId(), "Video", None, "Contenido audiovisual"),
    Category(nextId(), "Diseño", None, "Imágenes y recursos gráficos"),
    
    // Subcategorías de Música
    Category(nextId(), "LoFi", Some(1), "Música LoFi y chill"),
    Category(nextId(), "Electrónica", Some(1), "Música electrónica"),
    Category(nextId(), "Rock", Some(1), "Rock y metal"),
    
    // Sub-subcategorías de LoFi
    Category(nextId(), "Beats", Some(4), "Beats instrumentales LoFi"),
    Category(nextId(), "Ambient", Some(4), "Ambient y atmosférico"),
    
    // Subcategorías de Video
    Category(nextId(), "Cortos", Some(2), "Cortometrajes y clips"),
    Category(nextId(), "Documentales", Some(2), "Documentales"),
    
    // Subcategorías de Diseño
    Category(nextId(), "Pósters", Some(3), "Pósters y carteles"),
    Category(nextId(), "Fotografía", Some(3), "Fotografía profesional"),
    Category(nextId(), "Ilustraciones", Some(3), "Ilustraciones digitales")
  )
  
  // ============ CONSULTAS ============
  
  def all: Vector[Category] = data.sortBy(c => (c.parentId.getOrElse(0L), c.name))
  
  def find(id: Long): Option[Category] = data.find(_.id == id)
  
  // Obtener todas las categorías raíz (sin padre)
  def getRoots: Vector[Category] = data.filter(_.parentId.isEmpty).sortBy(_.name)
  
  // Obtener hijos directos de una categoría
  def getChildren(parentId: Long): Vector[Category] = 
    data.filter(_.parentId.contains(parentId)).sortBy(_.name)
  
  // Verificar si una categoría tiene hijos
  def hasChildren(categoryId: Long): Boolean = 
    data.exists(_.parentId.contains(categoryId))
  
  // Obtener breadcrumb (ruta completa desde raíz)
  def getBreadcrumb(categoryId: Long): Vector[Category] = {
    def buildPath(id: Long, acc: Vector[Category] = Vector.empty): Vector[Category] = {
      find(id) match {
        case Some(cat) =>
          cat.parentId match {
            case Some(parentId) => buildPath(parentId, cat +: acc)
            case None => cat +: acc
          }
        case None => acc
      }
    }
    buildPath(categoryId)
  }
  
  // Obtener árbol completo con profundidad
  def getTree: Vector[(Category, Int)] = {
    def buildTree(parentId: Option[Long], depth: Int): Vector[(Category, Int)] = {
      val children = parentId match {
        case Some(pid) => getChildren(pid)
        case None => getRoots
      }
      children.flatMap { cat =>
        Vector((cat, depth)) ++ buildTree(Some(cat.id), depth + 1)
      }
    }
    buildTree(None, 0)
  }
  
  // Obtener todos los descendientes de una categoría (recursivo)
  def getAllDescendants(categoryId: Long): Vector[Category] = {
    val children = getChildren(categoryId)
    children ++ children.flatMap(c => getAllDescendants(c.id))
  }
  
  // Contar productos en una categoría (incluyendo subcategorías)
  def countProducts(categoryId: Long): Int = {
    val descendantIds = (categoryId +: getAllDescendants(categoryId).map(_.id))
    MediaRepo.all.count(m => m.categoryId.exists(descendantIds.contains))
  }
  
  // ============ CRUD ============
  
  def add(name: String, parentId: Option[Long], description: String = ""): Category = {
    // Validar que el padre existe si se especifica
    parentId.foreach { pid =>
      if (!find(pid).isDefined) {
        throw new IllegalArgumentException(s"Categoría padre $pid no existe")
      }
    }
    
    val category = Category(nextId(), name, parentId, description)
    data = data :+ category
    category
  }
  
  def update(id: Long, name: String, parentId: Option[Long], description: String): Option[Category] = {
    // Validar que no se cree un ciclo (una categoría no puede ser hija de sí misma)
    parentId.foreach { pid =>
      if (pid == id) {
        throw new IllegalArgumentException("Una categoría no puede ser su propia hija")
      }
      
      // Validar que el padre no sea un descendiente de la categoría actual
      val descendants = getAllDescendants(id)
      if (descendants.exists(_.id == pid)) {
        throw new IllegalArgumentException("No se puede crear un ciclo en el árbol de categorías")
      }
      
      // Validar que el padre existe
      if (!find(pid).isDefined) {
        throw new IllegalArgumentException(s"Categoría padre $pid no existe")
      }
    }
    
    data.find(_.id == id).map { oldCategory =>
      val updated = oldCategory.copy(name = name, parentId = parentId, description = description)
      data = data.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  def delete(id: Long): Boolean = {
    // No permitir eliminar si tiene hijos
    if (hasChildren(id)) {
      throw new IllegalArgumentException("No se puede eliminar una categoría con subcategorías")
    }
    
    // No permitir eliminar si tiene productos
    if (MediaRepo.all.exists(_.categoryId.contains(id))) {
      throw new IllegalArgumentException("No se puede eliminar una categoría con productos")
    }
    
    val sizeBefore = data.size
    data = data.filterNot(_.id == id)
    data.size < sizeBefore
  }
  
  // Búsqueda por nombre
  def search(query: String): Vector[Category] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else data.filter(c => c.name.toLowerCase.contains(q) || c.description.toLowerCase.contains(q))
  }
}
