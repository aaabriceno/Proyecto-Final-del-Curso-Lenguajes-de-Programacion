package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.{Updates, Sorts}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

case class Receipt(
  id: Long,
  orderId: Long,
  userId: Long,
  series: String,
  number: Long,
  hash: String,
  qrData: String,
  htmlPath: Option[String] = None,
  pdfPath: Option[String] = None,
  createdAt: LocalDateTime = LocalDateTime.now()
)

object ReceiptRepo {

  private val collection = MongoConnection.Collections.receipts

  private def nextId(): Long = synchronized {
    val doc = Await.result(
      collection.find().sort(Sorts.descending("_id")).first().toFuture(),
      5.seconds
    )
    val currentMax = Option(doc)
      .flatMap(d => Try(d.getLong("_id")).toOption)
      .map(_.longValue())
      .getOrElse(0L)
    currentMax + 1L
  }

  private def toEpoch(ldt: LocalDateTime): Long =
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli

  private def fromEpoch(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

  private def docToReceipt(doc: Document): Receipt = {
    Receipt(
      id = doc.getLong("_id"),
      orderId = doc.getLong("orderId"),
      userId = doc.getLong("userId"),
      series = doc.getString("series"),
      number = doc.getLong("number"),
      hash = doc.getString("hash"),
      qrData = doc.getString("qrData"),
      htmlPath = Option(doc.getString("htmlPath")),
      pdfPath = Option(doc.getString("pdfPath")),
      createdAt = fromEpoch(doc.getLong("createdAt"))
    )
  }

  private def receiptToDoc(receipt: Receipt): Document = {
    var doc = Document(
      "_id" -> receipt.id,
      "orderId" -> receipt.orderId,
      "userId" -> receipt.userId,
      "series" -> receipt.series,
      "number" -> receipt.number,
      "hash" -> receipt.hash,
      "qrData" -> receipt.qrData,
      "createdAt" -> toEpoch(receipt.createdAt)
    )
    receipt.htmlPath.foreach(path => doc = doc + ("htmlPath" -> path))
    receipt.pdfPath.foreach(path => doc = doc + ("pdfPath" -> path))
    doc
  }

  def create(orderId: Long, userId: Long, series: String, hash: String, qrData: String, issuedAt: LocalDateTime): Receipt = synchronized {
    val id = nextId()
    val receipt = Receipt(id, orderId, userId, series, number = id, hash, qrData, createdAt = issuedAt)
    Await.result(collection.insertOne(receiptToDoc(receipt)).toFuture(), 5.seconds)
    receipt
  }

  def updateAssetPaths(id: Long, htmlPath: Option[String], pdfPath: Option[String]): Receipt = synchronized {
    val updates = scala.collection.mutable.ListBuffer.empty[org.mongodb.scala.bson.conversions.Bson]
    htmlPath.foreach(path => updates += Updates.set("htmlPath", path))
    pdfPath.foreach(path => updates += Updates.set("pdfPath", path))

    if (updates.nonEmpty) {
      val ops = updates.toList
      Await.result(collection.updateOne(equal("_id", id), Updates.combine(ops: _*)).toFuture(), 5.seconds)
    }
    findById(id).get
  }

  def findById(id: Long): Option[Receipt] = {
    val doc = Await.result(collection.find(equal("_id", id)).first().toFuture(), 5.seconds)
    Option(doc).map(docToReceipt)
  }

  def findByOrder(orderId: Long): Option[Receipt] = {
    val doc = Await.result(collection.find(equal("orderId", orderId)).first().toFuture(), 5.seconds)
    Option(doc).map(docToReceipt)
  }

  def findByHash(hash: String): Option[Receipt] = {
    val doc = Await.result(collection.find(equal("hash", hash)).first().toFuture(), 5.seconds)
    Option(doc).map(docToReceipt)
  }
}
