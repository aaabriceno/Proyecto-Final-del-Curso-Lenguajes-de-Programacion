package session

import scala.collection.mutable
import java.util.UUID
import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Protección CSRF (Cross-Site Request Forgery) manual.
 * 
 * Genera, valida y gestiona tokens únicos por sesión para formularios POST.
 * Compatible con HTML y APIs.
 */
object CsrfProtection {

  // Mapa: sessionId -> csrfToken
  private val csrfTokens: mutable.Map[String, String] = mutable.Map.empty

  private val secureRandom = new SecureRandom()

  /**
   * Genera un token CSRF seguro (mezcla de UUID + bytes aleatorios).
   */
  def generateToken(): String = {
    val uuidPart = UUID.randomUUID().toString.replace("-", "")
    val randomBytes = new Array[Byte](16)
    secureRandom.nextBytes(randomBytes)
    val randomPart = Base64.getUrlEncoder.withoutPadding().encodeToString(randomBytes)
    s"${uuidPart}_$randomPart"
  }

  /**
   * Obtiene o crea un token CSRF para una sesión.
   */
  def getOrCreateToken(sessionId: String): String = synchronized {
    csrfTokens.getOrElseUpdate(sessionId, generateToken())
  }

  /**
   * Valida un token CSRF contra el de la sesión actual (comparación segura).
   */
  def validateToken(sessionId: String, submittedToken: String): Boolean = synchronized {
    csrfTokens.get(sessionId) match {
      case Some(validToken) => constantTimeEquals(validToken, submittedToken)
      case None             => false
    }
  }

  /**
   * Valida un token CSRF de forma opcional (para endpoints API).
   */
  def validateTokenOptional(sessionId: String, submittedTokenOpt: Option[String]): Boolean = synchronized {
    (csrfTokens.get(sessionId), submittedTokenOpt) match {
      case (None, _)                 => true   // No se esperaba token
      case (Some(_), None)           => false  // Se esperaba, pero no se envió
      case (Some(valid), Some(sent)) => constantTimeEquals(valid, sent)
    }
  }

  /**
   * Regenera el token CSRF (por ejemplo, después de login).
   */
  def regenerateToken(sessionId: String): String = synchronized {
    val newToken = generateToken()
    csrfTokens(sessionId) = newToken
    newToken
  }

  /**
   * Elimina el token de una sesión (logout).
   */
  def removeToken(sessionId: String): Unit = synchronized {
    csrfTokens.remove(sessionId)
  }

  /**
   * Genera un campo hidden HTML con el token.
   */
  def hiddenFieldHtml(sessionId: String): String = {
    val token = htmlEscape(getOrCreateToken(sessionId))
    s"""<input type="hidden" name="csrfToken" value="$token">"""
  }

  /**
   * Limpia todos los tokens activos (testing/mantenimiento).
   */
  def clearAllTokens(): Unit = synchronized {
    csrfTokens.clear()
  }

  /**
   * Devuelve el número de tokens activos (debug).
   */
  def activeTokensCount(): Int = synchronized(csrfTokens.size)

  // =============================================================
  // Métodos privados auxiliares
  // =============================================================

  /** Comparación segura contra ataques de tiempo. */
  private def constantTimeEquals(a: String, b: String): Boolean = {
    if (a == null || b == null) return false
    val bytesA = a.getBytes(StandardCharsets.UTF_8)
    val bytesB = b.getBytes(StandardCharsets.UTF_8)
    if (bytesA.length != bytesB.length) return false

    var result = 0
    var i = 0
    while (i < bytesA.length) {
      result |= bytesA(i) ^ bytesB(i)
      i += 1
    }
    result == 0
  }

  /** Escapa HTML de forma simple (defensivo). */
  private def htmlEscape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
