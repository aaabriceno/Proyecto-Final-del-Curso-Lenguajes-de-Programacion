error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -javax/inject/views/html/register.
	 -javax/inject/views/html/register#
	 -javax/inject/views/html/register().
	 -play/api/mvc/views/html/register.
	 -play/api/mvc/views/html/register#
	 -play/api/mvc/views/html/register().
	 -views/html/register.
	 -views/html/register#
	 -views/html/register().
	 -scala/Predef.views.html.register.
	 -scala/Predef.views.html.register#
	 -scala/Predef.views.html.register().
offset: 588
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala
text:
```scala
package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class AuthController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc) {

  private val SessionKey = "userEmail"

  def loginForm = Action { implicit req =>
    Ok(views.html.login())
  }

  def login = Action { implicit req =>
    // TODO: leer form, validar con servicio (BCrypt), setear sesión
    Redirect(routes.HomeController.index).flashing("success" -> "Login OK (demo)")
  }

  def registerForm = Action { implicit req =>
    Ok(views.html.regist@@er())
  }

  def register = Action { implicit req =>
    // TODO: leer form, crear usuario con BCrypt.hashpw
    Redirect(routes.AuthController.loginForm).flashing("success" -> "Registro OK (demo)")
  }

  def account = Action { implicit req =>
    // TODO: verificar sesión real; por ahora muestra la vista
    Ok(views.html.user_account())
  }

  def logout = Action { implicit req =>
    Redirect(routes.HomeController.index).withNewSession.flashing("success" -> "Sesión cerrada")
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.