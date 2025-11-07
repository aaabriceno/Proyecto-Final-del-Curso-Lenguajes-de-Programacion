package models
import org.mindrot.jbcrypt.BCrypt


case class User(
  id: Long,
  name: String,
  email: String,
  phone: String,
  passwordHash: String,
  isAdmin: Boolean = false,
  isActive: Boolean = true,
  balance: BigDecimal = 0,           // Saldo disponible
  totalSpent: BigDecimal = 0         // Total gastado (para descuento 20%)
)

object UserRepo {
  // memoria: arranca con un admin demo
  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  private var users = Vector[User](
    {
      val hash = BCrypt.hashpw("admin123", BCrypt.gensalt(10))
      User(nextId(), "Admin", "admin@lpstudios.com", "999999999", hash, isAdmin = true)
    }
  )

  def all: Vector[User] = users

  def findByEmail(email: String): Option[User] =
    users.find(_.email.equalsIgnoreCase(email.trim))

  def findById(id: Long): Option[User] =
    users.find(_.id == id)

  def add(name: String, email: String, phone: String, passwordHash: String, isAdmin: Boolean = false): User = {
    val u = User(nextId(), name.trim, email.trim, phone.trim, passwordHash, isAdmin = isAdmin)
    users = users :+ u
    u
  }
  
  def toggleActive(id: Long): Boolean = {
    users.find(_.id == id).foreach { user =>
      val updated = user.copy(isActive = !user.isActive)
      users = users.filterNot(_.id == id) :+ updated
    }
    true
  }
  
  // Funciones para manejar saldo
  def addBalance(id: Long, amount: BigDecimal): Option[User] = {
    users.find(_.id == id).map { user =>
      val updated = user.copy(balance = user.balance + amount)
      users = users.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  def deductBalance(id: Long, amount: BigDecimal): Option[User] = {
    users.find(_.id == id).flatMap { user =>
      if (user.balance >= amount) {
        val updated = user.copy(
          balance = user.balance - amount,
          totalSpent = user.totalSpent + amount
        )
        users = users.filterNot(_.id == id) :+ updated
        Some(updated)
      } else None
    }
  }
  
  def getBalance(id: Long): Option[BigDecimal] = 
    users.find(_.id == id).map(_.balance)
    
  def getTotalSpent(id: Long): Option[BigDecimal] = 
    users.find(_.id == id).map(_.totalSpent)
}
