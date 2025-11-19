package models

import java.time.LocalDateTime
import org.mongodb.scala.Document
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._
import org.mongodb.scala.bson.BsonDateTime
import java.time.{ZoneId, Instant}

/** Estados posibles de una solicitud de recarga */
sealed trait RequestStatus {
  def asString: String
}
object RequestStatus {
  case object Pending extends RequestStatus { val asString = "pending" }
  case object Approved extends RequestStatus { val asString = "approved" }
  case object Rejected extends RequestStatus { val asString = "rejected" }

  def from(s: String): RequestStatus = s.toLowerCase match {
    case "pending"  => Pending
    case "approved" => Approved
    case "rejected" => Rejected
    case _          => Pending
  }
}

/** Representa una solicitud de recarga de saldo */
case class BalanceRequest(
  id: Long,
  userId: Long,
  amount: BigDecimal,
  paymentMethod: String,
  status: RequestStatus = RequestStatus.Pending,
  requestDate: LocalDateTime = LocalDateTime.now(),
  reviewedBy: Option[Long] = None,
  reviewDate: Option[LocalDateTime] = None
)

/** Repositorio MongoDB para solicitudes de recarga */
object BalanceRequestRepo {

  private val collection = MongoConnection.Collections.balanceRequests

  // Generador de IDs
  private def nextId(): Long = synchronized {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    val maxId = if (docs.isEmpty) 0L else {
      docs.map(doc => doc.getLong("_id").toLong).max
    }
    maxId + 1L
  }

  // Conversión LocalDateTime ↔ Epoch milliseconds
  private def localDateTimeToEpoch(ldt: LocalDateTime): Long =
    ldt.atZone(ZoneId.systemDefault()).toInstant.toEpochMilli

  private def epochToLocalDateTime(epoch: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault())

  // Conversión Document ↔ BalanceRequest
  private def docToRequest(doc: Document): BalanceRequest = {
    import org.mongodb.scala.bson.BsonDateTime
    
    // Conversión segura de requestDate (campo obligatorio)
    val requestDate: LocalDateTime = try {
      // Usar apply() en lugar de get() para obtener el valor directo
      val rawValue = doc("requestDate")
      rawValue match {
        case bsonDate: BsonDateTime => 
          epochToLocalDateTime(bsonDate.getValue)
        case _ => 
          println(s"⚠️  requestDate corrupto: ${rawValue.getClass.getName}")
          LocalDateTime.now() // Fallback
      }
    } catch {
      case e: Exception => 
        println(s"❌ Error al leer requestDate: ${e.getMessage}")
        LocalDateTime.now() // Fallback
    }
    
    // Conversión segura de reviewDate (campo opcional)
    val reviewDate: Option[LocalDateTime] = try {
      if (doc.containsKey("reviewDate")) {
        val rawValue = doc("reviewDate")
        rawValue match {
          case bsonDate: BsonDateTime => 
            Some(epochToLocalDateTime(bsonDate.getValue))
          case _ => 
            println(s"⚠️  reviewDate corrupto: ${rawValue.getClass.getName}")
            None
        }
      } else {
        None
      }
    } catch {
      case e: Exception => 
        println(s"⚠️  Error al leer reviewDate: ${e.getMessage}")
        None
    }
    
    BalanceRequest(
      id = doc.getLong("_id"),
      userId = doc.getLong("userId"),
      amount = BigDecimal(doc.getDouble("amount")),
      paymentMethod = doc.getString("paymentMethod"),
      status = RequestStatus.from(doc.getString("status")),
      requestDate = requestDate,
      reviewedBy = if (doc.containsKey("reviewedBy")) Some(doc.getLong("reviewedBy")) else None,
      reviewDate = reviewDate
    )
  }

  private def requestToDoc(request: BalanceRequest): Document = {
    import org.mongodb.scala.bson.BsonDateTime
    
    val baseDoc = Document(
      "_id" -> request.id,
      "userId" -> request.userId,
      "amount" -> request.amount.toDouble,
      "paymentMethod" -> request.paymentMethod,
      "status" -> request.status.asString,
      "requestDate" -> BsonDateTime(localDateTimeToEpoch(request.requestDate))
    )
    
    // Agregar campos opcionales solo si existen, sin envolver en Some
    val withReviewedBy = request.reviewedBy match {
      case Some(id) => baseDoc + ("reviewedBy" -> id)
      case None => baseDoc
    }
    
    val withReviewDate = request.reviewDate match {
      case Some(date) => withReviewedBy + ("reviewDate" -> BsonDateTime(localDateTimeToEpoch(date)))
      case None => withReviewedBy
    }
    
    withReviewDate
  }

  def all: Vector[BalanceRequest] = {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    docs.map(docToRequest).toVector.sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)
  }

  def findById(id: Long): Option[BalanceRequest] = {
    val result = Await.result(
      collection.find(equal("_id", id)).toFuture(),
      5.seconds
    )
    result.headOption.map(docToRequest)
  }

  def findByUserId(userId: Long): Vector[BalanceRequest] = {
    val docs = Await.result(
      collection.find(equal("userId", userId)).toFuture(),
      5.seconds
    )
    docs.map(docToRequest).toVector.sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)
  }

  def findPending: Vector[BalanceRequest] = {
    val docs = Await.result(
      collection.find(equal("status", "pending")).toFuture(),
      5.seconds
    )
    docs.map(docToRequest).toVector.sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)
  }

  def countPending: Int = {
    Await.result(
      collection.countDocuments(equal("status", "pending")).toFuture(),
      5.seconds
    ).toInt
  }

  /** Crea una nueva solicitud */
  def add(userId: Long, amount: BigDecimal, paymentMethod: String): BalanceRequest = synchronized {
    val request = BalanceRequest(nextId(), userId, amount, paymentMethod)
    Await.result(
      collection.insertOne(requestToDoc(request)).toFuture(),
      5.seconds
    )
    println(s"✅ Solicitud de saldo creada en MongoDB: User ${userId}, Amount $${amount}")
    request
  }

  /** Aprueba una solicitud de recarga */
  def approve(id: Long, adminId: Long): Option[BalanceRequest] = synchronized {
    findById(id).filter(_.status == RequestStatus.Pending).map { request =>
      // Agregar saldo al usuario
      UserRepo.addBalance(request.userId, request.amount)
      TopUpRepo.create(request.userId, adminId, request.amount, Some(request.id))

      val updated = request.copy(
        status = RequestStatus.Approved,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )

      Await.result(
        collection.replaceOne(equal("_id", id), requestToDoc(updated)).toFuture(),
        5.seconds
      )
      
      println(s"✅ Solicitud ${id} APROBADA por admin ${adminId}")
      updated
    }
  }

  /** Rechaza una solicitud de recarga */
  def reject(id: Long, adminId: Long): Option[BalanceRequest] = synchronized {
    findById(id).filter(_.status == RequestStatus.Pending).map { request =>
      val updated = request.copy(
        status = RequestStatus.Rejected,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )

      Await.result(
        collection.replaceOne(equal("_id", id), requestToDoc(updated)).toFuture(),
        5.seconds
      )
      
      println(s"❌ Solicitud ${id} RECHAZADA por admin ${adminId}")
      updated
    }
  }

  /** Limpia TODAS las solicitudes corruptas de la base de datos (útil para debugging) */
  def deleteAll(): Unit = synchronized {
    println("⚠️  LIMPIANDO todas las solicitudes de balance de MongoDB...")
    Await.result(
      collection.deleteMany(Document()).toFuture(),
      5.seconds
    )
    println("✅ Todas las solicitudes eliminadas correctamente")
  }
}
