package models

import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Modelo que representa una categoría jerárquica (puede tener subcategorías)
 */
case class Category(
  id: Long,
  name: String,
  parentId: Option[Long] = None,      // None = categoría raíz
  description: String = "",
  productType: String = "digital",    // "digital" o "hardware" - filtro por tipo de producto
  isActive: Boolean = true            // Soft delete
)

/**
 * Repositorio in-memory de categorías
 * Compatible con Scala 2.13, sin frameworks ni librerías externas.
 */
object CategoryRepo {

  private val collection = MongoConnection.Collections.categories

  // ========= CONVERSIONES DOCUMENT <-> CATEGORY =========

  private def nextId(): Long = synchronized {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    val maxId = if (docs.isEmpty) 0L else {
      docs.map(doc => doc.getLong("_id").toLong).max
    }
    maxId + 1L
  }

  private def getLongOpt(doc: Document, fieldName: String): Option[Long] = {
    try {
      Some(doc.getLong(fieldName))
    } catch {
      case _: Exception => None
    }
  }

  private def docToCategory(doc: Document): Category = {
    val productType = try {
      doc.getString("productType")
    } catch {
      case _: Exception => "digital"  // Default para backward compatibility
    }
    
    Category(
      id = doc.getLong("_id"),
      name = doc.getString("name"),
      parentId = getLongOpt(doc, "parentId"),
      description = doc.getString("description"),
      productType = productType,
      isActive = doc.getBoolean("isActive")
    )
  }

  private def categoryToDoc(category: Category): Document = {
    import org.mongodb.scala.bson.{BsonNull, BsonInt64}
    
    val baseDoc = Document(
      "_id" -> category.id,
      "name" -> category.name,
      "description" -> category.description,
      "productType" -> category.productType,
      "isActive" -> category.isActive
    )
    
    // Agregar parentId SIEMPRE (null si no tiene padre, Long si tiene)
    val finalDoc = category.parentId match {
      case Some(pid) => baseDoc + ("parentId" -> pid)  // ✅ Usa + en lugar de append
      case None      => baseDoc + ("parentId" -> BsonNull())
    }
    
    finalDoc
  }

  // ==============================
  // CONSULTAS
  // ==============================

  def all: Vector[Category] = {
    val docs = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    )
    docs.map(docToCategory).toVector.sortBy(c => (c.parentId.getOrElse(0L), c.name))
  }

  def find(id: Long): Option[Category] = {
    val result = Await.result(
      collection.find(and(equal("_id", id), equal("isActive", true))).toFuture(),
      5.seconds
    )
    result.headOption.map(docToCategory)
  }

  def getRoots: Vector[Category] = {
    val allCategories = all
    allCategories.filter(_.parentId.isEmpty).sortBy(_.name)
  }

  def getChildren(parentId: Long): Vector[Category] = {
    val allCategories = all
    allCategories.filter(_.parentId.contains(parentId)).sortBy(_.name)
  }

  def hasChildren(categoryId: Long): Boolean = {
    val allCategories = all
    allCategories.exists(_.parentId.contains(categoryId))
  }

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
    buildPath(categoryId, Vector.empty)  // SIN .reverse - ya está en orden correcto
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

  /** Crear categoría con ID específico (para scripts de inicialización) */
  def create(category: Category): Category = synchronized {
    category.parentId.foreach { pid =>
      require(find(pid).isDefined, s"Categoría padre $pid no existe")
    }

    Await.result(
      collection.insertOne(categoryToDoc(category)).toFuture(),
      5.seconds
    )
    println(s"✅ Categoría creada: ${category.name} (ID: ${category.id})")
    category
  }

  /** Eliminar TODAS las categorías (para scripts de reorganización) */
  def deleteAll(): Unit = synchronized {
    Await.result(
      collection.deleteMany(Document()).toFuture(),
      5.seconds
    )
    println(s"🗑️  Todas las categorías eliminadas")
  }

  def add(name: String, parentId: Option[Long], description: String = ""): Category = synchronized {
    parentId.foreach { pid =>
      require(find(pid).isDefined, s"Categoría padre $pid no existe")
    }

    val category = Category(nextId(), name, parentId, description)
    Await.result(
      collection.insertOne(categoryToDoc(category)).toFuture(),
      5.seconds
    )
    println(s"✅ Categoría creada en MongoDB: ${category.name} (ID: ${category.id})")
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

    find(id).map { oldCategory =>
      val updated = oldCategory.copy(name = name, parentId = parentId, description = description)
      Await.result(
        collection.replaceOne(equal("_id", id), categoryToDoc(updated)).toFuture(),
        5.seconds
      )
      println(s"✅ Categoría actualizada en MongoDB: ${updated.name} (ID: ${id})")
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    if (hasChildren(id))
      throw new IllegalArgumentException("No se puede eliminar una categoría con subcategorías")

    if (MediaRepo.all.exists(_.categoryId.contains(id)))
      throw new IllegalArgumentException("No se puede eliminar una categoría con productos")

    find(id) match {
      case Some(category) =>
        Await.result(
          collection.updateOne(
            equal("_id", id),
            set("isActive", false)
          ).toFuture(),
          5.seconds
        )
        println(s"🗑️ Categoría eliminada (soft delete): ${category.name} (ID: ${id})")
        true
      case None => false
    }
  }

  def search(query: String): Vector[Category] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else {
      val allCategories = all
      allCategories.filter(c => c.name.toLowerCase.contains(q) || c.description.toLowerCase.contains(q))
    }
  }
}
