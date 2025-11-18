package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

sealed trait RankingType { def asString: String }

object RankingType {
  case object TopDownloads extends RankingType { val asString = "top_downloads" }
  case object TopProducts  extends RankingType { val asString = "top_products" }
  case object TopRated     extends RankingType { val asString = "top_rated" }

  def from(value: String): RankingType = value match {
    case "top_products" => TopProducts
    case "top_rated"    => TopRated
    case _               => TopDownloads
  }
}

case class RankingEntry(referenceId: Long, position: Int)

case class RankingSnapshot(
  id: Long,
  rankingType: RankingType,
  generatedAt: LocalDateTime,
  entries: Vector[RankingEntry]
)

object RankingRepo {

  private val collection = MongoConnection.Collections.rankings

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

  private def docToSnapshot(doc: Document): RankingSnapshot = {
    val entries = doc.getList("entries", classOf[Document]).toArray.toVector.collect {
      case d: Document => RankingEntry(readLong(d, "referenceId"), d.getInteger("position"))
    }
    RankingSnapshot(
      id = readLong(doc, "_id"),
      rankingType = RankingType.from(doc.getString("rankingType")),
      generatedAt = fromEpoch(readLong(doc, "generatedAt")),
      entries = entries
    )
  }

  private def snapshotToDoc(snapshot: RankingSnapshot): Document = {
    val entries = snapshot.entries.map(e => Document("referenceId" -> e.referenceId, "position" -> e.position))
    Document(
      "_id" -> snapshot.id,
      "rankingType" -> snapshot.rankingType.asString,
      "generatedAt" -> toEpoch(snapshot.generatedAt),
      "entries" -> entries
    )
  }

  def saveSnapshot(rankingType: RankingType, entries: Vector[RankingEntry]): RankingSnapshot = synchronized {
    val snapshot = RankingSnapshot(nextId(), rankingType, LocalDateTime.now(), entries)
    Await.result(collection.insertOne(snapshotToDoc(snapshot)).toFuture(), 5.seconds)
    snapshot
  }

  def latestSnapshot(rankingType: RankingType): Option[RankingSnapshot] = {
    val doc = Await.result(
      collection.find(equal("rankingType", rankingType.asString)).sort(Document("generatedAt" -> -1)).first().toFuture(),
      5.seconds
    )
    Option(doc).map(docToSnapshot)
  }

  def previousSnapshot(rankingType: RankingType): Option[RankingSnapshot] = {
    val docs = Await.result(
      collection.find(equal("rankingType", rankingType.asString)).sort(Document("generatedAt" -> -1)).limit(2).toFuture(),
      5.seconds
    )
    val seq = docs.toSeq
    if (seq.size >= 2) Some(docToSnapshot(seq(1))) else None
  }
}
