error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -models/Base64.getEncoder.encodeToString.
	 -models/Base64.getEncoder.encodeToString#
	 -models/Base64.getEncoder.encodeToString().
	 -java/util/Base64.getEncoder.encodeToString.
	 -java/util/Base64.getEncoder.encodeToString#
	 -java/util/Base64.getEncoder.encodeToString().
	 -Base64.getEncoder.encodeToString.
	 -Base64.getEncoder.encodeToString#
	 -Base64.getEncoder.encodeToString().
	 -scala/Predef.Base64.getEncoder.encodeToString.
	 -scala/Predef.Base64.getEncoder.encodeToString#
	 -scala/Predef.Base64.getEncoder.encodeToString().
offset: 501
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/services/UserService.scala
text:
```scala
package services

import models._
import java.security.MessageDigest
import java.util.Base64

/**
 * Servicio de usuarios adaptado para trabajar SIN Play Framework.
 * Reemplaza BCrypt con SHA-256 (sin dependencias externas).
 */
object UserService {

  // Hash con SHA-256 (reemplaza BCrypt)
  private def hashPassword(password: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(password.getBytes("UTF-8"))
    Base64.getEncoder.en@@codeToString(hash)
  }

  private def verifyPassword(password: String, hash: String): Boolean = {
    hashPassword(password) == hash
  }

  // REGISTRO
  def register(name: String, email: String, phone: String, password: String): Either[String, User] = {
    if (UserRepo.findByEmail(email).isDefined)
      Left("El correo ya está registrado")
    else {
      val u = UserRepo.add(name, email, phone, password) // UserRepo.add ya hashea internamente
      Right(u)
    }
  }

  // LOGIN
  def authenticate(email: String, password: String): Option[User] =
    UserRepo.authenticate(email, password) // UserRepo.authenticate ya verifica con SHA-256
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.