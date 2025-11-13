package models

import java.security.MessageDigest
import java.util.Base64
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import scala.concurrent.Await
import scala.concurrent.duration._
import db.MongoConnection

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
 * Repositorio con MongoDB para la gestión de usuarios.
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
  //  📦 Acceso a la colección de MongoDB
  // =====================================================

  private val collection = MongoConnection.Collections.users

  // Generador de IDs
  private def nextId(): Long = synchronized {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    val maxId = if (docs.isEmpty) 0L else {
      docs.map(doc => doc.getLong("_id").toLong).max
    }
    maxId + 1L
  }

  // =====================================================
  //  🔄 Conversión Document ↔ User
  // =====================================================

  private def docToUser(doc: Document): User = {
    User(
      id = doc.getLong("_id"),
      name = doc.getString("name"),
      email = doc.getString("email"),
      phone = doc.getString("phone"),
      passwordHash = doc.getString("password"),
      isAdmin = doc.getBoolean("isAdmin"),
      isActive = doc.getBoolean("isActive"),
      balance = BigDecimal(doc.getDouble("balance")),
      totalSpent = BigDecimal(doc.getDouble("totalSpent"))
    )
  }

  private def userToDoc(user: User): Document = {
    Document(
      "_id" -> user.id,
      "name" -> user.name,
      "email" -> user.email,
      "phone" -> user.phone,
      "password" -> user.passwordHash,
      "isAdmin" -> user.isAdmin,
      "isActive" -> user.isActive,
      "balance" -> user.balance.toDouble,
      "totalSpent" -> user.totalSpent.toDouble
    )
  }

  // =====================================================
  //  🔍 Consultas
  // =====================================================

  def all: Vector[User] = {
    val docs = Await.result(collection.find().toFuture(), 5.seconds)
    docs.map(docToUser).toVector
  }

  def findByEmail(email: String): Option[User] = {
    val result = Await.result(
      collection.find(equal("email", email.trim.toLowerCase)).toFuture(),
      5.seconds
    )
    result.headOption.map(docToUser)
  }

  def findById(id: Long): Option[User] = {
    val result = Await.result(
      collection.find(equal("_id", id)).toFuture(),
      5.seconds
    )
    result.headOption.map(docToUser)
  }

  // =====================================================
  //  ➕ Registro de usuario
  // =====================================================

  def add(name: String, email: String, phone: String, password: String, isAdmin: Boolean = false): User = synchronized {
    val user = User(
      id = nextId(),
      name = name.trim,
      email = email.trim.toLowerCase,
      phone = phone.trim,
      passwordHash = hashPassword(password),
      isAdmin = isAdmin
    )
    
    Await.result(
      collection.insertOne(userToDoc(user)).toFuture(),
      5.seconds
    )
    
    println(s"✅ Usuario creado en MongoDB: ${user.email} (ID: ${user.id})")
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
    findById(id).map { user =>
      val updated = user.copy(isActive = !user.isActive)
      
      Await.result(
        collection.updateOne(
          equal("_id", id),
          set("isActive", updated.isActive)
        ).toFuture(),
        5.seconds
      )
      
      println(s"✅ Usuario ${id} actualizado: isActive = ${updated.isActive}")
      updated
    }
  }

  // =====================================================
  //  💰 Gestión de saldo
  // =====================================================

  /** Añadir saldo a un usuario (usado por admin o BalanceRequestRepo.approve) */
  def addBalance(id: Long, amount: BigDecimal): Option[User] = synchronized {
    findById(id).map { user =>
      val updated = user.copy(balance = user.balance + amount)
      
      Await.result(
        collection.updateOne(
          equal("_id", id),
          set("balance", updated.balance.toDouble)
        ).toFuture(),
        5.seconds
      )
      
      println(s"💰 Saldo añadido a usuario ${id}: +$${amount} = $${updated.balance}")
      updated
    }
  }

  /** Descontar saldo tras una compra */
  def deductBalance(id: Long, amount: BigDecimal): Option[User] = synchronized {
    findById(id).flatMap { user =>
      if (user.balance >= amount) {
        val updated = user.copy(
          balance = user.balance - amount,
          totalSpent = user.totalSpent + amount
        )
        
        Await.result(
          collection.updateOne(
            equal("_id", id),
            combine(
              set("balance", updated.balance.toDouble),
              set("totalSpent", updated.totalSpent.toDouble)
            )
          ).toFuture(),
          5.seconds
        )
        
        println(s"💸 Compra procesada usuario ${id}: -$${amount}, total gastado: $${updated.totalSpent}")
        Some(updated)
      } else {
        println(s"❌ Saldo insuficiente usuario ${id}: tiene $${user.balance}, necesita $${amount}")
        None
      }
    }
  }

  /** Consultar saldo actual */
  def getBalance(id: Long): Option[BigDecimal] =
    findById(id).map(_.balance)

  /** Consultar total gastado (para descuento VIP) */
  def getTotalSpent(id: Long): Option[BigDecimal] =
    findById(id).map(_.totalSpent)
}
