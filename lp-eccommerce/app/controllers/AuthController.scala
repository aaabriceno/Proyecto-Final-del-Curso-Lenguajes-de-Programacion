package controllers

import javax.inject._
import play.api.mvc._
import models._

@Singleton
class AuthController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc) {

  private val SessionKey = "userEmail"

  // GET /login
  def loginForm = Action { implicit req =>
    Ok(views.html.login(None))
  }

  // POST /login
  def login = Action { implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val email = data.get("email").flatMap(_.headOption).getOrElse("")
    val pass = data.get("password").flatMap(_.headOption).getOrElse("")

    UserRepo.findByEmail(email) match {
      case Some(u) if u.password == pass =>
        Redirect(routes.ProductsController.list)
          .addingToSession(SessionKey -> email)
          .flashing("success" -> s"Bienvenido, ${u.name}")
      case _ =>
        Redirect(routes.AuthController.loginForm)
          .flashing("error" -> "Correo o contraseña incorrectos")
    }
  }

  // GET /register
  def registerForm = Action { implicit req =>
    Ok(views.html.register(None))
  }

  // POST /register
  def register = Action { implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val name = data("name").head
    val email = data("email").head
    val phone = data("phone").head
    val pass = data("password").head

    if (UserRepo.findByEmail(email).isDefined)
      Redirect(routes.AuthController.registerForm)
        .flashing("error" -> "El correo ya está registrado")
    else {
      UserRepo.add(User(0, name, email, phone, pass))
      Redirect(routes.AuthController.loginForm)
        .flashing("success" -> "Registro exitoso. Inicia sesión.")
    }
  }

  // GET /account
  def account = Action { implicit req =>
    req.session.get(SessionKey) match {
      case Some(email) =>
        UserRepo.findByEmail(email)
          .map(u => Ok(views.html.account(u)))
          .getOrElse(Redirect(routes.AuthController.loginForm))
      case None =>
        Redirect(routes.AuthController.loginForm)
    }
  }

  // GET /logout
  def logout = Action { implicit req =>
    Redirect(routes.ProductsController.list).withNewSession
      .flashing("success" -> "Sesión cerrada")
  }
}
