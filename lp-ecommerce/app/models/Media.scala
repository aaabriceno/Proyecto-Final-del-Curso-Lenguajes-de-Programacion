package models

import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Tipos de medios disponibles
 */
sealed trait MediaType { def asString: String }

object MediaType {
  case object Image extends MediaType { val asString = "image" }
  case object Audio extends MediaType { val asString = "audio" }
  case object Video extends MediaType { val asString = "video" }

  def from(s: String): MediaType = s.toLowerCase match {
    case "image" => Image
    case "audio" => Audio
    case "video" => Video
    case _       => Image
  }
}

/**
 * Representa un producto multimedia en venta.
 */
case class Media(
  id: Long,
  title: String,
  description: String,
  mtype: MediaType,
  price: BigDecimal,
  rating: Double,
  categoryId: Option[Long] = None,     // Categoría asociada
  assetPath: String,                   // Ruta del archivo (en /public/media/ o /public/images/)
  coverImage: Option[String] = None,   // Imagen de portada (ruta a /assets/images/)
  stock: Int = 0,                      // Stock disponible
  promotionId: Option[Long] = None,    // ID de promoción asociada (si aplica)
  isActive: Boolean = true             // Soft delete
) {

  // Obtener imagen de portada o placeholder
  def getCoverImageUrl: String = coverImage.getOrElse("/assets/images/placeholder.jpg")

  // ========= RELACIONES =========

  def downloads: Int = DownloadRepo.downloadsByMedia(id)
  def revenue: BigDecimal = DownloadRepo.revenueByMedia(id)
  def category: Option[Category] = categoryId.flatMap(CategoryRepo.find)
  def categoryBreadcrumb: Vector[Category] =
    categoryId.map(CategoryRepo.getBreadcrumb).getOrElse(Vector.empty)

  // ========= STOCK =========

  def hasStock(quantity: Int = 1): Boolean = stock >= quantity
  def isLowStock: Boolean = stock > 0 && stock <= 10
  def isOutOfStock: Boolean = stock <= 0

  def stockStatus: String =
    if (isOutOfStock) "AGOTADO"
    else if (stock == 1) "ÚLTIMA UNIDAD"
    else if (isLowStock) s"ÚLTIMAS $stock UNIDADES"
    else "DISPONIBLE"

  // ========= PROMOCIONES Y PRECIOS =========

  def activePromotion: Option[Promotion] =
    PromotionRepo.getBestPromotionFor(this)

  def hasActivePromotion: Boolean = activePromotion.isDefined
  def promotionDiscount: Int = activePromotion.map(_.discountPercent).getOrElse(0)

  /** Calcula el precio final considerando promoción o descuento VIP */
  def finalPrice(user: Option[User] = None): BigDecimal = {
    activePromotion match {
      case Some(promo) =>
        promo.applyDiscount(price)
      case None =>
        user match {
          case Some(u) if u.totalSpent >= 100 => price * BigDecimal(0.80) // 20% descuento VIP
          case _                              => price
        }
    }
  }

  def originalPrice: BigDecimal = price

  def discountText: Option[String] =
    activePromotion.map(p => s"${p.discountPercent}% OFF")
}

/**
 * Repositorio en memoria de productos multimedia
 */
object MediaRepo {

  private val collection = MongoConnection.Collections.media

  // ========= CONVERSIONES DOCUMENT <-> MEDIA =========

  private def nextId(): Long = synchronized {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    val maxId = if (docs.isEmpty) 0L else {
      docs.map(doc => doc.getLong("_id").toLong).max
    }
    maxId + 1L
  }

  private def docToMedia(doc: Document): Media = {
    // Extrae Long opcional de un campo del documento
    def getLongOpt(fieldName: String): Option[Long] = {
      try {
        Some(doc.getLong(fieldName))
      } catch {
        case _: Exception => None
      }
    }
    
    // Extrae String opcional de un campo del documento
    def getStringOpt(fieldName: String): Option[String] = {
      try {
        Option(doc.getString(fieldName))
      } catch {
        case _: Exception => None
      }
    }
    
    Media(
      id = doc.getLong("_id"),
      title = doc.getString("title"),
      description = doc.getString("description"),
      mtype = MediaType.from(doc.getString("mtype")),
      price = BigDecimal(doc.getDouble("price")),
      rating = doc.getDouble("rating"),
      categoryId = getLongOpt("categoryId"),
      assetPath = doc.getString("assetPath"),
      coverImage = getStringOpt("coverImage"),
      stock = doc.getInteger("stock"),
      promotionId = getLongOpt("promotionId"),
      isActive = doc.getBoolean("isActive")
    )
  }

  private def mediaToDoc(media: Media): Document = {
    val doc = Document(
      "_id" -> media.id,
      "title" -> media.title,
      "description" -> media.description,
      "mtype" -> media.mtype.asString,
      "price" -> media.price.toDouble,
      "rating" -> media.rating,
      "assetPath" -> media.assetPath,
      "stock" -> media.stock,
      "isActive" -> media.isActive
    )
    // Agregar campos opcionales solo si están presentes
    media.categoryId.foreach(cid => doc.append("categoryId", cid))
    media.promotionId.foreach(pid => doc.append("promotionId", pid))
    media.coverImage.foreach(img => doc.append("coverImage", img))
    doc
  }

  // ========= CONSULTAS =========

  def all: Vector[Media] = {
    val docs = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    )
    docs.map(docToMedia).toVector.sortBy(-_.downloads)
  }

  def find(id: Long): Option[Media] = {
    val result = Await.result(
      collection.find(and(equal("_id", id), equal("isActive", true))).toFuture(),
      5.seconds
    )
    result.headOption.map(docToMedia)
  }

  def search(query: String): Vector[Media] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else {
      val allMedia = Await.result(
        collection.find(equal("isActive", true)).toFuture(),
        5.seconds
      ).map(docToMedia).toVector
      
      allMedia.filter { m =>
        m.title.toLowerCase.contains(q) ||
        m.description.toLowerCase.contains(q) ||
        m.id.toString == q
      }.sortBy(-_.downloads)
    }
  }

  def filterByType(mtype: MediaType): Vector[Media] = {
    val docs = Await.result(
      collection.find(and(equal("mtype", mtype.asString), equal("isActive", true))).toFuture(),
      5.seconds
    )
    docs.map(docToMedia).toVector.sortBy(-_.downloads)
  }

  def filterByCategory(categoryId: Long): Vector[Media] = {
    val categoryIds = categoryId +: CategoryRepo.getAllDescendants(categoryId).map(_.id)
    val allMedia = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    ).map(docToMedia).toVector
    
    allMedia.filter(m => m.categoryId.exists(categoryIds.contains)).sortBy(-_.downloads)
  }

  def searchAdvanced(
    query: Option[String] = None,
    mtype: Option[MediaType] = None,
    categoryId: Option[Long] = None
  ): Vector[Media] = {
    val allMedia = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    ).map(docToMedia).toVector
    
    var results = allMedia

    mtype.foreach(t => results = results.filter(_.mtype == t))
    categoryId.foreach { cid =>
      val allIds = cid +: CategoryRepo.getAllDescendants(cid).map(_.id)
      results = results.filter(m => m.categoryId.exists(allIds.contains))
    }
    query.foreach { q =>
      val qLower = q.toLowerCase.trim
      if (qLower.nonEmpty)
        results = results.filter(m =>
          m.title.toLowerCase.contains(qLower) ||
          m.description.toLowerCase.contains(qLower) ||
          m.id.toString == qLower)
    }
    results.sortBy(-_.downloads)
  }

  // ========= ESTADÍSTICAS =========

  def totalProducts: Int = {
    val docs = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    )
    docs.size
  }
  
  def totalRevenue: BigDecimal = DownloadRepo.totalRevenue
  
  def averagePrice: BigDecimal = {
    val allMedia = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    ).map(docToMedia).toVector
    
    if (allMedia.nonEmpty) allMedia.map(_.price).sum / BigDecimal(allMedia.size) 
    else BigDecimal(0.0)
  }
  
  def averageRating: Double = {
    val allMedia = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    ).map(docToMedia).toVector
    
    if (allMedia.nonEmpty) allMedia.map(_.rating).sum / allMedia.size 
    else 0.0
  }
  
  def totalDownloads: Int = DownloadRepo.totalDownloads
  
  def countByType(mtype: MediaType): Int = {
    val docs = Await.result(
      collection.find(and(equal("mtype", mtype.asString), equal("isActive", true))).toFuture(),
      5.seconds
    )
    docs.size
  }
  
  def topProducts(limit: Int = 5): Vector[Media] = {
    val allMedia = Await.result(
      collection.find(equal("isActive", true)).toFuture(),
      5.seconds
    ).map(docToMedia).toVector
    
    allMedia.sortBy(-_.downloads).take(limit)
  }

  // ========= CRUD ADMIN =========

  def add(title: String, description: String, mtype: MediaType,
          price: BigDecimal, categoryId: Option[Long], assetPath: String,
          coverImage: Option[String] = None,
          stock: Int = 0, promotionId: Option[Long] = None): Media = synchronized {
    val media = Media(nextId(), title, description, mtype, price, 0.0, categoryId, assetPath, coverImage, stock, promotionId)
    Await.result(
      collection.insertOne(mediaToDoc(media)).toFuture(),
      5.seconds
    )
    println(s"✅ Producto creado en MongoDB: ${media.title} (ID: ${media.id})")
    media
  }

  def update(id: Long, title: String, description: String, mtype: MediaType,
             price: BigDecimal, categoryId: Option[Long], assetPath: String,
             coverImage: Option[String] = None,
             stock: Int, promotionId: Option[Long] = None): Option[Media] = synchronized {
    find(id).map { oldMedia =>
      val updated = oldMedia.copy(
        title = title, 
        description = description,
        mtype = mtype, 
        price = price, 
        categoryId = categoryId,
        assetPath = assetPath,
        coverImage = coverImage,
        stock = stock,
        promotionId = promotionId
      )
      
      Await.result(
        collection.replaceOne(equal("_id", id), mediaToDoc(updated)).toFuture(),
        5.seconds
      )
      println(s"✅ Producto actualizado en MongoDB: ${updated.title} (ID: ${id})")
      updated
    }
  }

  def reduceStock(id: Long, quantity: Int): Either[String, Media] = synchronized {
    find(id) match {
      case None => Left("Producto no encontrado")
      case Some(media) =>
        if (media.stock < quantity)
          Left(s"Stock insuficiente. Solo quedan ${media.stock} unidades disponibles")
        else {
          val updated = media.copy(stock = media.stock - quantity)
          Await.result(
            collection.updateOne(
              equal("_id", id),
              set("stock", updated.stock)
            ).toFuture(),
            5.seconds
          )
          println(s"📦 Stock reducido: ${media.title} (-${quantity}, quedan ${updated.stock})")
          Right(updated)
        }
    }
  }

  def addStock(id: Long, quantity: Int): Option[Media] = synchronized {
    find(id).map { media =>
      val updated = media.copy(stock = media.stock + quantity)
      Await.result(
        collection.updateOne(
          equal("_id", id),
          set("stock", updated.stock)
        ).toFuture(),
        5.seconds
      )
      println(s"📦 Stock agregado: ${media.title} (+${quantity}, total ${updated.stock})")
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    find(id) match {
      case Some(media) =>
        Await.result(
          collection.updateOne(
            equal("_id", id),
            set("isActive", false)
          ).toFuture(),
          5.seconds
        )
        println(s"🗑️ Producto eliminado (soft delete): ${media.title} (ID: ${id})")
        true
      case None => false
    }
  }
}
