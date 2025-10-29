error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala:jbcrypt.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala
empty definition using pc, found symbol in pc: jbcrypt.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -javax/inject/org/mindrot/jbcrypt.
	 -models/org/mindrot/jbcrypt.
	 -org/mindrot/jbcrypt.
	 -scala/Predef.org.mindrot.jbcrypt.
offset: 39
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala
text:
```scala
package services
import org.mindrot.jb@@crypt.BCrypt


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

```


#### Short summary: 

empty definition using pc, found symbol in pc: jbcrypt.