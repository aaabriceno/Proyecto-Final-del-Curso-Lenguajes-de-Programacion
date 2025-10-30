package controllers

import javax.inject._
import play.api.mvc._
import models._
import services.UserService

@Singleton
class AuthController @Inject()(cc: MessagesControllerComponents, userSvc: UserService)
  extends MessagesAbstractController(cc) {

  private val SessionKey = "userEmail"

  // GET /login
  def loginForm = Action { implicit req =>
    Ok(views.html.login()) // usa tu vista convertida
  }

  // POST /login
  def login = Action { implicit req =>
    val data  = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val email = data.get("email").flatMap(_.headOption).getOrElse("").trim
    val pass  = data.get("password").flatMap(_.headOption).getOrElse("")

    println(s"[DEBUG] login: email=$email") // <-- temporal

    userSvc.authenticate(email, pass) match {
      case Some(u) =>
        Redirect(routes.HomeController.index)
          .addingToSession(SessionKey -> u.email)
          .flashing("success" -> s"Bienvenido, ${u.name}")

      case None =>
        Redirect(routes.AuthController.loginForm)
          .flashing("error" -> "Correo o contraseña inválidos") // <-- verás el flash
    }
  }


  // GET /register
  def registerForm = Action { implicit req =>
    Ok(views.html.register())
  }

  // POST /register
  def register = Action { implicit req =>
    val data  = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val name  = data.get("name").flatMap(_.headOption).getOrElse("").trim
    val email = data.get("email").flatMap(_.headOption).getOrElse("").trim
    val phone = data.get("phone").flatMap(_.headOption).getOrElse("").trim
    val pass  = data.get("password").flatMap(_.headOption).getOrElse("")

    userSvc.register(name, email, phone, pass) match {
      case Right(u) =>
        Redirect(routes.AuthController.account)             // ir directo a cuenta
          .addingToSession(SessionKey -> u.email)           // autologin
          .flashing("success" -> "Registro exitoso. ¡Bienvenido!")

      case Left(msg) =>
        Redirect(routes.AuthController.registerForm).flashing("error" -> msg)
    }
  }


  // GET /account
  def account = Action { implicit req =>
    req.session.get(SessionKey) match {
      case Some(email) =>
        UserRepo.findByEmail(email)
          .map(u => Ok(views.html.user_account())) // por ahora muestra tu vista convertida
          .getOrElse(Redirect(routes.AuthController.loginForm))
      case None =>
        Redirect(routes.AuthController.loginForm).flashing("error" -> "Primero inicia sesión")
    }
  }
  
  // POST /request-balance - Solicitar saldo al administrador
  def requestBalance = Action { implicit req =>
    req.session.get(SessionKey) match {
      case Some(email) =>
        UserRepo.findByEmail(email) match {
          case Some(user) =>
            req.body.asFormUrlEncoded.flatMap(_.get("amount")).flatMap(_.headOption) match {
              case Some(amountStr) =>
                try {
                  val amount = BigDecimal(amountStr)
                  val paymentMethod = req.body.asFormUrlEncoded
                    .flatMap(_.get("payment_method")).flatMap(_.headOption).getOrElse("N/A")
                  
                  if (amount > 0) {
                    // Crear solicitud de saldo
                    BalanceRequestRepo.add(user.id, amount, paymentMethod)
                    
                    Redirect(routes.AuthController.account)
                      .flashing("success" -> s"Solicitud de $$${amount} enviada. El administrador la revisará pronto.")
                  } else {
                    Redirect(routes.AuthController.account)
                      .flashing("error" -> "El monto debe ser mayor a 0")
                  }
                } catch {
                  case _: NumberFormatException =>
                    Redirect(routes.AuthController.account)
                      .flashing("error" -> "Monto inválido")
                }
              case None =>
                Redirect(routes.AuthController.account)
                  .flashing("error" -> "Debes especificar un monto")
            }
          case None =>
            Redirect(routes.AuthController.loginForm)
        }
      case None =>
        Redirect(routes.AuthController.loginForm).flashing("error" -> "Primero inicia sesión")
    }
  }

  // GET /logout
  def logout = Action { implicit req =>
    Redirect(routes.HomeController.index).withNewSession
      .flashing("success" -> "Sesión cerrada")
  }
}
