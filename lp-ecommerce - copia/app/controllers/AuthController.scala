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

  // GET /notifications (JSON) - Obtener notificaciones no leídas
  def getNotifications = Action { implicit req =>
    req.session.get("userEmail").flatMap(UserRepo.findByEmail) match {
      case Some(user) =>
        val notifications = NotificationRepo.getUnread(user.id)
        val json = play.api.libs.json.Json.obj(
          "count" -> notifications.size,
          "notifications" -> notifications.map { n =>
            play.api.libs.json.Json.obj(
              "id" -> n.id,
              "message" -> n.message,
              "type" -> n.notificationType.asString,
              "createdAt" -> n.createdAt.toString
            )
          }
        )
        Ok(json)
      case None =>
        Unauthorized(play.api.libs.json.Json.obj("error" -> "No autenticado"))
    }
  }

  // POST /notifications/:id/read - Marcar notificación como leída
  def markNotificationAsRead(id: Long) = Action { implicit req =>
    req.session.get("userEmail").flatMap(UserRepo.findByEmail) match {
      case Some(user) =>
        if (NotificationRepo.markAsRead(id, user.id)) {
          Ok(play.api.libs.json.Json.obj("success" -> true))
        } else {
          NotFound(play.api.libs.json.Json.obj("error" -> "Notificación no encontrada"))
        }
      case None =>
        Unauthorized(play.api.libs.json.Json.obj("error" -> "No autenticado"))
    }
  }

  // POST /notifications/mark-all-read - Marcar todas como leídas
  def markAllNotificationsAsRead = Action { implicit req =>
    req.session.get("userEmail").flatMap(UserRepo.findByEmail) match {
      case Some(user) =>
        val count = NotificationRepo.markAllAsRead(user.id)
        Ok(play.api.libs.json.Json.obj("success" -> true, "count" -> count))
      case None =>
        Unauthorized(play.api.libs.json.Json.obj("error" -> "No autenticado"))
    }
  }

  // GET /user/balance (JSON) - Obtener saldo actual del usuario
  def getUserBalance = Action { implicit req =>
    req.session.get("userEmail").flatMap(UserRepo.findByEmail) match {
      case Some(user) =>
        Ok(play.api.libs.json.Json.obj(
          "balance" -> user.balance,
          "totalSpent" -> user.totalSpent,
          "isVip" -> (user.totalSpent >= 100)
        ))
      case None =>
        Unauthorized(play.api.libs.json.Json.obj("error" -> "No autenticado"))
    }
  }
}
