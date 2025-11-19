package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

sealed trait TransactionType { def asString: String }

object TransactionType {
  case object Purchase extends TransactionType { val asString = "purchase" }
  case object GiftSent extends TransactionType { val asString = "gift_sent" }
  case object GiftClaimed extends TransactionType { val asString = "gift_claimed" }

  def from(value: String): TransactionType = value match {
    case "gift_sent"    => GiftSent
    case "gift_claimed" => GiftClaimed
    case _              => Purchase
  }
}

case class Transaction(
  id: Long,
  transactionType: TransactionType,
  fromUserId: Option[Long],
  toUserId: Option[Long],
  mediaId: Option[Long],
  quantity: Int,
  grossAmount: BigDecimal,
  discount: BigDecimal,
  netAmount: BigDecimal,
  createdAt: LocalDateTime = LocalDateTime.now(),
  referenceId: Option[Long] = None,
  orderId: Option[Long] = None,
  notes: Option[String] = None
)

object TransactionRepo {

  private val collection = MongoConnection.Collections.transactions

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

  private def docToTransaction(doc: Document): Transaction = {
    Transaction(
      id = readLong(doc, "_id"),
      transactionType = TransactionType.from(doc.getString("transactionType")),
      fromUserId = doc.get("fromUserId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0),
      toUserId = doc.get("toUserId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0),
      mediaId = doc.get("mediaId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0),
      quantity = doc.getInteger("quantity", 1),
      grossAmount = BigDecimal(doc.getDouble("grossAmount")),
      discount = BigDecimal(doc.getDouble("discount")),
      netAmount = BigDecimal(doc.getDouble("netAmount")),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      referenceId = doc.get("referenceId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0),
      notes = Option(doc.getString("notes")).filter(_.nonEmpty),
      orderId = doc.get("orderId").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0)
    )
  }

  private def transactionToDoc(tx: Transaction): Document = {
    var doc = Document(
      "_id" -> tx.id,
      "transactionType" -> tx.transactionType.asString,
      "quantity" -> tx.quantity,
      "grossAmount" -> tx.grossAmount.toDouble,
      "discount" -> tx.discount.toDouble,
      "netAmount" -> tx.netAmount.toDouble,
      "createdAt" -> toEpoch(tx.createdAt)
    )

    tx.fromUserId.foreach(id => doc = doc + ("fromUserId" -> id))
    tx.toUserId.foreach(id => doc = doc + ("toUserId" -> id))
    tx.mediaId.foreach(id => doc = doc + ("mediaId" -> id))
    tx.referenceId.foreach(id => doc = doc + ("referenceId" -> id))
    tx.notes.foreach(n => doc = doc + ("notes" -> n))
    tx.orderId.foreach(id => doc = doc + ("orderId" -> id))
    doc
  }

  def all: Vector[Transaction] = {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    docs.map(docToTransaction).toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
  }

  def add(tx: Transaction): Transaction = synchronized {
    Await.result(collection.insertOne(transactionToDoc(tx)).toFuture(), 5.seconds)
    tx
  }

  def create(
    transactionType: TransactionType,
    fromUserId: Option[Long],
    toUserId: Option[Long],
    mediaId: Option[Long],
    quantity: Int,
    grossAmount: BigDecimal,
    discount: BigDecimal,
    referenceId: Option[Long] = None,
    orderId: Option[Long] = None,
    notes: Option[String] = None
  ): Transaction = synchronized {
    val net = (grossAmount - discount).max(BigDecimal(0))
    val tx = Transaction(
      id = nextId(),
      transactionType = transactionType,
      fromUserId = fromUserId,
      toUserId = toUserId,
      mediaId = mediaId,
      quantity = quantity,
      grossAmount = grossAmount,
      discount = discount,
      netAmount = net,
      createdAt = LocalDateTime.now(),
      referenceId = referenceId,
      orderId = orderId,
      notes = notes
    )
    add(tx)
  }

  def findByUser(userId: Long, limit: Int = 20): Vector[Transaction] = {
    val docs = Await.result(
      collection.find(or(equal("fromUserId", userId), equal("toUserId", userId))).sort(Document("createdAt" -> -1)).limit(limit).toFuture(),
      5.seconds
    )
    docs.map(docToTransaction).toVector
  }

  def purchasesWithoutOrder(userId: Long, limit: Int = 50): Vector[Transaction] = {
    val filter = and(
      equal("transactionType", TransactionType.Purchase.asString),
      equal("fromUserId", userId),
      or(equal("orderId", null), exists("orderId", false))
    )
    val docs = Await.result(
      collection.find(filter).sort(Document("createdAt" -> -1)).limit(limit).toFuture(),
      5.seconds
    )
    docs.map(docToTransaction).toVector
  }
}
