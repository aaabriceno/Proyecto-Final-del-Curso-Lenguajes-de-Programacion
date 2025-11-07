error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/BalanceRequest.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/BalanceRequest.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -NotificationRepo.
	 -scala/Predef.NotificationRepo.
offset: 3019
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/BalanceRequest.scala
text:
```scala
package models

import java.time.LocalDateTime

/** Estados posibles de una solicitud de recarga */
sealed trait RequestStatus
object RequestStatus {
  case object Pending extends RequestStatus
  case object Approved extends RequestStatus
  case object Rejected extends RequestStatus

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

/** Repositorio in-memory para solicitudes de recarga */
object BalanceRequestRepo {

  private var requests: Vector[BalanceRequest] = Vector.empty
  private var nextId: Long = 1L

  def all: Vector[BalanceRequest] = requests.sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)

  def findById(id: Long): Option[BalanceRequest] =
    requests.find(_.id == id)

  def findByUserId(userId: Long): Vector[BalanceRequest] =
    requests.filter(_.userId == userId).sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)

  def findPending: Vector[BalanceRequest] =
    requests.filter(_.status == RequestStatus.Pending).sortBy(_.requestDate)(Ordering[LocalDateTime].reverse)

  def countPending: Int =
    requests.count(_.status == RequestStatus.Pending)

  /** Crea una nueva solicitud */
  def add(userId: Long, amount: BigDecimal, paymentMethod: String): BalanceRequest = synchronized {
    val request = BalanceRequest(nextId, userId, amount, paymentMethod)
    requests :+= request
    nextId += 1
    request
  }

  /** Aprueba una solicitud de recarga */
  def approve(id: Long, adminId: Long): Option[BalanceRequest] = synchronized {
    requests.find(_.id == id).filter(_.status == RequestStatus.Pending).map { request =>
      // Agregar saldo al usuario
      UserRepo.addBalance(request.userId, request.amount)

      // Notificar usuario
      NotificationRepo.create(
        request.userId,
        s"✅ Tu solicitud de recarga de $$${request.amount} ha sido APROBADA. El saldo ya está disponible en tu cuenta.",
        NotificationType.BalanceApproved
      )

      val updated = request.copy(
        status = RequestStatus.Approved,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )

      // Actualizar lista
      requests = requests.map(r => if (r.id == id) updated else r)
      updated
    }
  }

  /** Rechaza una solicitud de recarga */
  def reject(id: Long, adminId: Long): Option[BalanceRequest] = synchronized {
    requests.find(_.id == id).filter(_.status == RequestStatus.Pending).map { request =>
      // Notificar usuario
      Notification@@Repo.create(
        request.userId,
        s"❌ Tu solicitud de recarga de $$${request.amount} ha sido RECHAZADA. Por favor contacta al administrador para más información.",
        NotificationType.BalanceRejected
      )

      val updated = request.copy(
        status = RequestStatus.Rejected,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )

      requests = requests.map(r => if (r.id == id) updated else r)
      updated
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.