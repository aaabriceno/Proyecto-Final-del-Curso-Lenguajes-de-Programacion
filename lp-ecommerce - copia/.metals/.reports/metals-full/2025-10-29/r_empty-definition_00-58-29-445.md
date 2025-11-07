error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala:scala/package.Vector.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala
empty definition using pc, found symbol in pc: scala/package.Vector.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -Vector.
	 -Vector#
	 -Vector().
	 -scala/Predef.Vector.
	 -scala/Predef.Vector#
	 -scala/Predef.Vector().
offset: 321
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/models/User.scala
text:
```scala
package models

case class User(
  id: Long,
  name: String,
  email: String,
  phone: String,
  passwordHash: String,
  isAdmin: Boolean = false,
  isActive: Boolean = true
)

object UserRepo {
  // memoria: arranca con un admin demo
  private var seq: Long = 1L
  private var users: Vector[User] = Vector@@(
    User(id = seq, name = "Admin", email = "admin@lpstudios.com",
      phone = "999999999", passwordHash = "$2a$10$FQH8pN1jYVJz9r2Y5m6z6uQk7Qw7s2yUqQz8QnF3q6rT4pP4bC0pm", // "admin123"
      isAdmin = true)
  )

  private def nextId(): Long = { seq += 1; seq }

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
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/package.Vector.