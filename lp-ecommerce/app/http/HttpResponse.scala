package http

import java.io.File
import java.nio.file.{Files, Paths}
import scala.io.Source

/**
 * Representación de una respuesta HTTP.
 * Inspirado en play.api.mvc.Result, pero sin dependencias externas.
 */
case class HttpResponse(
  status: Int,
  statusText: String,
  headers: Map[String, String],
  body: String,
  binaryBody: Option[Array[Byte]] = None
) {

  /** Convierte este objeto en una cadena HTTP válida lista para enviar por el socket */
  def toHttpString: String = {
    val sb = new StringBuilder
    sb.append(s"HTTP/1.1 $status $statusText\r\n")

    // Agregar headers personalizados
    headers.foreach { case (key, value) =>
      sb.append(s"$key: $value\r\n")
    }

    // Agregar Content-Length si no está definido
    if (!headers.contains("Content-Length")) {
      val length = binaryBody match {
        case Some(bytes) => bytes.length
        case None => body.getBytes("UTF-8").length
      }
      sb.append(s"Content-Length: $length\r\n")
    }

    sb.append("\r\n") // Separador entre headers y body
    // Para binarios, el body se envía por separado
    if (binaryBody.isEmpty) {
      sb.append(body)
    }
    sb.toString()
  }

  /** Agregar o reemplazar un header */
  def withHeader(key: String, value: String): HttpResponse =
    copy(headers = headers + (key -> value))

  /** Agregar cookie (HttpOnly por defecto) */
  def withCookie(name: String, value: String, maxAge: Option[Int] = None, path: String = "/"): HttpResponse = {
    val cookieValue = maxAge match {
      case Some(age) => s"$name=$value; Path=$path; Max-Age=$age; HttpOnly"
      case None      => s"$name=$value; Path=$path; HttpOnly"
    }
    withHeader("Set-Cookie", cookieValue)
  }

  /** Eliminar cookie (invalida con Max-Age=0) */
  def deleteCookie(name: String, path: String = "/"): HttpResponse =
    withHeader("Set-Cookie", s"$name=; Path=$path; Max-Age=0; HttpOnly")
}

object HttpResponse {

  // ---------------------------------------------------------
  // 🔹 Respuestas estándar
  // ---------------------------------------------------------

  def ok(html: String): HttpResponse =
    HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), html)

  def json(jsonString: String): HttpResponse =
    HttpResponse(200, "OK", Map("Content-Type" -> "application/json; charset=UTF-8"), jsonString)

  def css(content: String): HttpResponse =
    HttpResponse(200, "OK", Map("Content-Type" -> "text/css; charset=UTF-8"), content)

  def javascript(content: String): HttpResponse =
    HttpResponse(200, "OK", Map("Content-Type" -> "application/javascript; charset=UTF-8"), content)

  def redirect(location: String): HttpResponse =
    HttpResponse(302, "Found", Map("Location" -> location), "")

  def notFound(message: String = "Página no encontrada"): HttpResponse = {
    val html =
      s"""<!DOCTYPE html>
         |<html><head><title>404 Not Found</title></head>
         |<body><h1>404 - Página no encontrada</h1><p>$message</p></body></html>
         |""".stripMargin
    HttpResponse(404, "Not Found", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
  }

  def badRequest(message: String = "Solicitud inválida"): HttpResponse = {
    val html =
      s"""<!DOCTYPE html>
         |<html><head><title>400 Bad Request</title></head>
         |<body><h1>400 - Solicitud inválida</h1><p>$message</p></body></html>
         |""".stripMargin
    HttpResponse(400, "Bad Request", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
  }

  def unauthorized(message: String = "No autorizado"): HttpResponse = {
    val html =
      s"""<!DOCTYPE html>
         |<html><head><title>401 Unauthorized</title></head>
         |<body><h1>401 - No autorizado</h1><p>$message</p></body></html>
         |""".stripMargin
    HttpResponse(401, "Unauthorized", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
  }

  def forbidden(message: String = "Acceso prohibido"): HttpResponse = {
    val html =
      s"""<!DOCTYPE html>
         |<html><head><title>403 Forbidden</title></head>
         |<body><h1>403 - Acceso prohibido</h1><p>$message</p></body></html>
         |""".stripMargin
    HttpResponse(403, "Forbidden", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
  }

  def internalError(message: String = "Error interno del servidor"): HttpResponse = {
    val html =
      s"""<!DOCTYPE html>
         |<html><head><title>500 Internal Server Error</title></head>
         |<body><h1>500 - Error interno del servidor</h1><p>$message</p></body></html>
         |""".stripMargin
    HttpResponse(500, "Internal Server Error", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
  }

  // ---------------------------------------------------------
  // 🔹 Archivos estáticos
  // ---------------------------------------------------------

  /**
   * Sirve un archivo desde el directorio public/
   * @param path Ruta relativa (ejemplo: "stylesheets/main.css")
   */
  def serveStaticFile(path: String): HttpResponse = {
    val decodedPath = java.net.URLDecoder.decode(path, "UTF-8")
    val file = new File(s"public/$decodedPath")
    if (!file.exists() || !file.isFile)
      return notFound(s"Archivo no encontrado: $decodedPath")

    val mimeType = detectMimeType(decodedPath)

    try {
      if (mimeType.startsWith("text/") || mimeType.contains("javascript") || mimeType.contains("json")) {
        val content = Source.fromFile(file, "UTF-8").mkString
        HttpResponse(200, "OK", Map("Content-Type" -> mimeType), content)
      } else {
        val bytes = Files.readAllBytes(file.toPath)
        HttpResponse(
          200,
          "OK",
          Map("Content-Type" -> mimeType),
          "",  // body vacío
          Some(bytes)  // binaryBody
        )
      }
    } catch {
      case e: Exception => internalError(s"Error al leer archivo: ${e.getMessage}")
    }
  }

  /** Detecta MIME type según extensión */
  private def detectMimeType(path: String): String =
    path.split("\\.").lastOption.map(_.toLowerCase) match {
      case Some("css")   => "text/css; charset=UTF-8"
      case Some("js")    => "application/javascript; charset=UTF-8"
      case Some("html")  => "text/html; charset=UTF-8"
      case Some("json")  => "application/json; charset=UTF-8"
      case Some("png")   => "image/png"
      case Some("jpg") | Some("jpeg") => "image/jpeg"
      case Some("gif")   => "image/gif"
      case Some("svg")   => "image/svg+xml"
      case Some("ico")   => "image/x-icon"
      case Some("woff") | Some("woff2") => "font/woff2"
      case Some("ttf")   => "font/ttf"
      case Some("mp3")   => "audio/mpeg"
      case Some("mp4")   => "video/mp4"
      case Some("webm")  => "video/webm"
      case _             => "application/octet-stream"
    }
}
