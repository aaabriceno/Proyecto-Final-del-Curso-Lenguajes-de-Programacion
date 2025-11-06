package models

import java.time.LocalDateTime

sealed trait NotificationType { def asString: String }
object NotificationType {
  case object BalanceApproved extends NotificationType { val asString = "balance_approved" }
  case object BalanceRejected extends NotificationType { val asString = "balance_rejected" }
  case object PurchaseSuccess extends NotificationType { val asString = "purchase_success" }
  case object Info extends NotificationType { val asString = "info" }

  def from(s: String): NotificationType = s.toLowerCase match {
    case "balance_approved" => BalanceApproved
    case "balance_rejected" => BalanceRejected
    case "purchase_success" => PurchaseSuccess
    case _ => Info
  }
}

case class Notification(
  id: Long,
  userId: Long,
  message: String,
  notificationType: NotificationType,
  read: Boolean = false,
  createdAt: LocalDateTime = LocalDateTime.now()
)

object NotificationRepo {
  private var notifications = Vector[Notification]()
  private var nextId: Long = 1

  // Crear notificación
  def create(userId: Long, message: String, notificationType: NotificationType): Notification = {
    val notification = Notification(nextId, userId, message, notificationType, false, LocalDateTime.now())
    notifications = notifications :+ notification
    nextId += 1
    notification
  }

  // Obtener notificaciones no leídas de un usuario
  def getUnread(userId: Long): Vector[Notification] = {
    notifications.filter(n => n.userId == userId && !n.read).sortBy(_.createdAt).reverse
  }

  // Obtener todas las notificaciones de un usuario
  def getByUser(userId: Long, limit: Int = 50): Vector[Notification] = {
    notifications.filter(_.userId == userId).sortBy(_.createdAt).reverse.take(limit)
  }

  // Contar notificaciones no leídas
  def countUnread(userId: Long): Int = {
    notifications.count(n => n.userId == userId && !n.read)
  }

  // Marcar notificación como leída
  def markAsRead(notificationId: Long, userId: Long): Boolean = {
    notifications.find(n => n.id == notificationId && n.userId == userId) match {
      case Some(notification) =>
        val updated = notification.copy(read = true)
        notifications = notifications.filterNot(_.id == notificationId) :+ updated
        true
      case None => false
    }
  }

  // Marcar todas las notificaciones de un usuario como leídas
  def markAllAsRead(userId: Long): Int = {
    val unreadNotifications = notifications.filter(n => n.userId == userId && !n.read)
    notifications = notifications.map { n =>
      if (n.userId == userId && !n.read) n.copy(read = true) else n
    }
    unreadNotifications.size
  }

  // Eliminar notificación
  def delete(notificationId: Long, userId: Long): Boolean = {
    val initialSize = notifications.size
    notifications = notifications.filterNot(n => n.id == notificationId && n.userId == userId)
    notifications.size < initialSize
  }

  // Limpiar notificaciones antiguas (más de 30 días)
  def cleanOld(): Int = {
    val thirtyDaysAgo = LocalDateTime.now().minusDays(30)
    val oldNotifications = notifications.filter(_.createdAt.isBefore(thirtyDaysAgo))
    notifications = notifications.filterNot(_.createdAt.isBefore(thirtyDaysAgo))
    oldNotifications.size
  }
}
