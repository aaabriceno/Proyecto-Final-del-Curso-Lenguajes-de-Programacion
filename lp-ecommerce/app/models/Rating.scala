package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

case class Rating(
  id: Long,
  userId: Long,
  mediaId: Long,
  score: Int,
  createdAt: LocalDateTime = LocalDateTime.now(),
  updatedAt: LocalDateTime = LocalDateTime.now()
)

object RatingRepo {

  private val collection = MongoConnection.Collections.ratings

  private def readLong(doc: Document, field: String): Long = doc.get(field) match {
    case Some(value: BsonInt64)    => value.getValue
    case Some(value: BsonInt32)    => value.getValue.toLong
    case Some(value: BsonDouble)   => value.getValue.toLong
    case Some(value: BsonString)   => Try(value.getValue.toLong).getOrElse(0L)
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

  private def docToRating(doc: Document): Rating = {
    Rating(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      mediaId = readLong(doc, "mediaId"),
      score = doc.getInteger("score"),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      updatedAt = fromEpoch(readLong(doc, "updatedAt"))
    )
  }

  private def ratingToDoc(r: Rating): Document = {
    Document(
      "_id" -> r.id,
      "userId" -> r.userId,
      "mediaId" -> r.mediaId,
      "score" -> r.score,
      "createdAt" -> toEpoch(r.createdAt),
      "updatedAt" -> toEpoch(r.updatedAt)
    )
  }

  def findByMedia(mediaId: Long): Vector[Rating] = {
    val docs = Await.result(collection.find(equal("mediaId", mediaId)).toFuture(), 5.seconds)
    docs.map(docToRating).toVector
  }

  def findByUser(userId: Long): Vector[Rating] = {
    val docs = Await.result(collection.find(equal("userId", userId)).toFuture(), 5.seconds)
    docs.map(docToRating).toVector
  }

  def find(userId: Long, mediaId: Long): Option[Rating] = {
    val doc = Await.result(collection.find(and(equal("userId", userId), equal("mediaId", mediaId))).first().toFuture(), 5.seconds)
    Option(doc).map(docToRating)
  }

  def averageScore(mediaId: Long): Double = {
    val ratings = findByMedia(mediaId)
    if (ratings.isEmpty) 0.0
    else ratings.map(_.score).sum.toDouble / ratings.size
  }

  def save(userId: Long, mediaId: Long, score: Int): Rating = synchronized {
    require(score >= 1 && score <= 10, "La nota debe estar entre 1 y 10")
    val existing = find(userId, mediaId)
    val now = LocalDateTime.now()

    val rating = existing match {
      case Some(r) =>
        val updated = r.copy(score = score, updatedAt = now)
        Await.result(collection.replaceOne(and(equal("userId", userId), equal("mediaId", mediaId)), ratingToDoc(updated)).toFuture(), 5.seconds)
        updated
      case None =>
        val created = Rating(nextId(), userId, mediaId, score, now, now)
        Await.result(collection.insertOne(ratingToDoc(created)).toFuture(), 5.seconds)
        created
    }

    val avg = averageScore(mediaId)
    MediaRepo.updateRating(mediaId, avg)
    rating
  }
}
