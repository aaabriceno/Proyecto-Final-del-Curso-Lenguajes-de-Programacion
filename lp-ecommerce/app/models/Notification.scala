package models

import java.time.LocalDateTime

/**
 * Tipos de notificaciones que el sistema puede generar.
 */
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
    case _                  => Info
  }
}

/**
 * Representa una notificación enviada a un usuario.
 */
case class Notification(
  id: Long,
  userId: Long,
  message: String,
  notificationType: NotificationType,
  read: Boolean = false,
  createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Repositorio en memoria de notificaciones de usuarios.
 * Thread-safe, funcional y optimizado para consultas frecuentes.
 */
object NotificationRepo {

  private var notifications: Vector[Notification] = Vector.empty
  private var nextId: Long = 1L

  // =========================
  // CREAR / AGREGAR
  // =========================

  def create(userId: Long, message: String, notificationType: NotificationType): Notification = synchronized {
    val n = Notification(nextId, userId, message, notificationType, read = false, createdAt = LocalDateTime.now())
    notifications :+= n
    nextId += 1
    n
  }

  // =========================
  // CONSULTAS
  // =========================

  /** Obtiene las notificaciones no leídas más recientes de un usuario */
  def getUnread(userId: Long): Vector[Notification] =
    notifications.view.filter(n => n.userId == userId && !n.read)
      .toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)

  /** Obtiene todas las notificaciones del usuario (limit configurable) */
  def getByUser(userId: Long, limit: Int = 50): Vector[Notification] =
    notifications.view.filter(_.userId == userId)
      .toVector.sortBy(_.createdAt)(Ordering[LocalDateTime].reverse)
      .take(limit)

  /** Cuenta cuántas notificaciones no leídas tiene un usuario */
  def countUnread(userId: Long): Int =
    notifications.count(n => n.userId == userId && !n.read)

  // =========================
  // ACTUALIZAR ESTADO
  // =========================

  /** Marca una notificación como leída */
  def markAsRead(notificationId: Long, userId: Long): Boolean = synchronized {
    var updatedFlag = false
    notifications = notifications.map { n =>
      if (n.id == notificationId && n.userId == userId && !n.read) {
        updatedFlag = true
        n.copy(read = true)
      } else n
    }
    updatedFlag
  }

  /** Marca todas las notificaciones de un usuario como leídas */
  def markAllAsRead(userId: Long): Int = synchronized {
    val unreadCount = notifications.count(n => n.userId == userId && !n.read)
    if (unreadCount > 0) {
      notifications = notifications.map { n =>
        if (n.userId == userId && !n.read) n.copy(read = true) else n
      }
    }
    unreadCount
  }

  // =========================
  // ELIMINAR
  // =========================

  /** Elimina una notificación específica del usuario */
  def delete(notificationId: Long, userId: Long): Boolean = synchronized {
    val before = notifications.size
    notifications = notifications.filterNot(n => n.id == notificationId && n.userId == userId)
    notifications.size < before
  }

  /** Limpia notificaciones más antiguas de 30 días */
  def cleanOld(): Int = synchronized {
    val threshold = LocalDateTime.now().minusDays(30)
    val old = notifications.filter(_.createdAt.isBefore(threshold))
    notifications = notifications.filterNot(_.createdAt.isBefore(threshold))
    old.size
  }

  // =========================
  // ESTADÍSTICAS (Opcional)
  // =========================

  /** Número total de notificaciones en el sistema */
  def totalCount: Int = notifications.size

  /** Usuarios con más notificaciones no leídas */
  def topUsersWithUnread(limit: Int = 5): Vector[(Long, Int)] =
    notifications
      .filterNot(_.read)
      .groupBy(_.userId)
      .view.mapValues(_.size)
      .toVector
      .sortBy(-_._2)
      .take(limit)
}
