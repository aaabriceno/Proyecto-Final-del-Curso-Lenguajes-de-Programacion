package models

case class User(
  id: Long,
  name: String,
  email: String,
  phone: String,
  password: String
)

object UserRepo {
  // Vector con mayúscula
  private var users = Vector(
    User(1, "Admin", "admin@gmail.com", "999999999", "12345")
  )

  private var nextId: Long = users.map(_.id).max + 1

  def all: Seq[User] = users

  def findByEmail(email: String): Option[User] = users.find(_.email == email)

  def add(user: User): User = {
    val newUser = user.copy(id = nextId)
    users :+= newUser
    nextId += 1
    newUser
  }
}
