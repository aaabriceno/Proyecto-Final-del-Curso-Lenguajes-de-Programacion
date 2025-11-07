error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/UserController.scala:scala/Predef.String#
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/UserController.scala
empty definition using pc, found symbol in pc: scala/Predef.String#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -String#
	 -scala/Predef.String#
offset: 433
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/UserController.scala
text:
```scala
package controllers

import http.{HttpRequest, HttpResponse}
import models.{UserRepo, BalanceRequestRepo, DownloadRepo}
import session.SessionManager
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

/**
 * Controlador de Usuario (perfil, saldo, descargas)
 */
object UserController {
  
  /**
   * Sirve un archivo HTML estático desde app/views/
   */
  private def serveHtml(filename: S@@tring): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = Paths.get(projectDir, "app", "views", s"$filename.html")
    
    Try(Files.readString(path)) match {
      case Success(html) => HttpResponse.ok(html)
      case Failure(exception) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${exception.getMessage}<br>Ruta intentada: $path")
    }
  }
  
  /**
   * Ver perfil de usuario
   * GET /user/account
   */
  def account(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        serveHtml("user_account")
        
      case Left(response) => response
    }
  }
  
  /**
   * Ver información del usuario (editable)
   * GET /user/info
   */
  def info(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        serveHtml("user_info")
        
      case Left(response) => response
    }
  }
  
  /**
   * Actualizar información del usuario
   * POST /user/info
   */
  def updateInfo(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val name = request.formData.getOrElse("name", user.name)
        val phone = request.formData.getOrElse("phone", user.phone)
        
        // TODO: Implementar UserRepo.updateInfo
        HttpResponse.redirect("/user/info?success=Informaci%C3%B3n+actualizada")
        
      case Left(response) => response
    }
  }
  
  /**
   * Ver descargas del usuario
   * GET /user/downloads
   */
  def downloads(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val userDownloads = DownloadRepo.findByUserId(user.id)
        serveHtml("user_downloads")
        
      case Left(response) => response
    }
  }
  
  /**
   * Solicitar recarga de saldo
   * GET /user/balance/request
   */
  def balanceRequestForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        HttpResponse.ok(s"""
<!DOCTYPE html>
<html>
<head>
  <title>Solicitar Recarga</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
</head>
<body class="bg-dark text-light">
  <div class="container mt-5">
    <h2>Solicitar Recarga de Saldo</h2>
    <form method="POST" action="/user/balance/request">
      <div class="mb-3">
        <label>Monto a recargar:</label>
        <input type="number" name="amount" class="form-control" step="0.01" required>
      </div>
      <button type="submit" class="btn btn-primary">Solicitar</button>
    </form>
  </div>
</body>
</html>
        """)
        
      case Left(response) => response
    }
  }
  
  /**
   * Procesar solicitud de recarga
   * POST /user/balance/request
   */
  def createBalanceRequest(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val amount = request.formData.get("amount").flatMap(_.toDoubleOption).getOrElse(0.0)
        
        if (amount <= 0) {
          return HttpResponse.redirect("/user/account?error=Monto+inv%C3%A1lido")
        }
        
        BalanceRequestRepo.add(user.id, BigDecimal(amount), "transferencia")
        HttpResponse.redirect("/user/account?success=Solicitud+enviada.+Espera+aprobaci%C3%B3n+del+administrador")
        
      case Left(response) => response
    }
  }
  
  /**
   * Ver transacciones del usuario
   * GET /user/transactions
   */
  def transactions(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        serveHtml("transacciones")
        
      case Left(response) => response
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/Predef.String#