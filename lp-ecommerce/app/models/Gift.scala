package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime, BsonBoolean}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

case class Gift(
  id: Long,
  fromUserId: Long,
  toUserId: Long,
  mediaId: Long,
  originalPrice: BigDecimal,
  pricePaid: BigDecimal,
  discountApplied: BigDecimal,
  message: Option[String] = None,
  createdAt: LocalDateTime = LocalDateTime.now(),
  claimed: Boolean = false,
  claimedAt: Option[LocalDateTime] = None
)

object GiftRepo {

  private val collection = MongoConnection.Collections.gifts

  private def readLong(doc: Document, field: String): Long = doc.get(field) match {
    case Some(value: BsonInt64)    => value.getValue
    case Some(value: BsonInt32)    => value.getValue.toLong
    case Some(value: BsonDouble)   => value.getValue.toLong
    case Some(value: BsonString)   => value.getValue.toLongOption.getOrElse(0L)
    case Some(value: BsonDateTime) => value.getValue
    case _                         => 0L
  }

  private def readBoolean(doc: Document, field: String): Boolean = doc.get(field) match {
    case Some(value: BsonBoolean) => value.getValue
    case Some(value: BsonInt32)   => value.getValue != 0
    case Some(value: BsonInt64)   => value.getValue != 0L
    case Some(value: BsonString)  => value.getValue.equalsIgnoreCase("true")
    case _                        => false
  }

  private def nextId(): Long = synchronized {
    val doc = Await.result(
      collection.find().sort(Document("_id" -> -1)).first().toFuture(),
      5.seconds
    )

    val currentMax = Option(doc).map(d => readLong(d, "_id")).getOrElse(0L)
    currentMax + 1L
  }

  private def toEpoch(ldt: LocalDateTime): Long =
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli

  private def fromEpoch(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

  private def docToGift(doc: Document): Gift = {
    val claimedFlag = readBoolean(doc, "claimed")
    val claimedAt = doc.get("claimedAt").flatMap {
      case dt: BsonDateTime => Some(fromEpoch(dt.getValue))
      case num: BsonInt64   => Some(fromEpoch(num.getValue))
      case num: BsonInt32   => Some(fromEpoch(num.getValue.toLong))
      case str: BsonString  => str.getValue.toLongOption.map(fromEpoch)
      case _ => None
    }

    Gift(
      id = readLong(doc, "_id"),
      fromUserId = readLong(doc, "fromUserId"),
      toUserId = readLong(doc, "toUserId"),
      mediaId = readLong(doc, "mediaId"),
      originalPrice = BigDecimal(doc.getDouble("originalPrice")),
      pricePaid = BigDecimal(doc.getDouble("pricePaid")),
      discountApplied = BigDecimal(doc.getDouble("discountApplied")),
      message = Option(doc.getString("message")).filter(_.nonEmpty),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      claimed = claimedFlag,
      claimedAt = claimedAt
    )
  }

  private def giftToDoc(gift: Gift): Document = {
    val base = Document(
      "_id" -> gift.id,
      "fromUserId" -> gift.fromUserId,
      "toUserId" -> gift.toUserId,
      "mediaId" -> gift.mediaId,
      "originalPrice" -> gift.originalPrice.toDouble,
      "pricePaid" -> gift.pricePaid.toDouble,
      "discountApplied" -> gift.discountApplied.toDouble,
      "createdAt" -> toEpoch(gift.createdAt),
      "claimed" -> gift.claimed
    )

    val withMessage = gift.message.map(msg => base + ("message" -> msg)).getOrElse(base)
    gift.claimedAt.map(date => withMessage + ("claimedAt" -> toEpoch(date))).getOrElse(withMessage)
  }

  def pendingFor(userId: Long): Vector[Gift] = {
    val docs = Await.result(collection.find(and(equal("toUserId", userId), equal("claimed", false))).toFuture(), 5.seconds)
    docs.map(docToGift).toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
  }

  def sentBy(userId: Long): Vector[Gift] = {
    val docs = Await.result(collection.find(equal("fromUserId", userId)).toFuture(), 5.seconds)
    docs.map(docToGift).toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
  }

  def find(id: Long): Option[Gift] = {
    val doc = Await.result(collection.find(equal("_id", id)).first().toFuture(), 5.seconds)
    Option(doc).map(docToGift)
  }

  def create(fromUserId: Long, toUserId: Long, mediaId: Long, originalPrice: BigDecimal, pricePaid: BigDecimal, discount: BigDecimal, message: Option[String]): Gift = synchronized {
    val now = LocalDateTime.now()
    val gift = Gift(nextId(), fromUserId, toUserId, mediaId, originalPrice, pricePaid, discount, message.filter(_.nonEmpty), now)
    Await.result(collection.insertOne(giftToDoc(gift)).toFuture(), 5.seconds)
    gift
  }

  def markClaimed(giftId: Long): Option[Gift] = synchronized {
    find(giftId).map { gift =>
      if (gift.claimed) gift
      else {
        val updated = gift.copy(claimed = true, claimedAt = Some(LocalDateTime.now()))
        Await.result(collection.replaceOne(equal("_id", giftId), giftToDoc(updated)).toFuture(), 5.seconds)
        updated
      }
    }
  }
}
