package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

case class PasswordResetCode(
  id: Long,
  userId: Long,
  code: String,
  createdAt: LocalDateTime = LocalDateTime.now(),
  expiresAt: LocalDateTime,
  used: Boolean = false
)

object PasswordResetCodeRepo {

  private val collection = MongoConnection.Collections.passwordResetCodes

  private def readLong(doc: Document, field: String): Long = doc.get(field) match {
    case Some(value: BsonInt64)    => value.getValue
    case Some(value: BsonInt32)    => value.getValue.toLong
    case Some(value: BsonDouble)   => value.getValue.toLong
    case Some(value: BsonString)   => value.getValue.toLongOption.getOrElse(0L)
    case Some(value: BsonDateTime) => value.getValue
    case _                         => 0L
  }

  private def toEpoch(ldt: LocalDateTime): Long =
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli

  private def fromEpoch(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

  private def nextId(): Long = synchronized {
    val doc = Await.result(
      collection.find().sort(Document("_id" -> -1)).first().toFuture(),
      5.seconds
    )
    val currentMax = Option(doc).map(d => readLong(d, "_id")).getOrElse(0L)
    currentMax + 1L
  }

  private def docToCode(doc: Document): PasswordResetCode = {
    PasswordResetCode(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      code = doc.getString("code"),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      expiresAt = fromEpoch(readLong(doc, "expiresAt")),
      used = doc.getBoolean("used", false)
    )
  }

  private def codeToDoc(code: PasswordResetCode): Document = {
    Document(
      "_id" -> code.id,
      "userId" -> code.userId,
      "code" -> code.code,
      "createdAt" -> toEpoch(code.createdAt),
      "expiresAt" -> toEpoch(code.expiresAt),
      "used" -> code.used
    )
  }

  private def generateCode(): String = {
    val n = 100000 + Random.nextInt(900000) // 6 dígitos
    n.toString
  }

  def createForUser(userId: Long, minutesValid: Int = 3): PasswordResetCode = synchronized {
    val now = LocalDateTime.now()
    val code = PasswordResetCode(
      id = nextId(),
      userId = userId,
      code = generateCode(),
      createdAt = now,
      expiresAt = now.plusMinutes(minutesValid.toLong),
      used = false
    )
    Await.result(collection.insertOne(codeToDoc(code)).toFuture(), 5.seconds)
    code
  }

  def findValid(userId: Long, code: String): Option[PasswordResetCode] = {
    val now = LocalDateTime.now()
    val docs = Await.result(
      collection.find(
        and(
          equal("userId", userId),
          equal("code", code),
          equal("used", false)
        )
      ).sort(Document("createdAt" -> -1)).toFuture(),
      5.seconds
    )
    docs.headOption.map(docToCode).filter(_.expiresAt.isAfter(now))
  }

  def markUsed(id: Long): Unit = synchronized {
    Await.result(
      collection.updateOne(
        equal("_id", id),
        set("used", true)
      ).toFuture(),
      5.seconds
    )
  }
}

