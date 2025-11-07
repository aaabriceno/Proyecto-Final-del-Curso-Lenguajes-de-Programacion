package models

import java.time.LocalDateTime

// Tipo de objetivo de la promoción
sealed trait PromotionTarget { def asString: String }
object PromotionTarget {
  case object Product extends PromotionTarget { val asString = "product" }      // Producto específico
  case object Category extends PromotionTarget { val asString = "category" }    // Categoría completa
  case object MediaType extends PromotionTarget { val asString = "mediatype" }  // Tipo (Image/Audio/Video)
  case object All extends PromotionTarget { val asString = "all" }              // Todos los productos

  def from(s: String): PromotionTarget = s.toLowerCase match {
    case "product" => Product
    case "category" => Category
    case "mediatype" => MediaType
    case "all" => All
    case _ => Product
  }
}

case class Promotion(
  id: Long,
  name: String,
  description: String,
  discountPercent: Int,           // 10 = 10%, 50 = 50%
  startDate: LocalDateTime,
  endDate: LocalDateTime,
  targetType: PromotionTarget,
  targetIds: Vector[Long] = Vector.empty,  // IDs de productos, categorías o tipos según targetType
  isActive: Boolean = true,       // Admin puede pausar manualmente
  createdAt: LocalDateTime = LocalDateTime.now()
) {
  // Verificar si la promoción está activa (fecha + flag)
  def isCurrentlyActive: Boolean = {
    val now = LocalDateTime.now()
    isActive && !now.isBefore(startDate) && !now.isAfter(endDate)
  }

  // Calcular precio con descuento
  def applyDiscount(originalPrice: BigDecimal): BigDecimal = {
    val discount = originalPrice * (discountPercent.toDouble / 100)
    originalPrice - discount
  }

  // Días restantes
  def daysRemaining: Long = {
    val now = LocalDateTime.now()
    if (now.isAfter(endDate)) 0
    else java.time.temporal.ChronoUnit.DAYS.between(now, endDate)
  }

  // Horas restantes (para countdown)
  def hoursRemaining: Long = {
    val now = LocalDateTime.now()
    if (now.isAfter(endDate)) 0
    else java.time.temporal.ChronoUnit.HOURS.between(now, endDate)
  }

  // Estado: "ACTIVA", "PRÓXIMAMENTE", "EXPIRADA", "PAUSADA"
  def status: String = {
    val now = LocalDateTime.now()
    if (!isActive) "PAUSADA"
    else if (now.isBefore(startDate)) "PRÓXIMAMENTE"
    else if (now.isAfter(endDate)) "EXPIRADA"
    else "ACTIVA"
  }
}

object PromotionRepo {
  private var promotions = Vector[Promotion]()
  private var nextId: Long = 1

  // Seed de ejemplo
  init()
  
  private def init(): Unit = {
    // Promoción activa en categoría "Música"
    val musicPromo = Promotion(
      nextId, 
      "Black Friday Música", 
      "30% de descuento en toda la música",
      30,
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now().plusDays(5),
      PromotionTarget.Category,
      Vector(1L), // ID de categoría Música
      true
    )
    promotions = promotions :+ musicPromo
    nextId += 1

    // Promoción futura
    val futurePromo = Promotion(
      nextId,
      "Cyber Monday Videos",
      "50% en todos los videos",
      50,
      LocalDateTime.now().plusDays(7),
      LocalDateTime.now().plusDays(10),
      PromotionTarget.MediaType,
      Vector(2L), // Asumiendo que 2 = Video
      true
    )
    promotions = promotions :+ futurePromo
    nextId += 1
  }

  // Obtener todas las promociones
  def all: Vector[Promotion] = promotions.sortBy(_.startDate).reverse

  // Buscar por ID
  def find(id: Long): Option[Promotion] = promotions.find(_.id == id)

  // Obtener promociones activas actualmente
  def getActive: Vector[Promotion] = {
    promotions.filter(_.isCurrentlyActive)
  }

  // Obtener promoción activa para un producto específico
  def getActiveForProduct(mediaId: Long): Option[Promotion] = {
    getActive.find { promo =>
      promo.targetType == PromotionTarget.All ||
      (promo.targetType == PromotionTarget.Product && promo.targetIds.contains(mediaId))
    }.headOption
  }

  // Obtener promoción activa para una categoría (incluye subcategorías)
  def getActiveForCategory(categoryId: Long): Option[Promotion] = {
    val categoryIds = categoryId +: CategoryRepo.getAllDescendants(categoryId).map(_.id)
    
    getActive.find { promo =>
      promo.targetType == PromotionTarget.All ||
      (promo.targetType == PromotionTarget.Category && promo.targetIds.exists(categoryIds.contains))
    }.headOption
  }

  // Obtener promoción activa para un tipo de media
  def getActiveForMediaType(mtype: MediaType): Option[Promotion] = {
    val typeId = mtype match {
      case MediaType.Image => 0L
      case MediaType.Audio => 1L
      case MediaType.Video => 2L
    }

    getActive.find { promo =>
      promo.targetType == PromotionTarget.All ||
      (promo.targetType == PromotionTarget.MediaType && promo.targetIds.contains(typeId))
    }.headOption
  }

  // Obtener la mejor promoción para un producto (prioridad: producto > categoría > tipo > all)
  def getBestPromotionFor(media: Media): Option[Promotion] = {
    // 1. Buscar promoción específica del producto
    getActiveForProduct(media.id).orElse(
      // 2. Buscar promoción de su categoría
      media.categoryId.flatMap(getActiveForCategory)
    ).orElse(
      // 3. Buscar promoción de su tipo
      getActiveForMediaType(media.mtype)
    ).orElse(
      // 4. Buscar promoción general (ALL)
      getActive.find(_.targetType == PromotionTarget.All)
    )
  }

  // CRUD operations
  def create(
    name: String,
    description: String,
    discountPercent: Int,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    targetType: PromotionTarget,
    targetIds: Vector[Long]
  ): Promotion = {
    val promotion = Promotion(
      nextId, name, description, discountPercent,
      startDate, endDate, targetType, targetIds, true
    )
    promotions = promotions :+ promotion
    nextId += 1
    promotion
  }

  def update(
    id: Long,
    name: String,
    description: String,
    discountPercent: Int,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    targetType: PromotionTarget,
    targetIds: Vector[Long],
    isActive: Boolean
  ): Option[Promotion] = {
    promotions.find(_.id == id).map { old =>
      val updated = old.copy(
        name = name,
        description = description,
        discountPercent = discountPercent,
        startDate = startDate,
        endDate = endDate,
        targetType = targetType,
        targetIds = targetIds,
        isActive = isActive
      )
      promotions = promotions.filterNot(_.id == id) :+ updated
      updated
    }
  }

  def delete(id: Long): Boolean = {
    val sizeBefore = promotions.size
    promotions = promotions.filterNot(_.id == id)
    promotions.size < sizeBefore
  }

  // Pausar/reanudar promoción
  def toggleActive(id: Long): Option[Promotion] = {
    promotions.find(_.id == id).map { promo =>
      val updated = promo.copy(isActive = !promo.isActive)
      promotions = promotions.filterNot(_.id == id) :+ updated
      updated
    }
  }

  // Estadísticas
  def countActive: Int = getActive.size
  def countUpcoming: Int = promotions.count(p => p.isActive && LocalDateTime.now().isBefore(p.startDate))
  def countExpired: Int = promotions.count(p => LocalDateTime.now().isAfter(p.endDate))
}
