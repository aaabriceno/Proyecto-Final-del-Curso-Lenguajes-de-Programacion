package models

import java.time.LocalDateTime

// =======================================
//   Tipos de objetivo de la promoción
// =======================================
sealed trait PromotionTarget { def asString: String }

object PromotionTarget {
  case object Product extends PromotionTarget { val asString = "product" }
  case object Category extends PromotionTarget { val asString = "category" }
  case object MediaType extends PromotionTarget { val asString = "mediatype" }
  case object All extends PromotionTarget { val asString = "all" }

  def from(s: String): PromotionTarget = s.toLowerCase match {
    case "product"    => Product
    case "category"   => Category
    case "mediatype"  => MediaType
    case "all"        => All
    case _            => Product
  }
}

// =======================================
//   Entidad principal de promoción
// =======================================
case class Promotion(
  id: Long,
  name: String,
  description: String,
  discountPercent: Int,
  startDate: LocalDateTime,
  endDate: LocalDateTime,
  targetType: PromotionTarget,
  targetIds: Vector[Long] = Vector.empty,
  isActive: Boolean = true,
  createdAt: LocalDateTime = LocalDateTime.now()
) {

  /** Verifica si la promoción está activa según fecha y estado */
  def isCurrentlyActive: Boolean = {
    val now = LocalDateTime.now()
    isActive && !now.isBefore(startDate) && !now.isAfter(endDate)
  }

  /** Aplica descuento sobre un precio original */
  def applyDiscount(originalPrice: BigDecimal): BigDecimal = {
    val discount = originalPrice * (discountPercent.toDouble / 100)
    (originalPrice - discount).max(BigDecimal(0))
  }

  /** Días restantes antes de expirar */
  def daysRemaining: Long = {
    val now = LocalDateTime.now()
    if (now.isAfter(endDate)) 0
    else java.time.temporal.ChronoUnit.DAYS.between(now, endDate)
  }

  /** Horas restantes (para countdown) */
  def hoursRemaining: Long = {
    val now = LocalDateTime.now()
    if (now.isAfter(endDate)) 0
    else java.time.temporal.ChronoUnit.HOURS.between(now, endDate)
  }

  /** Estado textual de la promoción */
  def status: String = {
    val now = LocalDateTime.now()
    if (!isActive) "PAUSADA"
    else if (now.isBefore(startDate)) "PRÓXIMAMENTE"
    else if (now.isAfter(endDate)) "EXPIRADA"
    else "ACTIVA"
  }
}

// =======================================
//   Repositorio de promociones
// =======================================
object PromotionRepo {
  private var promotions: Vector[Promotion] = Vector.empty
  private var nextId: Long = 1L

  // Bloque de inicialización
  init()

  /** Carga inicial con promociones de ejemplo */
  private def init(): Unit = synchronized {
    val musicPromo = Promotion(
      nextId,
      "Black Friday Música",
      "30% de descuento en toda la música",
      30,
      LocalDateTime.now().minusDays(1),
      LocalDateTime.now().plusDays(5),
      PromotionTarget.Category,
      Vector(1L), // categoría "Música"
      true
    )
    promotions :+= musicPromo
    nextId += 1

    val futurePromo = Promotion(
      nextId,
      "Cyber Monday Videos",
      "50% en todos los videos",
      50,
      LocalDateTime.now().plusDays(7),
      LocalDateTime.now().plusDays(10),
      PromotionTarget.MediaType,
      Vector(2L), // tipo Video
      true
    )
    promotions :+= futurePromo
    nextId += 1
  }

  // =============================
  //   CONSULTAS
  // =============================
  def all: Vector[Promotion] =
    promotions.sortBy(_.startDate)(Ordering[LocalDateTime].reverse)

  def find(id: Long): Option[Promotion] =
    promotions.find(_.id == id)

  def getActive: Vector[Promotion] =
    promotions.view.filter(_.isCurrentlyActive).toVector

  /** Promoción activa aplicable a un producto específico */
  def getActiveForProduct(mediaId: Long): Option[Promotion] =
    getActive.find { p =>
      p.targetType == PromotionTarget.All ||
      (p.targetType == PromotionTarget.Product && p.targetIds.contains(mediaId))
    }

  /** Promoción activa aplicable a una categoría (incluye subcategorías) */
  def getActiveForCategory(categoryId: Long): Option[Promotion] = {
    val categoryIds = categoryId +: CategoryRepo.getAllDescendants(categoryId).map(_.id)
    getActive.find { p =>
      p.targetType == PromotionTarget.All ||
      (p.targetType == PromotionTarget.Category && p.targetIds.exists(categoryIds.contains))
    }
  }

  /** Promoción activa aplicable a un tipo de media */
  def getActiveForMediaType(mtype: MediaType): Option[Promotion] = {
    val typeId = mtype match {
      case MediaType.Image => 0L
      case MediaType.Audio => 1L
      case MediaType.Video => 2L
    }
    getActive.find { p =>
      p.targetType == PromotionTarget.All ||
      (p.targetType == PromotionTarget.MediaType && p.targetIds.contains(typeId))
    }
  }

  /** Determina la mejor promoción disponible para un producto */
  def getBestPromotionFor(media: Media): Option[Promotion] =
    getActiveForProduct(media.id)
      .orElse(media.categoryId.flatMap(getActiveForCategory))
      .orElse(getActiveForMediaType(media.mtype))
      .orElse(getActive.find(_.targetType == PromotionTarget.All))

  // =============================
  //   CRUD ADMIN
  // =============================
  def create(
    name: String,
    description: String,
    discountPercent: Int,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    targetType: PromotionTarget,
    targetIds: Vector[Long]
  ): Promotion = synchronized {
    val safeDiscount = discountPercent.max(0).min(100)
    val promotion = Promotion(nextId, name, description, safeDiscount,
      startDate, endDate, targetType, targetIds, true)
    promotions :+= promotion
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
  ): Option[Promotion] = synchronized {
    promotions.find(_.id == id).map { old =>
      val updated = old.copy(
        name = name,
        description = description,
        discountPercent = discountPercent.max(0).min(100),
        startDate = startDate,
        endDate = endDate,
        targetType = targetType,
        targetIds = targetIds,
        isActive = isActive
      )
      promotions = promotions.map(p => if (p.id == id) updated else p)
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    val before = promotions.size
    promotions = promotions.filterNot(_.id == id)
    promotions.size < before
  }

  def toggleActive(id: Long): Option[Promotion] = synchronized {
    promotions.find(_.id == id).map { promo =>
      val updated = promo.copy(isActive = !promo.isActive)
      promotions = promotions.map(p => if (p.id == id) updated else p)
      updated
    }
  }

  // =============================
  //   ESTADÍSTICAS
  // =============================
  def countActive: Int = getActive.size

  def countUpcoming: Int =
    promotions.count(p => p.isActive && LocalDateTime.now().isBefore(p.startDate))

  def countExpired: Int =
    promotions.count(p => LocalDateTime.now().isAfter(p.endDate))
}
