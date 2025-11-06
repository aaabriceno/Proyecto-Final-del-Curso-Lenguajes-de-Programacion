package http

/**
 * Representación de un HTTP Response
 */
case class HttpResponse(
  status: Int,
  statusText: String,
  headers: Map[String, String],
  body: String
) {
  
  /**
   * Convertir a string HTTP válido
   */
  def toHttpString: String = {
    val sb = new StringBuilder
    
    // Status line
    sb.append(s"HTTP/1.1 $status $statusText\r\n")
    
    // Headers
    headers.foreach { case (key, value) =>
      sb.append(s"$key: $value\r\n")
    }
    
    // Content-Length automático
    if (!headers.contains("Content-Length")) {
      sb.append(s"Content-Length: ${body.getBytes("UTF-8").length}\r\n")
    }
    
    // Línea vacía antes del body
    sb.append("\r\n")
    
    // Body
    sb.append(body)
    
    sb.toString()
  }
  
  /**
   * Agregar header
   */
  def withHeader(key: String, value: String): HttpResponse = {
    copy(headers = headers + (key -> value))
  }
  
  /**
   * Agregar cookie
   */
  def withCookie(name: String, value: String, maxAge: Option[Int] = None, path: String = "/"): HttpResponse = {
    val cookieValue = maxAge match {
      case Some(age) => s"$name=$value; Path=$path; Max-Age=$age; HttpOnly"
      case None => s"$name=$value; Path=$path; HttpOnly"
    }
    withHeader("Set-Cookie", cookieValue)
  }
  
  /**
   * Eliminar cookie (setear Max-Age=0)
   */
  def deleteCookie(name: String, path: String = "/"): HttpResponse = {
    withHeader("Set-Cookie", s"$name=; Path=$path; Max-Age=0; HttpOnly")
  }
}

object HttpResponse {
  
  // Respuestas comunes
  
  def ok(html: String): HttpResponse = {
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = html
    )
  }
  
  def redirect(location: String): HttpResponse = {
    HttpResponse(
      status = 302,
      statusText = "Found",
      headers = Map("Location" -> location),
      body = ""
    )
  }
  
  def notFound(message: String = "Página no encontrada"): HttpResponse = {
    HttpResponse(
      status = 404,
      statusText = "Not Found",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = s"""
        <!DOCTYPE html>
        <html>
        <head><title>404 Not Found</title></head>
        <body>
          <h1>404 - Página no encontrada</h1>
          <p>$message</p>
        </body>
        </html>
      """
    )
  }
  
  def badRequest(message: String = "Solicitud inválida"): HttpResponse = {
    HttpResponse(
      status = 400,
      statusText = "Bad Request",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = s"""
        <!DOCTYPE html>
        <html>
        <head><title>400 Bad Request</title></head>
        <body>
          <h1>400 - Solicitud inválida</h1>
          <p>$message</p>
        </body>
        </html>
      """
    )
  }
  
  def unauthorized(message: String = "No autorizado"): HttpResponse = {
    HttpResponse(
      status = 401,
      statusText = "Unauthorized",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = s"""
        <!DOCTYPE html>
        <html>
        <head><title>401 Unauthorized</title></head>
        <body>
          <h1>401 - No autorizado</h1>
          <p>$message</p>
        </body>
        </html>
      """
    )
  }
  
  def forbidden(message: String = "Acceso prohibido"): HttpResponse = {
    HttpResponse(
      status = 403,
      statusText = "Forbidden",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = s"""
        <!DOCTYPE html>
        <html>
        <head><title>403 Forbidden</title></head>
        <body>
          <h1>403 - Acceso prohibido</h1>
          <p>$message</p>
        </body>
        </html>
      """
    )
  }
  
  def internalError(message: String = "Error interno del servidor"): HttpResponse = {
    HttpResponse(
      status = 500,
      statusText = "Internal Server Error",
      headers = Map("Content-Type" -> "text/html; charset=UTF-8"),
      body = s"""
        <!DOCTYPE html>
        <html>
        <head><title>500 Internal Server Error</title></head>
        <body>
          <h1>500 - Error interno del servidor</h1>
          <p>$message</p>
        </body>
        </html>
      """
    )
  }
  
  def json(data: String): HttpResponse = {
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
      body = data
    )
  }
  
  def css(content: String): HttpResponse = {
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map("Content-Type" -> "text/css; charset=UTF-8"),
      body = content
    )
  }
  
  def javascript(content: String): HttpResponse = {
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map("Content-Type" -> "application/javascript; charset=UTF-8"),
      body = content
    )
  }
  
  def image(bytes: Array[Byte], mimeType: String): HttpResponse = {
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map(
        "Content-Type" -> mimeType,
        "Content-Length" -> bytes.length.toString
      ),
      body = new String(bytes, "ISO-8859-1") // Binary data
    )
  }
}
