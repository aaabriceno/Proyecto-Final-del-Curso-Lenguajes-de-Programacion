error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -http/HttpResponse.
	 -HttpResponse.
	 -scala/Predef.HttpResponse.
offset: 4165
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AuthController.scala
text:
```scala
package controllers

import http.{HttpRequest, HttpResponse}
import models.{User, UserRepo}
import session.{SessionManager, CsrfProtection}
import services.UserService
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Controlador de autenticación (sin Play Framework)
 * Maneja login, registro y logout con protección CSRF manual.
 */
object AuthController {

  /** Sirve una vista HTML desde /app/views */
  private def serveHtml(filename: String): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = s"$projectDir/app/views/$filename.html"

    Try(Source.fromFile(path, "UTF-8").mkString) match {
      case Success(html) => HttpResponse.ok(html)
      case Failure(e) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${e.getMessage}<br>Ruta: $path")
    }
  }

  /** GET /login — muestra el formulario de login con token CSRF */
  def loginForm(request: HttpRequest): HttpResponse = {
    val sessionId = request.cookies.getOrElse("sessionId", SessionManager.createSession("anonymous"))
    val csrfField = CsrfProtection.hiddenFieldHtml(sessionId)

    val html =
      s"""
      <!DOCTYPE html>
      <html lang="es">
      <head>
        <meta charset="UTF-8">
        <title>Iniciar Sesión</title>
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
      </head>
      <body class="bg-dark text-light d-flex align-items-center justify-content-center vh-100">
        <div class="card p-4 bg-secondary" style="min-width: 300px;">
          <h2 class="text-center mb-3">Iniciar Sesión</h2>
          <form method="POST" action="/login">
            $csrfField
            <div class="mb-3">
              <label>Email:</label>
              <input type="email" name="email" class="form-control" required>
            </div>
            <div class="mb-3">
              <label>Contraseña:</label>
              <input type="password" name="password" class="form-control" required>
            </div>
            <button type="submit" class="btn btn-primary w-100">Entrar</button>
          </form>
        </div>
      </body>
      </html>
      """
    HttpResponse.ok(html)
  }

  /** POST /login — procesa login con verificación CSRF */
  def login(request: HttpRequest): HttpResponse = {
    val data = request.formData
    val email = data.getOrElse("email", "")
    val password = data.getOrElse("password", "")
    val csrfToken = data.getOrElse("csrfToken", "")
    val sessionId = request.cookies.getOrElse("sessionId", "")

    // 🔒 Validación CSRF obligatoria
    if (!CsrfProtection.validateToken(sessionId, csrfToken))
      return HttpResponse.redirect("/login?error=Token+CSRF+inv%C3%A1lido")

    if (email.isEmpty || password.isEmpty)
      return HttpResponse.redirect("/login?error=Todos+los+campos+son+requeridos")

    UserRepo.authenticate(email, password) match {
      case Some(user) =>
        // Crear nueva sesión real
        val newSessionId = SessionManager.createSession(user.email)
        CsrfProtection.regenerateToken(newSessionId)
        val redirectUrl = if (user.isAdmin) "/admin" else "/shop"

        HttpResponse
          .redirect(redirectUrl)
          .withCookie("sessionId", newSessionId, maxAge = Some(86400)) // 24h

      case None =>
        HttpResponse.redirect("/login?error=Credenciales+inv%C3%A1lidas")
    }
  }

  /** GET /register */
  def registerForm(request: HttpRequest): HttpResponse = {
    request.cookies.get("sessionId").flatMap(SessionManager.getSession) match {
      case Some(_) => HttpResponse.redirect("/shop")
      case None    => serveHtml("register")
    }
  }

  /** POST /register */
  def register(request: HttpRequest): HttpResponse = {
    val data = request.formData
    val name = data.getOrElse("name", "")
    val email = data.getOrElse("email", "")
    val phone = data.getOrElse("phone", "")
    val password = data.getOrElse("password", "")

    if (name.isEmpty || email.isEmpty || password.isEmpty)
      return HttpRespon@@se.redirect("/register?error=Todos+los+campos+son+requeridos")

    if (password.length < 6)
      return HttpResponse.redirect("/register?error=La+contrase%C3%B1a+debe+tener+al+menos+6+caracteres")

    if (!email.matches("""^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$"""))
      return HttpResponse.redirect("/register?error=Email+inv%C3%A1lido")

    if (UserRepo.findByEmail(email).isDefined)
      return HttpResponse.redirect("/register?error=El+email+ya+est%C3%A1+registrado")

    UserService.register(name, email, phone, password) match {
      case Right(newUser) =>
        val sessionId = SessionManager.createSession(newUser.email)
        CsrfProtection.regenerateToken(sessionId)
        HttpResponse
          .redirect("/shop")
          .withCookie("sessionId", sessionId, maxAge = Some(86400))

      case Left(error) =>
        HttpResponse.redirect(s"/register?error=${java.net.URLEncoder.encode(error, "UTF-8")}")
    }
  }

  /** GET o POST /logout */
  def logout(request: HttpRequest): HttpResponse = {
    request.cookies.get("sessionId") match {
      case Some(sessionId) =>
        SessionManager.destroySession(sessionId)
        CsrfProtection.removeToken(sessionId)
        HttpResponse
          .redirect("/login")
          .deleteCookie("sessionId")
      case None =>
        HttpResponse.redirect("/login")
    }
  }

  /** Middleware — obtiene email del usuario autenticado */
  def getCurrentUser(request: HttpRequest): Option[String] =
    request.cookies.get("sessionId").flatMap(SessionManager.getSession)

  /** Middleware — verifica si el usuario es admin */
  def isAdmin(request: HttpRequest): Boolean =
    getCurrentUser(request).flatMap(UserRepo.findByEmail).exists(_.isAdmin)

  /** Middleware — requiere usuario autenticado */
  def requireAuth(request: HttpRequest): Either[HttpResponse, User] =
    getCurrentUser(request).flatMap(UserRepo.findByEmail) match {
      case Some(user) => Right(user)
      case None => Left(HttpResponse.redirect("/login?error=Debe+iniciar+sesi%C3%B3n"))
    }

  /** Middleware — requiere rol admin */
  def requireAdmin(request: HttpRequest): Either[HttpResponse, User] =
    requireAuth(request) match {
      case Right(user) if user.isAdmin => Right(user)
      case Right(_)  => Left(HttpResponse.redirect("/shop?error=Acceso+denegado"))
      case Left(resp) => Left(resp)
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.