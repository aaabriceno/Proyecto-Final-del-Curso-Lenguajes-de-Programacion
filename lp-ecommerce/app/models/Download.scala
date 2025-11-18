package models

import java.time.{LocalDateTime, ZoneId, Instant}
import java.util.UUID
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
 * Representa una compra o descarga realizada por un usuario.
 * Cada registro equivale a una transacción.
 */
case class Download(
  id: Long,
  userId: Long,
  mediaId: Long,
  quantity: Int = 1,                     // Cantidad comprada
  price: BigDecimal,                     // Precio unitario
  discount: BigDecimal = 0,              // Descuento total aplicado
  finalPrice: BigDecimal,                // Precio final total (price * quantity - discount)
  downloadDate: LocalDateTime = LocalDateTime.now(),
  uniqueCode: String = UUID.randomUUID().toString
)

/**
 * Repositorio en memoria de descargas (transacciones).
 * Thread-safe y compatible con Scala 2.13 puro.
 */
object DownloadRepo {

  private val collection = MongoConnection.Collections.downloads

  private def readLong(doc: Document, field: String): Long = doc.get(field) match {
    case Some(value: BsonInt64)   => value.getValue
    case Some(value: BsonInt32)   => value.getValue.toLong
    case Some(value: BsonDouble)  => value.getValue.toLong
    case Some(value: BsonString)  => value.getValue.toLongOption.getOrElse(0L)
    case Some(value: BsonDateTime)=> value.getValue
    case _                        => 0L
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

  private def docToDownload(doc: Document): Download = {
    val downloadDateEpoch = readLong(doc, "downloadDate")
    Download(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      mediaId = readLong(doc, "mediaId"),
      quantity = doc.getInteger("quantity"),
      price = BigDecimal(doc.getDouble("price")),
      discount = BigDecimal(doc.getDouble("discount")),
      finalPrice = BigDecimal(doc.getDouble("finalPrice")),
      downloadDate = fromEpoch(downloadDateEpoch),
      uniqueCode = doc.getString("uniqueCode")
    )
  }

  private def downloadToDoc(download: Download): Document = {
    Document(
      "_id" -> download.id,
      "userId" -> download.userId,
      "mediaId" -> download.mediaId,
      "quantity" -> download.quantity,
      "price" -> download.price.toDouble,
      "discount" -> download.discount.toDouble,
      "finalPrice" -> download.finalPrice.toDouble,
      "downloadDate" -> toEpoch(download.downloadDate),
      "uniqueCode" -> download.uniqueCode
    )
  }

  // ==============================
  // CONSULTAS BÁSICAS
  // ==============================

  def all: Vector[Download] = {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    docs.map(docToDownload).toVector.sortBy(_.downloadDate)(Ordering[LocalDateTime].reverse)
  }

  def findById(id: Long): Option[Download] = {
    val docs = Await.result(collection.find(equal("_id", id)).first().toFuture(), 5.seconds)
    Option(docs).map(docToDownload)
  }

  def findByUserId(userId: Long): Vector[Download] = {
    val docs = Await.result(collection.find(equal("userId", userId)).toFuture(), 5.seconds)
    docs.map(docToDownload).toVector.sortBy(_.downloadDate)(Ordering[LocalDateTime].reverse)
  }

  def findByMediaId(mediaId: Long): Vector[Download] = {
    val docs = Await.result(collection.find(equal("mediaId", mediaId)).toFuture(), 5.seconds)
    docs.map(docToDownload).toVector
  }

  def findByCode(code: String): Option[Download] = {
    val docs = Await.result(collection.find(equal("uniqueCode", code)).first().toFuture(), 5.seconds)
    Option(docs).map(docToDownload)
  }

  def userHasDownloaded(userId: Long, mediaId: Long): Boolean = {
    val count = Await.result(
      collection.countDocuments(and(equal("userId", userId), equal("mediaId", mediaId))).toFuture(),
      5.seconds
    )
    count > 0
  }

  // ==============================
  // CREACIÓN / AGREGADO
  // ==============================

  /** Agrega una nueva compra/descarga */
  def add(
    userId: Long,
    mediaId: Long,
    quantity: Int,
    price: BigDecimal,
    discount: BigDecimal = 0
  ): Download = synchronized {
    val totalPrice = price * quantity
    val finalPrice = (totalPrice - discount).max(BigDecimal(0))
    val uniqueCode = UUID.randomUUID().toString
    val now = LocalDateTime.now()

    val newDownload = Download(nextId(), userId, mediaId, quantity, price, discount, finalPrice, now, uniqueCode)
    Await.result(collection.insertOne(downloadToDoc(newDownload)).toFuture(), 5.seconds)
    newDownload
  }

  // ==============================
  // ESTADÍSTICAS
  // ==============================

  /** Ingresos totales (de todas las descargas) */
  def totalRevenue: BigDecimal =
    all.map(_.finalPrice).sum

  /** Total de unidades descargadas (suma de cantidades) */
  def totalDownloads: Int =
    all.map(_.quantity).sum

  /** Total de transacciones realizadas */
  def totalPurchases: Int = all.size

  /** Ingresos generados por un usuario */
  def revenueByUser(userId: Long): BigDecimal =
    findByUserId(userId).map(_.finalPrice).sum

  /** Cantidad total de descargas por usuario */
  def downloadsByUser(userId: Long): Int =
    findByUserId(userId).map(_.quantity).sum

  /** Top compradores (por ingreso total) */
  def topBuyers(limit: Int = 10): Vector[(Long, BigDecimal)] = {
    all
      .groupBy(_.userId)
      .view
      .mapValues(_.map(_.finalPrice).sum)
      .toVector
      .sortBy(-_._2)
      .take(limit)
  }

  /** Cantidad total de descargas por producto */
  def downloadsByMedia(mediaId: Long): Int =
    findByMediaId(mediaId).map(_.quantity).sum

  /** Ingresos totales generados por un producto */
  def revenueByMedia(mediaId: Long): BigDecimal =
    findByMediaId(mediaId).map(_.finalPrice).sum
}
