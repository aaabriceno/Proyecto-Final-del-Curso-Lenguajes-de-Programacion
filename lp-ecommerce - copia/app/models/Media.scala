package models

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

case class Media(
  id: Long,
  title: String,
  description: String,
  mtype: MediaType,
  price: BigDecimal,
  rating: Double,
  categoryId: Option[Long] = None,  // Categoría a la que pertenece
  assetPath: String,          // ruta en /public (p.ej. "images/hero.jpg" o "media/video/clip.mp4")
  stock: Int = 0              // Stock disponible (0 = agotado)
) {
  // Estadísticas dinámicas basadas en DownloadRepo
  def downloads: Int = DownloadRepo.downloadsByMedia(id)
  def revenue: BigDecimal = DownloadRepo.revenueByMedia(id)
  
  // Obtener categoría completa
  def category: Option[Category] = categoryId.flatMap(CategoryRepo.find)
  
  // Obtener breadcrumb de categoría
  def categoryBreadcrumb: Vector[Category] = 
    categoryId.map(CategoryRepo.getBreadcrumb).getOrElse(Vector.empty)
  
  // Métodos relacionados con stock
  def hasStock(quantity: Int = 1): Boolean = stock >= quantity
  def isLowStock: Boolean = stock > 0 && stock <= 10
  def isOutOfStock: Boolean = stock <= 0
  def stockStatus: String = {
    if (isOutOfStock) "AGOTADO"
    else if (stock == 1) "ÚLTIMA UNIDAD"
    else if (isLowStock) s"ÚLTIMAS $stock UNIDADES"
    else "DISPONIBLE"
  }
  
  // Métodos relacionados con promociones
  def activePromotion: Option[Promotion] = PromotionRepo.getBestPromotionFor(this)
  def hasActivePromotion: Boolean = activePromotion.isDefined
  def promotionDiscount: Int = activePromotion.map(_.discountPercent).getOrElse(0)
  
  // Precio final considerando promoción (prioridad) o descuento VIP
  def finalPrice(user: Option[User] = None): BigDecimal = {
    activePromotion match {
      case Some(promo) =>
        // Si hay promoción activa, aplicar promoción (ignora VIP)
        promo.applyDiscount(price)
      
      case None =>
        // Si no hay promoción, verificar si usuario es VIP
        user match {
          case Some(u) if u.totalSpent >= 100 =>
            price * 0.80 // 20% descuento VIP
          case _ =>
            price
        }
    }
  }
  
  // Precio original tachado (para mostrar en UI)
  def originalPrice: BigDecimal = price
  
  // Texto del descuento para mostrar
  def discountText: Option[String] = {
    activePromotion.map(p => s"${p.discountPercent}% OFF")
  }
}

object MediaRepo {
  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  // Dummy seed con categorías asignadas y stock
  private var data: Vector[Media] = Vector(
    Media(nextId(), "Poster Aurora", "Póster en alta resolución", MediaType.Image, 7.50, 4.6,
      Some(11), "images/poster_aurora.jpg", 25),  // Categoría: Pósters, Stock: 25
    Media(nextId(), "Beat LoFi #12", "Pista LoFi 48kHz", MediaType.Audio, 4.00, 4.8,
      Some(7), "media/audio/lofi12.mp3", 50),     // Categoría: Beats, Stock: 50
    Media(nextId(), "Corto City Rush", "Clip FullHD 30s", MediaType.Video, 11.99, 4.4,
      Some(9), "media/video/city_rush.mp4", 8)    // Categoría: Cortos, Stock: 8 (ÚLTIMAS UNIDADES)
  )

  def all: Vector[Media] = data.sortBy(-_.downloads)
  def find(id: Long): Option[Media] = data.find(_.id == id)
  
  // Búsqueda de productos
  def search(query: String): Vector[Media] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else data.filter { m =>
      m.title.toLowerCase.contains(q) ||
      m.description.toLowerCase.contains(q) ||
      m.id.toString == q
    }.sortBy(-_.downloads)
  }
  
  // Filtrar por tipo
  def filterByType(mtype: MediaType): Vector[Media] = 
    data.filter(_.mtype == mtype).sortBy(-_.downloads)
  
  // Filtrar por categoría (incluye subcategorías)
  def filterByCategory(categoryId: Long): Vector[Media] = {
    val categoryIds = categoryId +: CategoryRepo.getAllDescendants(categoryId).map(_.id)
    data.filter(m => m.categoryId.exists(categoryIds.contains)).sortBy(-_.downloads)
  }
  
  // Búsqueda combinada: tipo + categoría + texto
  def searchAdvanced(
    query: Option[String] = None,
    mtype: Option[MediaType] = None,
    categoryId: Option[Long] = None
  ): Vector[Media] = {
    var results = data
    
    // Filtrar por tipo
    mtype.foreach { t =>
      results = results.filter(_.mtype == t)
    }
    
    // Filtrar por categoría (incluye subcategorías)
    categoryId.foreach { cid =>
      val categoryIds = cid +: CategoryRepo.getAllDescendants(cid).map(_.id)
      results = results.filter(m => m.categoryId.exists(categoryIds.contains))
    }
    
    // Filtrar por texto
    query.foreach { q =>
      val qLower = q.toLowerCase.trim
      if (qLower.nonEmpty) {
        results = results.filter { m =>
          m.title.toLowerCase.contains(qLower) ||
          m.description.toLowerCase.contains(qLower) ||
          m.id.toString == qLower
        }
      }
    }
    
    results.sortBy(-_.downloads)
  }
  
  // Estadísticas REALES basadas en DownloadRepo
  def totalProducts: Int = data.size
  def totalRevenue: BigDecimal = DownloadRepo.totalRevenue
  def averagePrice: BigDecimal = if (data.nonEmpty) data.map(_.price).sum / data.size else BigDecimal(0)
  def averageRating: Double = if (data.nonEmpty) data.map(_.rating).sum / data.size else 0.0
  def totalDownloads: Int = DownloadRepo.totalDownloads
  def countByType(mtype: MediaType): Int = data.count(_.mtype == mtype)
  def topProducts(limit: Int = 5): Vector[Media] = data.sortBy(-_.downloads).take(limit)
  
  // CRUD operations for admin
  def add(title: String, description: String, mtype: MediaType, price: BigDecimal, categoryId: Option[Long], assetPath: String, stock: Int = 0): Media = {
    val media = Media(nextId(), title, description, mtype, price, 0.0, categoryId, assetPath, stock)
    data = data :+ media
    media
  }
  
  def update(id: Long, title: String, description: String, mtype: MediaType, price: BigDecimal, categoryId: Option[Long], assetPath: String, stock: Int): Option[Media] = {
    data.find(_.id == id).map { oldMedia =>
      val updated = oldMedia.copy(title = title, description = description, mtype = mtype, price = price, categoryId = categoryId, assetPath = assetPath, stock = stock)
      data = data.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  // Reducir stock (SYNCHRONIZED para evitar race conditions)
  def reduceStock(id: Long, quantity: Int): Either[String, Media] = this.synchronized {
    data.find(_.id == id) match {
      case None => Left("Producto no encontrado")
      case Some(media) =>
        if (media.stock < quantity) {
          Left(s"Stock insuficiente. Solo quedan ${media.stock} unidades disponibles")
        } else {
          val updated = media.copy(stock = media.stock - quantity)
          data = data.filterNot(_.id == id) :+ updated
          Right(updated)
        }
    }
  }
  
  // Incrementar stock (para devoluciones o reabastecimiento)
  def addStock(id: Long, quantity: Int): Option[Media] = this.synchronized {
    data.find(_.id == id).map { media =>
      val updated = media.copy(stock = media.stock + quantity)
      data = data.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  def delete(id: Long): Boolean = {
    val sizeBefore = data.size
    data = data.filterNot(_.id == id)
    data.size < sizeBefore
  }
}