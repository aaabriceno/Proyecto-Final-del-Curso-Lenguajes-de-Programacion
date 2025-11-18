package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.ReplaceOptions
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

case class CartEntry(
  userId: Long,
  mediaId: Long,
  quantity: Int,
  dateAdded: LocalDateTime = LocalDateTime.now()
)

object CartRepo {

  private val collection = MongoConnection.Collections.carts

  private def key(userId: Long, mediaId: Long): String = s"${userId}_${mediaId}"

  private def toEpoch(ldt: LocalDateTime): Long =
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli

  private def fromEpoch(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

  private def docToEntry(doc: Document): CartEntry = {
    CartEntry(
      userId = doc.getLong("userId"),
      mediaId = doc.getLong("mediaId"),
      quantity = doc.getInteger("quantity"),
      dateAdded = fromEpoch(doc.getLong("dateAdded"))
    )
  }

  private def entryToDoc(entry: CartEntry): Document = {
    Document(
      "_id" -> key(entry.userId, entry.mediaId),
      "userId" -> entry.userId,
      "mediaId" -> entry.mediaId,
      "quantity" -> entry.quantity,
      "dateAdded" -> toEpoch(entry.dateAdded)
    )
  }

  def entriesForUser(userId: Long): Vector[CartEntry] = {
    val docs = Await.result(
      collection.find(equal("userId", userId)).toFuture(),
      5.seconds
    )
    docs.map(docToEntry).toVector.sortBy(_.dateAdded)
  }

  def entriesWithMedia(userId: Long): Vector[(CartEntry, Media)] =
    entriesForUser(userId).flatMap { entry =>
      MediaRepo.find(entry.mediaId).map(media => (entry, media))
    }

  def addOrIncrement(userId: Long, mediaId: Long, quantity: Int): Either[String, CartEntry] = synchronized {
    if (quantity <= 0)
      Left("La cantidad debe ser mayor a 0")
    else {
      MediaRepo.find(mediaId) match {
        case None => Left("Producto no encontrado")
        case Some(_) =>
          val existingDoc = Await.result(
            collection.find(equal("_id", key(userId, mediaId))).first().toFuture(),
            5.seconds
          )
          val existing = Option(existingDoc).map(docToEntry)
          val entry = existing match {
            case Some(e) => e.copy(quantity = e.quantity + quantity)
            case None    => CartEntry(userId, mediaId, quantity, LocalDateTime.now())
          }

          Await.result(
            collection.replaceOne(
              equal("_id", key(userId, mediaId)),
              entryToDoc(entry),
              ReplaceOptions().upsert(true)
            ).toFuture(),
            5.seconds
          )

          Right(entry)
      }
    }
  }

  def setQuantity(userId: Long, mediaId: Long, quantity: Int): Either[String, CartEntry] = synchronized {
    if (quantity <= 0) {
      remove(userId, mediaId)
      Left("Cantidad inválida")
    } else {
      val existing = find(userId, mediaId)
      val entry = existing match {
        case Some(e) => e.copy(quantity = quantity)
        case None    => CartEntry(userId, mediaId, quantity, LocalDateTime.now())
      }

      Await.result(
        collection.replaceOne(
          equal("_id", key(userId, mediaId)),
          entryToDoc(entry),
          ReplaceOptions().upsert(true)
        ).toFuture(),
        5.seconds
      )

      Right(entry)
    }
  }

  def find(userId: Long, mediaId: Long): Option[CartEntry] = {
    val doc = Await.result(
      collection.find(equal("_id", key(userId, mediaId))).first().toFuture(),
      5.seconds
    )
    Option(doc).map(docToEntry)
  }

  def remove(userId: Long, mediaId: Long): Boolean = synchronized {
    val result = Await.result(
      collection.deleteOne(equal("_id", key(userId, mediaId))).toFuture(),
      5.seconds
    )
    result.getDeletedCount > 0
  }

  def clear(userId: Long): Unit = synchronized {
    Await.result(
      collection.deleteMany(equal("userId", userId)).toFuture(),
      5.seconds
    )
  }

  def total(userId: Long): BigDecimal =
    entriesWithMedia(userId).map { case (entry, media) => media.price * entry.quantity }.sum

  def itemCount(userId: Long): Int =
    entriesForUser(userId).map(_.quantity).sum
}
