package models

import java.time.LocalDateTime

sealed trait RequestStatus
object RequestStatus {
  case object Pending extends RequestStatus
  case object Approved extends RequestStatus
  case object Rejected extends RequestStatus
  
  def from(s: String): RequestStatus = s.toLowerCase match {
    case "pending" => Pending
    case "approved" => Approved
    case "rejected" => Rejected
    case _ => Pending
  }
}

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

object BalanceRequestRepo {
  private var requests: Vector[BalanceRequest] = Vector.empty
  private var nextId = 1L
  
  def all: Vector[BalanceRequest] = requests
  
  def findById(id: Long): Option[BalanceRequest] = 
    requests.find(_.id == id)
  
  def findByUserId(userId: Long): Vector[BalanceRequest] = 
    requests.filter(_.userId == userId).sortBy(_.requestDate).reverse
  
  def findPending: Vector[BalanceRequest] = 
    requests.filter(_.status == RequestStatus.Pending).sortBy(_.requestDate).reverse
  
  def countPending: Int = 
    requests.count(_.status == RequestStatus.Pending)
  
  def add(userId: Long, amount: BigDecimal, paymentMethod: String): BalanceRequest = {
    val request = BalanceRequest(nextId, userId, amount, paymentMethod)
    requests = requests :+ request
    nextId += 1
    request
  }
  
  def approve(id: Long, adminId: Long): Option[BalanceRequest] = {
    requests.find(_.id == id).filter(_.status == RequestStatus.Pending).map { request =>
      // Agregar saldo al usuario
      UserRepo.addBalance(request.userId, request.amount)
      
      // Actualizar solicitud
      val updated = request.copy(
        status = RequestStatus.Approved,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )
      requests = requests.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  def reject(id: Long, adminId: Long): Option[BalanceRequest] = {
    requests.find(_.id == id).filter(_.status == RequestStatus.Pending).map { request =>
      val updated = request.copy(
        status = RequestStatus.Rejected,
        reviewedBy = Some(adminId),
        reviewDate = Some(LocalDateTime.now())
      )
      requests = requests.filterNot(_.id == id) :+ updated
      updated
    }
  }
}
