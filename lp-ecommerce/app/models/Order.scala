package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime, BsonArray, BsonDocument, BsonBoolean}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

case class OrderItem(
  mediaId: Long,
  title: String,
  quantity: Int,
  unitPrice: BigDecimal,
  discount: BigDecimal,
  netAmount: BigDecimal,
  productType: ProductType,
  isGift: Boolean = false,
  giftRecipient: Option[String] = None,
  giftSender: Option[String] = None
)

case class Order(
  id: Long,
  userId: Long,
  items: Vector[OrderItem],
  totalGross: BigDecimal,
  totalDiscount: BigDecimal,
  totalNet: BigDecimal,
  createdAt: LocalDateTime = LocalDateTime.now()
)

object OrderRepo {

  private val collection = MongoConnection.Collections.orders

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

  private def itemToDoc(item: OrderItem): Document = {
    var doc = Document(
      "mediaId" -> item.mediaId,
      "title" -> item.title,
      "quantity" -> item.quantity,
      "unitPrice" -> item.unitPrice.toDouble,
      "discount" -> item.discount.toDouble,
      "netAmount" -> item.netAmount.toDouble,
      "productType" -> item.productType.asString,
      "isGift" -> item.isGift
    )
    item.giftRecipient.foreach(v => doc = doc + ("giftRecipient" -> v))
    item.giftSender.foreach(v => doc = doc + ("giftSender" -> v))
    doc
  }

  private def docToItem(doc: Document): OrderItem = {
    val isGift = doc.get("isGift").collect { case v: BsonBoolean => v.getValue }.getOrElse(false)

    OrderItem(
      mediaId = readLong(doc, "mediaId"),
      title = doc.getString("title"),
      quantity = doc.getInteger("quantity"),
      unitPrice = BigDecimal(doc.getDouble("unitPrice")),
      discount = BigDecimal(doc.getDouble("discount")),
      netAmount = BigDecimal(doc.getDouble("netAmount")),
      productType = ProductType.from(doc.getString("productType")),
      isGift = isGift,
      giftRecipient = Option(doc.getString("giftRecipient")).filter(_.nonEmpty),
      giftSender = Option(doc.getString("giftSender")).filter(_.nonEmpty)
    )
  }

  private def orderToDoc(order: Order): Document = {
    Document(
      "_id" -> order.id,
      "userId" -> order.userId,
      "items" -> order.items.map(itemToDoc),
      "totalGross" -> order.totalGross.toDouble,
      "totalDiscount" -> order.totalDiscount.toDouble,
      "totalNet" -> order.totalNet.toDouble,
      "createdAt" -> toEpoch(order.createdAt)
    )
  }

  private def docToOrder(doc: Document): Order = {
    val bson = doc.toBsonDocument
    val items = Option(bson.get("items")).collect { case arr: BsonArray => arr }
      .map(_.getValues.asScala.collect {
        case value: BsonDocument => docToItem(Document(value))
      }.toVector)
      .getOrElse(Vector.empty)

    Order(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      items = items,
      totalGross = BigDecimal(doc.getDouble("totalGross")),
      totalDiscount = BigDecimal(doc.getDouble("totalDiscount")),
      totalNet = BigDecimal(doc.getDouble("totalNet")),
      createdAt = fromEpoch(readLong(doc, "createdAt"))
    )
  }

  def create(userId: Long, items: Vector[OrderItem]): Order = synchronized {
    val totalGross = items.map(i => i.unitPrice * i.quantity).sum
    val totalDiscount = items.map(_.discount).sum
    val totalNet = items.map(_.netAmount).sum
    val order = Order(nextId(), userId, items, totalGross, totalDiscount, totalNet, LocalDateTime.now())
    Await.result(collection.insertOne(orderToDoc(order)).toFuture(), 5.seconds)
    order
  }

  def findByUser(userId: Long, limit: Int = 20): Vector[Order] = {
    val docs = Await.result(
      collection.find(equal("userId", userId)).sort(Document("createdAt" -> -1)).limit(limit).toFuture(),
      5.seconds
    )
    docs.map(docToOrder).toVector
  }

  def countAll: Int = {
    Await.result(collection.countDocuments().toFuture(), 5.seconds).toInt
  }
}
