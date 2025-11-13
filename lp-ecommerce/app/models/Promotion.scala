package models

import java.time.LocalDateTime
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import org.mongodb.scala.bson.BsonDateTime
import java.time.{ZoneId, Instant}

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
  
  private val collection = MongoConnection.Collections.promotions

  // ========= CONVERSIONES DOCUMENT <-> PROMOTION =========

  private def nextId(): Long = synchronized {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    val maxId = if (docs.isEmpty) 0L else {
      docs.map(doc => doc.getLong("_id").toLong).max
    }
    maxId + 1L
  }

  private def localDateTimeToEpoch(ldt: LocalDateTime): Long = {
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli
  }

  private def epochToLocalDateTime(epoch: Long): LocalDateTime = {
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())
  }

  private def docToPromotion(doc: Document): Promotion = {
    val targetIdsArray = try {
      doc.getList("targetIds", classOf[java.lang.Long])
        .toArray.map(_.asInstanceOf[java.lang.Long].toLong).toVector
    } catch {
      case _: Exception => Vector.empty[Long]
    }

    Promotion(
      id = doc.getLong("_id"),
      name = doc.getString("name"),
      description = doc.getString("description"),
      discountPercent = doc.getInteger("discountPercent"),
      startDate = epochToLocalDateTime(doc("startDate").asInstanceOf[BsonDateTime].getValue),
      endDate = epochToLocalDateTime(doc("endDate").asInstanceOf[BsonDateTime].getValue),
      targetType = PromotionTarget.from(doc.getString("targetType")),
      targetIds = targetIdsArray,
      isActive = doc.getBoolean("isActive"),
      createdAt = epochToLocalDateTime(doc("createdAt").asInstanceOf[BsonDateTime].getValue)
    )
  }

  private def promotionToDoc(promotion: Promotion): Document = {
    import org.mongodb.scala.bson.{BsonArray, BsonInt64, BsonDocument}
    
    val targetIdsArray = BsonArray(promotion.targetIds.map(id => BsonInt64(id)))
    
    val bsonDoc = new BsonDocument()
    bsonDoc.append("_id", BsonInt64(promotion.id))
    bsonDoc.append("name", org.mongodb.scala.bson.BsonString(promotion.name))
    bsonDoc.append("description", org.mongodb.scala.bson.BsonString(promotion.description))
    bsonDoc.append("discountPercent", org.mongodb.scala.bson.BsonInt32(promotion.discountPercent))
    bsonDoc.append("startDate", BsonDateTime(localDateTimeToEpoch(promotion.startDate)))
    bsonDoc.append("endDate", BsonDateTime(localDateTimeToEpoch(promotion.endDate)))
    bsonDoc.append("targetType", org.mongodb.scala.bson.BsonString(promotion.targetType.asString))
    bsonDoc.append("targetIds", targetIdsArray)
    bsonDoc.append("isActive", org.mongodb.scala.bson.BsonBoolean(promotion.isActive))
    bsonDoc.append("createdAt", BsonDateTime(localDateTimeToEpoch(promotion.createdAt)))
    
    Document(bsonDoc)
  }

  // =============================
  //   CONSULTAS
  // =============================
  def all: Vector[Promotion] = {
    val docs = Await.result(
      collection.find().toFuture(),
      5.seconds
    )
    docs.map(docToPromotion).toVector.sortBy(_.startDate)(Ordering[LocalDateTime].reverse)
  }

  def find(id: Long): Option[Promotion] = {
    val result = Await.result(
      collection.find(equal("_id", id)).toFuture(),
      5.seconds
    )
    result.headOption.map(docToPromotion)
  }

  def getActive: Vector[Promotion] = {
    all.filter(_.isCurrentlyActive)
  }

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
    val promotion = Promotion(nextId(), name, description, safeDiscount,
      startDate, endDate, targetType, targetIds, true)
    
    Await.result(
      collection.insertOne(promotionToDoc(promotion)).toFuture(),
      5.seconds
    )
    println(s"✅ Promoción creada en MongoDB: ${promotion.name} (${promotion.discountPercent}% OFF)")
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
    find(id).map { old =>
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
      Await.result(
        collection.replaceOne(equal("_id", id), promotionToDoc(updated)).toFuture(),
        5.seconds
      )
      println(s"✅ Promoción actualizada en MongoDB: ${updated.name}")
      updated
    }
  }

  def delete(id: Long): Boolean = synchronized {
    find(id) match {
      case Some(promo) =>
        Await.result(
          collection.deleteOne(equal("_id", id)).toFuture(),
          5.seconds
        )
        println(s"🗑️ Promoción eliminada: ${promo.name}")
        true
      case None => false
    }
  }

  def toggleActive(id: Long): Option[Promotion] = synchronized {
    find(id).map { promo =>
      val updated = promo.copy(isActive = !promo.isActive)
      Await.result(
        collection.updateOne(
          equal("_id", id),
          set("isActive", updated.isActive)
        ).toFuture(),
        5.seconds
      )
      println(s"🔄 Promoción ${if (updated.isActive) "activada" else "desactivada"}: ${promo.name}")
      updated
    }
  }

  // =============================
  //   ESTADÍSTICAS
  // =============================
  def countActive: Int = getActive.size

  def countUpcoming: Int = {
    val allPromotions = all
    allPromotions.count(p => p.isActive && LocalDateTime.now().isBefore(p.startDate))
  }

  def countExpired: Int = {
    val allPromotions = all
    allPromotions.count(p => LocalDateTime.now().isAfter(p.endDate))
  }
}
