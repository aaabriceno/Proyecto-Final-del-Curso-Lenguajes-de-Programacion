error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -java/util/Base64.getEncoder.encodeToString.
	 -java/util/Base64.getEncoder.encodeToString#
	 -java/util/Base64.getEncoder.encodeToString().
	 -Base64.getEncoder.encodeToString.
	 -Base64.getEncoder.encodeToString#
	 -Base64.getEncoder.encodeToString().
	 -scala/Predef.Base64.getEncoder.encodeToString.
	 -scala/Predef.Base64.getEncoder.encodeToString#
	 -scala/Predef.Base64.getEncoder.encodeToString().
offset: 655
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala
text:
```scala
package models

import java.security.MessageDigest
import java.util.Base64

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
  
  // Funciones de hash (sin BCrypt - solo SHA-256)
  private def hashPassword(password: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(password.getBytes("UTF-8"))
    Base64.getEncoder.@@encodeToString(hash)
  }
  
  private def verifyPassword(password: String, hash: String): Boolean = {
    hashPassword(password) == hash
  }
  
  // memoria: arranca con un admin demo
  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  private var users = Vector[User](
    User(nextId(), "Admin", "admin@lpstudios.com", "999999999", hashPassword("admin123"), isAdmin = true)
  )

  def all: Vector[User] = users

  def findByEmail(email: String): Option[User] =
    users.find(_.email.equalsIgnoreCase(email.trim))

  def findById(id: Long): Option[User] =
    users.find(_.id == id)

  def add(name: String, email: String, phone: String, passwordHash: String, isAdmin: Boolean = false): User = {
    val u = User(nextId(), name.trim, email.trim, phone.trim, hashPassword(passwordHash), isAdmin = isAdmin)
    users = users :+ u
    u
  }
  
  // Autenticar usuario
  def authenticate(email: String, password: String): Option[User] = {
    findByEmail(email).filter { user =>
      user.isActive && verifyPassword(password, user.passwordHash)
    }
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

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.