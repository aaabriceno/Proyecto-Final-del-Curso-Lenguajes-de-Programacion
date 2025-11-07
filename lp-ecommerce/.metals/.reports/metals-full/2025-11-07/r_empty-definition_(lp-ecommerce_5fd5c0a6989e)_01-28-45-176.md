error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/HttpRequest.scala:scala/Predef.Map#
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/HttpRequest.scala
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol scala/Predef.Map#
empty definition using fallback
non-local guesses:

offset: 3883
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/HttpRequest.scala
text:
```scala
package http

import java.io.BufferedReader
import scala.collection.mutable
import scala.util.Try
import scala.util.parsing.json._

/**
 * Representa una solicitud HTTP recibida por el servidor.
 * Similar a `play.api.mvc.Request`, pero implementado de forma nativa.
 * Compatible con Scala estándar (sin dependencias externas).
 */
case class HttpRequest(
  method: String,
  path: String,
  queryParams: Map[String, String],
  headers: Map[String, String],
  body: Option[String],
  cookies: Map[String, String]
) {

  /** Obtiene un parámetro del query string (?x=1) */
  def getQueryParam(key: String): Option[String] = queryParams.get(key)

  /** Obtiene un header, sin distinguir mayúsculas/minúsculas */
  def getHeader(key: String): Option[String] = headers.get(key.toLowerCase)

  /** Obtiene una cookie por nombre */
  def getCookie(key: String): Option[String] = cookies.get(key)

  /** Devuelve el cuerpo si es JSON (usa parser estándar de Scala) */
  def jsonBody: Option[Map[String, Any]] =
    if (getHeader("content-type").exists(_.contains("application/json")))
      body.flatMap(b => JSON.parseFull(b).map(_.asInstanceOf[Map[String, Any]]))
    else None

  /** Devuelve el cuerpo si es formulario (application/x-www-form-urlencoded) */
  def formData: Map[String, String] =
    if (getHeader("content-type").exists(_.contains("application/x-www-form-urlencoded")))
      body.map(parseQueryString).getOrElse(Map.empty)
    else Map.empty

  // --- Utils internos ---
  private def parseQueryString(qs: String): Map[String, String] =
    qs.split("&").flatMap {
      case pair if pair.nonEmpty =>
        pair.split("=", 2) match {
          case Array(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Array(key)        => Some(urlDecode(key) -> "")
          case _                 => None
        }
      case _ => None
    }.toMap

  private def urlDecode(s: String): String =
    java.net.URLDecoder.decode(s.replace("+", " "), "UTF-8")
}

object HttpRequest {

  /** Límite máximo del tamaño del body (por seguridad) */
  private val MAX_BODY_SIZE = 1024 * 1024 * 5 // 5 MB

  /**
   * Parsea una solicitud HTTP completa desde un BufferedReader de socket.
   */
  def parse(reader: BufferedReader): HttpRequest = {
    // --- Línea inicial ---
    val requestLine = Option(reader.readLine()).getOrElse(
      throw new Exception("Request vacío o malformado")
    )

    val parts = requestLine.split(" ")
    if (parts.length < 2) throw new Exception(s"Request line inválida: $requestLine")

    val method = parts(0)
    val fullPath = parts(1)
    val (path, queryString) = fullPath.split("\\?", 2) match {
      case Array(p, qs) => (p, Some(qs))
      case Array(p)     => (p, None)
    }
    val queryParams = queryString.map(parseQueryString).getOrElse(Map.empty)

    // --- Headers ---
    val headers = mutable.Map[String, String]()
    var line: String = null
    while ({ line = reader.readLine(); line != null && line.nonEmpty }) {
      line.split(":", 2) match {
        case Array(k, v) => headers(k.trim.toLowerCase) = v.trim
        case _           => // ignorar líneas mal formadas
      }
    }

    // --- Cookies ---
    val cookies = headers
      .get("cookie")
      .map(parseCookies)
      .getOrElse(Map.empty)

    // --- Body ---
    val body = headers
      .get("content-length")
      .flatMap(len => Try(len.toInt).toOption)
      .filter(len => len > 0 && len < MAX_BODY_SIZE)
      .map { len =>
        val buffer = new Array[Char](len)
        val readCount = reader.read(buffer, 0, len)
        new String(buffer.take(readCount))
      }

    HttpRequest(method, path, queryParams, headers.toMap, body, cookies)
  }

  // --- Utils ---
  private def parseQueryString(qs: String): Map@@[String, String] =
    qs.split("&").flatMap {
      case pair if pair.nonEmpty =>
        pair.split("=", 2) match {
          case Array(k, v) => Some(urlDecode(k) -> urlDecode(v))
          case Array(k)    => Some(urlDecode(k) -> "")
          case _           => None
        }
      case _ => None
    }.toMap

  private def parseCookies(raw: String): Map[String, String] =
    raw.split(";").flatMap { cookie =>
      cookie.trim.split("=", 2) match {
        case Array(k, v) => Some(k.trim -> v.trim)
        case _           => None
      }
    }.toMap

  private def urlDecode(s: String): String =
    java.net.URLDecoder.decode(s.replace("+", " "), "UTF-8")
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 