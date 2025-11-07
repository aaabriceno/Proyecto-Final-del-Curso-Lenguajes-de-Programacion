package services

import models._

/**
 * Servicio de usuarios para registro y autenticación.
 * 
 * Implementación ligera y segura sin frameworks.
 * Basado en UserRepo (que usa SHA-256 internamente).
 */
object UserService {

  /**
   * Registra un nuevo usuario.
   * 
   * @return Either con mensaje de error o el usuario creado.
   */
  def register(name: String, email: String, phone: String, password: String): Either[String, User] = {
    val trimmedName = name.trim
    val trimmedEmail = email.trim
    val trimmedPhone = phone.trim
    val trimmedPass = password.trim

    // Validaciones mínimas
    if (trimmedName.isEmpty || trimmedEmail.isEmpty || trimmedPass.isEmpty)
      Left("Todos los campos son obligatorios")
    else if (!trimmedEmail.contains("@") || !trimmedEmail.contains("."))
      Left("Correo inválido")
    else if (trimmedPass.length < 6)
      Left("La contraseña debe tener al menos 6 caracteres")
    else if (UserRepo.findByEmail(trimmedEmail).isDefined)
      Left("Ya existe una cuenta registrada con este correo")
    else {
      val user = UserRepo.add(trimmedName, trimmedEmail, trimmedPhone, trimmedPass)
      Right(user)
    }
  }

  /**
   * Autentica un usuario con correo y contraseña.
   * 
   * @return Some(User) si las credenciales son correctas, None si no.
   */
  def authenticate(email: String, password: String): Option[User] =
    UserRepo.authenticate(email.trim, password.trim)
}
