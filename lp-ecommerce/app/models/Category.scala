package models

/**
 * Modelo que representa una categoría jerárquica (puede tener subcategorías)
 */
case class Category(
  id: Long,
  name: String,
  parentId: Option[Long] = None, // None = categoría raíz
  description: String = ""
)

/**
 * Repositorio in-memory de categorías
 * Compatible con Scala 2.13, sin frameworks ni librerías externas.
 */
object CategoryRepo {

  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  // Datos iniciales (semilla)
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

  // ==============================
  // CONSULTAS
  // ==============================

  def all: Vector[Category] =
    data.sortBy(c => (c.parentId.getOrElse(0L), c.name))

  def find(id: Long): Option[Category] =
    data.find(_.id == id)

  def getRoots: Vector[Category] =
    data.filter(_.parentId.isEmpty).sortBy(_.name)

  def getChildren(parentId: Long): Vector[Category] =
    data.filter(_.parentId.contains(parentId)).sortBy(_.name)

  def hasChildren(categoryId: Long): Boolean =
    data.exists(_.parentId.contains(categoryId))

  /** Retorna la jerarquía completa (breadcrumb) desde la raíz */
  def getBreadcrumb(categoryId: Long): Vector[Category] = {
    def buildPath(id: Long, acc: Vector[Category]): Vector[Category] = {
      find(id) match {
        case Some(cat) =>
          cat.parentId match {
            case Some(parentId) => buildPath(parentId, cat +: acc)
            case None           => cat +: acc
          }
        case None => acc
      }
    }
    buildPath(categoryId, Vector.empty).reverse
  }

  /** Retorna el árbol completo de categorías junto con la profundidad */
  def getTree: Vector[(Category, Int)] = {
    def buildTree(parentId: Option[Long], depth: Int): Vector[(Category, Int)] = {
      val children = parentId match {
        case Some(pid) => getChildren(pid)
        case None      => getRoots
      }
      children.flatMap(cat => Vector((cat, depth)) ++ buildTree(Some(cat.id), depth + 1))
    }
    buildTree(None, 0)
  }

  def getAllDescendants(categoryId: Long): Vector[Category] = {
    val children = getChildren(categoryId)
    children ++ children.flatMap(c => getAllDescendants(c.id))
  }

  /** Cuenta productos dentro de una categoría (y subcategorías) */
  def countProducts(categoryId: Long): Int = {
    val descendantIds = categoryId +: getAllDescendants(categoryId).map(_.id)
    MediaRepo.all.count(m => m.categoryId.exists(descendantIds.contains))
  }

  // ==============================
  // CRUD
  // ==============================

  def add(name: String, parentId: Option[Long], description: String = ""): Category = synchronized {
    parentId.foreach { pid =>
      require(find(pid).isDefined, s"Categoría padre $pid no existe")
    }

    val category = Category(nextId(), name, parentId, description)
    data :+= category
    category
  }

  def update(id: Long, name: String, parentId: Option[Long], description: String): Option[Category] = synchronized {
    parentId.foreach { pid =>
      require(pid != id, "Una categoría no puede ser su propia hija")

      // Evitar ciclos
      val descendants = getAllDescendants(id)
      require(!descendants.exists(_.id == pid), "No se puede crear un ciclo en el árbol de categorías")

      // Verificar padre existente
      require(find(pid).isDefined, s"Categoría padre $pid no existe")
    }

    data.find(_.id == id).map { oldCategory =>
      val updated = oldCategory.copy(name = name, parentId = parentId, description = description)
      data = data.map(c => if (c.id == id) updated else c)
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    if (hasChildren(id))
      throw new IllegalArgumentException("No se puede eliminar una categoría con subcategorías")

    if (MediaRepo.all.exists(_.categoryId.contains(id)))
      throw new IllegalArgumentException("No se puede eliminar una categoría con productos")

    val before = data.size
    data = data.filterNot(_.id == id)
    data.size < before
  }

  def search(query: String): Vector[Category] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else data.filter(c => c.name.toLowerCase.contains(q) || c.description.toLowerCase.contains(q))
  }
}
