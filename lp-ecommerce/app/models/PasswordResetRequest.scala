package models

import java.time.{LocalDateTime, ZoneId, Instant}
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.bson.{BsonInt64, BsonInt32, BsonDouble, BsonString, BsonDateTime}
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

sealed trait PasswordResetStatus { def asString: String }

object PasswordResetStatus {
  case object Pending   extends PasswordResetStatus { val asString = "pending" }
  case object Approved  extends PasswordResetStatus { val asString = "approved" }
  case object Rejected  extends PasswordResetStatus { val asString = "rejected" }
  case object Completed extends PasswordResetStatus { val asString = "completed" }

  def from(value: String): PasswordResetStatus = value.toLowerCase match {
    case "approved"  => Approved
    case "rejected"  => Rejected
    case "completed" => Completed
    case _           => Pending
  }
}

case class PasswordResetRequest(
  id: Long,
  userId: Long,
  status: PasswordResetStatus = PasswordResetStatus.Pending,
  createdAt: LocalDateTime = LocalDateTime.now(),
  reviewedBy: Option[Long] = None,
  reviewDate: Option[LocalDateTime] = None,
  notes: Option[String] = None
)

object PasswordResetRequestRepo {

  private val collection = MongoConnection.Collections.passwordResetRequests

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

  private def docToReq(doc: Document): PasswordResetRequest = {
    PasswordResetRequest(
      id = readLong(doc, "_id"),
      userId = readLong(doc, "userId"),
      status = PasswordResetStatus.from(doc.getString("status")),
      createdAt = fromEpoch(readLong(doc, "createdAt")),
      reviewedBy = doc.get("reviewedBy").map {
        case v: BsonInt64 => v.getValue
        case v: BsonInt32 => v.getValue.toLong
        case v: BsonDouble => v.getValue.toLong
        case v: BsonString => v.getValue.toLongOption.getOrElse(0L)
        case _ => 0L
      }.filter(_ > 0),
      reviewDate = doc.get("reviewDate").map(v => fromEpoch(readLong(doc, "reviewDate"))),
      notes = Option(doc.getString("notes")).filter(_.nonEmpty)
    )
  }

  private def reqToDoc(req: PasswordResetRequest): Document = {
    var doc = Document(
      "_id" -> req.id,
      "userId" -> req.userId,
      "status" -> req.status.asString,
      "createdAt" -> toEpoch(req.createdAt)
    )
    req.reviewedBy.foreach(id => doc = doc + ("reviewedBy" -> id))
    req.reviewDate.foreach(date => doc = doc + ("reviewDate" -> toEpoch(date)))
    req.notes.foreach(n => doc = doc + ("notes" -> n))
    doc
  }

  def create(userId: Long, notes: Option[String]): PasswordResetRequest = synchronized {
    val req = PasswordResetRequest(
      id = nextId(),
      userId = userId,
      status = PasswordResetStatus.Pending,
      createdAt = LocalDateTime.now(),
      notes = notes.filter(_.nonEmpty)
    )
    Await.result(collection.insertOne(reqToDoc(req)).toFuture(), 5.seconds)
    req
  }

  def findPending(): Vector[PasswordResetRequest] = {
    val docs = Await.result(
      collection.find(equal("status", PasswordResetStatus.Pending.asString)).toFuture(),
      5.seconds
    )
    docs.map(docToReq).toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
  }

  def findById(id: Long): Option[PasswordResetRequest] = {
    val docs = Await.result(
      collection.find(equal("_id", id)).toFuture(),
      5.seconds
    )
    docs.headOption.map(docToReq)
  }

  def findApprovedForUser(userId: Long): Option[PasswordResetRequest] = {
    val docs = Await.result(
      collection.find(and(
        equal("userId", userId),
        equal("status", PasswordResetStatus.Approved.asString)
      )).sort(Document("createdAt" -> -1)).first().toFuture(),
      5.seconds
    )
    Option(docs).map(docToReq)
  }

  def markCompleted(id: Long): Unit = synchronized {
    findById(id).foreach { old =>
      val updated = old.copy(status = PasswordResetStatus.Completed)
      Await.result(
        collection.replaceOne(equal("_id", id), reqToDoc(updated)).toFuture(),
        5.seconds
      )
    }
  }

  def updateStatus(id: Long, status: PasswordResetStatus, adminId: Long, notes: Option[String] = None): Option[PasswordResetRequest] = synchronized {
    findById(id).map { old =>
      val updated = old.copy(
        status = status,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now()),
        notes = notes.orElse(old.notes)
      )
      Await.result(
        collection.replaceOne(equal("_id", id), reqToDoc(updated)).toFuture(),
        5.seconds
      )
      updated
    }
  }
}
