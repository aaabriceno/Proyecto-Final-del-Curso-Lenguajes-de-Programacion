package models

import java.security.MessageDigest
import java.util.Base64

/**
 * Representa un usuario del sistema (cliente o administrador).
 */
case class User(
  id: Long,
  name: String,
  email: String,
  phone: String,
  passwordHash: String,
  isAdmin: Boolean = false,
  isActive: Boolean = true,
  balance: BigDecimal = 0,        // Saldo disponible
  totalSpent: BigDecimal = 0      // Total gastado (para descuento VIP)
)

/**
 * Repositorio en memoria para la gestión de usuarios.
 * Implementa autenticación, saldo y control de acceso.
 */
object UserRepo {

  // =====================================================
  //  🔐 Hash de contraseñas (SHA-256 con Base64)
  // =====================================================

  private def hashPassword(password: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(password.getBytes("UTF-8"))
    Base64.getEncoder.encodeToString(hash)
  }

  private def verifyPassword(password: String, hash: String): Boolean =
    hashPassword(password) == hash

  // =====================================================
  //  📦 Almacenamiento en memoria
  // =====================================================

  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  private var users: Vector[User] = Vector(
    User(nextId(), "Admin", "admin@lpstudios.com", "999999999", hashPassword("admin123"), isAdmin = true)
  )

  // =====================================================
  //  🔍 Consultas
  // =====================================================

  def all: Vector[User] = users

  def findByEmail(email: String): Option[User] =
    users.find(_.email.equalsIgnoreCase(email.trim))

  def findById(id: Long): Option[User] =
    users.find(_.id == id)

  // =====================================================
  //  ➕ Registro de usuario
  // =====================================================

  def add(name: String, email: String, phone: String, password: String, isAdmin: Boolean = false): User = synchronized {
    val user = User(nextId(), name.trim, email.trim, phone.trim, hashPassword(password), isAdmin = isAdmin)
    users :+= user
    user
  }

  // =====================================================
  //  🔑 Autenticación
  // =====================================================

  def authenticate(email: String, password: String): Option[User] =
    findByEmail(email).filter(u => u.isActive && verifyPassword(password, u.passwordHash))

  // =====================================================
  //  ⚙️ Estado y permisos
  // =====================================================

  def toggleActive(id: Long): Option[User] = synchronized {
    users.find(_.id == id).map { user =>
      val updated = user.copy(isActive = !user.isActive)
      users = users.map(u => if (u.id == id) updated else u)
      updated
    }
  }

  // =====================================================
  //  💰 Gestión de saldo
  // =====================================================

  /** Añadir saldo a un usuario (usado por admin o BalanceRequestRepo.approve) */
  def addBalance(id: Long, amount: BigDecimal): Option[User] = synchronized {
    users.find(_.id == id).map { user =>
      val updated = user.copy(balance = user.balance + amount)
      users = users.map(u => if (u.id == id) updated else u)
      updated
    }
  }

  /** Descontar saldo tras una compra */
  def deductBalance(id: Long, amount: BigDecimal): Option[User] = synchronized {
    users.find(_.id == id).flatMap { user =>
      if (user.balance >= amount) {
        val updated = user.copy(
          balance = user.balance - amount,
          totalSpent = user.totalSpent + amount
        )
        users = users.map(u => if (u.id == id) updated else u)
        Some(updated)
      } else None
    }
  }

  /** Consultar saldo actual */
  def getBalance(id: Long): Option[BigDecimal] =
    users.find(_.id == id).map(_.balance)

  /** Consultar total gastado (para descuento VIP) */
  def getTotalSpent(id: Long): Option[BigDecimal] =
    users.find(_.id == id).map(_.totalSpent)
}
