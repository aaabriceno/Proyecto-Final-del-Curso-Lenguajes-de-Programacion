package controllers

import http.{HttpRequest, HttpResponse}
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Controlador de la página principal (Home)
 */
object HomeController {

  /**
   * Sirve una vista HTML desde /app/views/
   */
  private def serveHtml(filename: String, request: HttpRequest): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = s"$projectDir/app/views/$filename.html"

    Try(Source.fromFile(path, "UTF-8").mkString) match {
      case Success(html) => 
        val response = HttpResponse.ok(html)
        // Preservar cookie de sesión si existe
        if (request.cookies.contains("sessionId")) {
          response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
        } else {
          response
        }
      case Failure(e) =>
        HttpResponse.notFound(
          s"No se pudo cargar la página: ${e.getMessage}<br>Ruta intentada: $path"
        )
    }
  }

  /**
   * Página principal con navbar dinámico
   * GET /
   */
  def index(request: HttpRequest): HttpResponse = {
    // Verificar si hay sesión activa
    AuthController.requireAuth(request) match {
      case Right(user) =>
        // Usuario autenticado - mostrar navbar dinámico
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/index.html"
        
        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            val navbarButtons = if (user.isAdmin) {
              """<a class="btn btn-outline-light btn-sm" href="/shop">🛍️ Tienda</a>
        <a class="btn btn-warning btn-sm" href="/admin">👨‍💼 Admin</a>
        <a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
        <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
            } else {
              """<a class="btn btn-outline-light btn-sm" href="/shop">🛍️ Tienda</a>
        <a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
        <a class="btn btn-success btn-sm" href="/cart">🛒 Carrito</a>
        <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
            }
            
            val updatedHtml = html.replace(
              """<a class="btn btn-outline-light btn-sm" href="/shop">🛍️ Tienda</a>
        <a class="btn btn-outline-light btn-sm" href="/login">
          <i class="bi bi-box-arrow-in-right"></i> Login
        </a>
        <a class="btn btn-warning text-dark btn-sm" href="/register">
          <i class="bi bi-person-plus-fill"></i> Registro
        </a>""",
              navbarButtons
            )
            
            val response = HttpResponse.ok(updatedHtml)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
            
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar la página: ${e.getMessage}")
        }
        
      case Left(_) =>
        // Usuario NO autenticado - mostrar página normal
        serveHtml("index", request)
    }
  }

  /**
   * Página "Acerca de"
   * GET /about
   */
  def about(request: HttpRequest): HttpResponse = serveHtml("about", request)
}
