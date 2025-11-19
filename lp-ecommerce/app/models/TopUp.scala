package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

case class TopUp(
  id: Long,
  userId: Long,
  adminId: Long,
  amount: BigDecimal,
  createdAt: LocalDateTime = LocalDateTime.now(),
  balanceRequestId: Option[Long] = None
)

object TopUpRepo {

  private val collection = MongoConnection.Collections.topups

  private def readLong(doc: Document, field: String): Long = doc.get(field) match {
    case Some(value: BsonInt64)    => value.getValue
    case Some(value: BsonInt32)    => value.getValue.toLong
    case Some(value: BsonDouble)   => value.getValue.toLong
    case Some(value: BsonString)   => value.getValue.toLongOption.getOrElse(0L)
    case Some(value: BsonDateTime) => value.getValue
    case _                         => 0L
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

  private def docToTopUp(doc: Document): TopUp = {
    TopUp(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      adminId = readLong(doc, "adminId"),
      amount = BigDecimal(doc.getDouble("amount")),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      balanceRequestId = doc.get("balanceRequestId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0)
    )
  }

  private def topUpToDoc(topUp: TopUp): Document = {
    var doc = Document(
      "_id" -> topUp.id,
      "userId" -> topUp.userId,
      "adminId" -> topUp.adminId,
      "amount" -> topUp.amount.toDouble,
      "createdAt" -> toEpoch(topUp.createdAt)
    )
    topUp.balanceRequestId.foreach(id => doc = doc + ("balanceRequestId" -> id))
    doc
  }

  def all: Vector[TopUp] = {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    docs.map(docToTopUp).toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
  }

  def create(userId: Long, adminId: Long, amount: BigDecimal, balanceRequestId: Option[Long]): TopUp = synchronized {
    val topUp = TopUp(nextId(), userId, adminId, amount, LocalDateTime.now(), balanceRequestId)
    Await.result(collection.insertOne(topUpToDoc(topUp)).toFuture(), 5.seconds)
    topUp
  }
}
