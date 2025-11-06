package services
import org.mindrot.jbcrypt.BCrypt


import javax.inject._
import models._
import org.mindrot.jbcrypt.BCrypt

@Singleton
class UserService @Inject()() {

  // REGISTRO
  def register(name: String, email: String, phone: String, password: String): Either[String, User] = {
    if (UserRepo.findByEmail(email).isDefined)
      Left("El correo ya está registrado")
    else {
      val hash = BCrypt.hashpw(password, BCrypt.gensalt(10))
      val u = UserRepo.add(name, email, phone, hash)
      Right(u)
    }
  }

  // LOGIN
  def authenticate(email: String, password: String): Option[User] =
    UserRepo.findByEmail(email).filter(u => BCrypt.checkpw(password, u.passwordHash))
}
