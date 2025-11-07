package models

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
  stock: Int = 0                       // Stock disponible
) {

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

  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  // Seed inicial con ejemplos
  private var data: Vector[Media] = Vector(
    Media(nextId(), "Poster Aurora", "Póster en alta resolución",
      MediaType.Image, 7.50, 4.6, Some(11), "images/poster_aurora.jpg", 25),
    Media(nextId(), "Beat LoFi #12", "Pista LoFi 48kHz",
      MediaType.Audio, 4.00, 4.8, Some(7), "media/audio/lofi12.mp3", 50),
    Media(nextId(), "Corto City Rush", "Clip FullHD 30s",
      MediaType.Video, 11.99, 4.4, Some(9), "media/video/city_rush.mp4", 8)
  )

  // ========= CONSULTAS =========

  def all: Vector[Media] = data.sortBy(-_.downloads)
  def find(id: Long): Option[Media] = data.find(_.id == id)

  def search(query: String): Vector[Media] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else data.filter { m =>
      m.title.toLowerCase.contains(q) ||
      m.description.toLowerCase.contains(q) ||
      m.id.toString == q
    }.sortBy(-_.downloads)
  }

  def filterByType(mtype: MediaType): Vector[Media] =
    data.filter(_.mtype == mtype).sortBy(-_.downloads)

  def filterByCategory(categoryId: Long): Vector[Media] = {
    val categoryIds = categoryId +: CategoryRepo.getAllDescendants(categoryId).map(_.id)
    data.filter(m => m.categoryId.exists(categoryIds.contains)).sortBy(-_.downloads)
  }

  def searchAdvanced(
    query: Option[String] = None,
    mtype: Option[MediaType] = None,
    categoryId: Option[Long] = None
  ): Vector[Media] = {
    var results = data

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

  def totalProducts: Int = data.size
  def totalRevenue: BigDecimal = DownloadRepo.totalRevenue
  def averagePrice: BigDecimal =
    if (data.nonEmpty) data.map(_.price).sum / BigDecimal(data.size) else BigDecimal(0.0)
  def averageRating: Double =
    if (data.nonEmpty) data.map(_.rating).sum / data.size else 0.0
  def totalDownloads: Int = DownloadRepo.totalDownloads
  def countByType(mtype: MediaType): Int = data.count(_.mtype == mtype)
  def topProducts(limit: Int = 5): Vector[Media] = data.sortBy(-_.downloads).take(limit)

  // ========= CRUD ADMIN =========

  def add(title: String, description: String, mtype: MediaType,
          price: BigDecimal, categoryId: Option[Long], assetPath: String,
          stock: Int = 0): Media = synchronized {
    val media = Media(nextId(), title, description, mtype, price, 0.0, categoryId, assetPath, stock)
    data :+= media
    media
  }

  def update(id: Long, title: String, description: String, mtype: MediaType,
             price: BigDecimal, categoryId: Option[Long], assetPath: String,
             stock: Int): Option[Media] = synchronized {
    data.find(_.id == id).map { oldMedia =>
      val updated = oldMedia.copy(title = title, description = description,
        mtype = mtype, price = price, categoryId = categoryId,
        assetPath = assetPath, stock = stock)
      data = data.map(m => if (m.id == id) updated else m)
      updated
    }
  }

  def reduceStock(id: Long, quantity: Int): Either[String, Media] = synchronized {
    data.find(_.id == id) match {
      case None => Left("Producto no encontrado")
      case Some(media) =>
        if (media.stock < quantity)
          Left(s"Stock insuficiente. Solo quedan ${media.stock} unidades disponibles")
        else {
          val updated = media.copy(stock = media.stock - quantity)
          data = data.map(m => if (m.id == id) updated else m)
          Right(updated)
        }
    }
  }

  def addStock(id: Long, quantity: Int): Option[Media] = synchronized {
    data.find(_.id == id).map { media =>
      val updated = media.copy(stock = media.stock + quantity)
      data = data.map(m => if (m.id == id) updated else m)
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    val sizeBefore = data.size
    data = data.filterNot(_.id == id)
    data.size < sizeBefore
  }
}
