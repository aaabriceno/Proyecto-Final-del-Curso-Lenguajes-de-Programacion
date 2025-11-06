package http

import java.io.BufferedReader
import scala.collection.mutable

/**
 * Representación de un HTTP Request
 */
case class HttpRequest(
  method: String,
  path: String,
  queryParams: Map[String, String],
  headers: Map[String, String],
  body: Option[String],
  cookies: Map[String, String]
) {
  
  /** Obtener parámetro de query string */
  def getQueryParam(key: String): Option[String] = queryParams.get(key)
  
  /** Obtener header */
  def getHeader(key: String): Option[String] = headers.get(key.toLowerCase)
  
  /** Obtener cookie */
  def getCookie(key: String): Option[String] = cookies.get(key)
  
  /** Obtener datos de formulario (application/x-www-form-urlencoded) */
  def formData: Map[String, String] = {
    body match {
      case Some(b) if getHeader("content-type").exists(_.contains("application/x-www-form-urlencoded")) =>
        parseQueryString(b)
      case _ => Map.empty
    }
  }
  
  private def parseQueryString(qs: String): Map[String, String] = {
    qs.split("&")
      .flatMap { pair =>
        pair.split("=", 2) match {
          case Array(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Array(key) => Some(urlDecode(key) -> "")
          case _ => None
        }
      }
      .toMap
  }
  
  private def urlDecode(s: String): String = {
    java.net.URLDecoder.decode(s.replace("+", " "), "UTF-8")
  }
}

object HttpRequest {
  
  /**
   * Parsear HTTP request desde BufferedReader
   */
  def parse(reader: BufferedReader): HttpRequest = {
    // Leer línea de request (GET /path HTTP/1.1)
    val requestLine = reader.readLine()
    if (requestLine == null || requestLine.isEmpty) {
      throw new Exception("Request line vacía")
    }
    
    val parts = requestLine.split(" ")
    if (parts.length < 2) {
      throw new Exception(s"Request line inválida: $requestLine")
    }
    
    val method = parts(0)
    val fullPath = parts(1)
    
    // Separar path y query string
    val (path, queryString) = fullPath.split("\\?", 2) match {
      case Array(p, qs) => (p, Some(qs))
      case Array(p) => (p, None)
    }
    
    // Parsear query params
    val queryParams = queryString.map(parseQueryString).getOrElse(Map.empty)
    
    // Leer headers
    val headers = mutable.Map[String, String]()
    var line = reader.readLine()
    while (line != null && line.nonEmpty) {
      line.split(":", 2) match {
        case Array(key, value) => 
          headers(key.trim.toLowerCase) = value.trim
        case _ => // Ignorar líneas mal formadas
      }
      line = reader.readLine()
    }
    
    // Parsear cookies
    val cookies = headers.get("cookie")
      .map(parseCookies)
      .getOrElse(Map.empty)
    
    // Leer body (si existe Content-Length)
    val body = headers.get("content-length")
      .flatMap(len => scala.util.Try(len.toInt).toOption)
      .filter(_ > 0)
      .map { length =>
        val buffer = new Array[Char](length)
        reader.read(buffer, 0, length)
        new String(buffer)
      }
    
    HttpRequest(
      method = method,
      path = path,
      queryParams = queryParams,
      headers = headers.toMap,
      body = body,
      cookies = cookies
    )
  }
  
  private def parseQueryString(qs: String): Map[String, String] = {
    qs.split("&")
      .flatMap { pair =>
        pair.split("=", 2) match {
          case Array(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Array(key) => Some(urlDecode(key) -> "")
          case _ => None
        }
      }
      .toMap
  }
  
  private def parseCookies(cookieHeader: String): Map[String, String] = {
    cookieHeader.split(";")
      .flatMap { cookie =>
        cookie.trim.split("=", 2) match {
          case Array(key, value) => Some(key.trim -> value.trim)
          case _ => None
        }
      }
      .toMap
  }
  
  private def urlDecode(s: String): String = {
    java.net.URLDecoder.decode(s.replace("+", " "), "UTF-8")
  }
}
