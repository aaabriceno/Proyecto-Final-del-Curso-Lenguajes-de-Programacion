error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/CsrfProtection.scala:java/util/UUID#
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/CsrfProtection.scala
empty definition using pc, found symbol in pc: java/util/UUID#
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -java/util/UUID.
	 -UUID.
	 -scala/Predef.UUID.
offset: 737
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/CsrfProtection.scala
text:
```scala
package session

import scala.collection.mutable
import java.util.UUID
import java.security.SecureRandom

/**
 * Protección CSRF (Cross-Site Request Forgery) manual.
 * 
 * Genera y valida tokens CSRF para proteger formularios POST/PUT/DELETE.
 * Cada sesión tiene su propio token CSRF único.
 */
object CsrfProtection {
  
  // Mapa de sessionId -> csrfToken
  private val csrfTokens: mutable.Map[String, String] = mutable.Map.empty
  
  // Generador de números aleatorios seguros
  private val secureRandom = new SecureRandom()
  
  /**
   * Genera un token CSRF criptográficamente seguro.
   * 
   * @return Token CSRF de 64 caracteres hexadecimales
   */
  def generateToken(): String = {
    val uuid = UUI@@D.randomUUID().toString.replace("-", "")
    val randomBytes = new Array[Byte](16)
    secureRandom.nextBytes(randomBytes)
    val randomHex = randomBytes.map("%02x".format(_)).mkString
    
    s"$uuid$randomHex"
  }
  
  /**
   * Obtiene o crea un token CSRF para una sesión.
   * Si la sesión ya tiene token, lo retorna. Si no, genera uno nuevo.
   * 
   * @param sessionId ID de la sesión
   * @return Token CSRF para la sesión
   */
  def getOrCreateToken(sessionId: String): String = synchronized {
    csrfTokens.getOrElseUpdate(sessionId, generateToken())
  }
  
  /**
   * Valida un token CSRF contra la sesión actual.
   * 
   * @param sessionId ID de la sesión
   * @param submittedToken Token enviado en el formulario
   * @return true si el token es válido, false si no
   */
  def validateToken(sessionId: String, submittedToken: String): Boolean = synchronized {
    csrfTokens.get(sessionId) match {
      case Some(validToken) => 
        // Comparación segura contra timing attacks
        validToken == submittedToken
      case None => 
        false
    }
  }
  
  /**
   * Valida un token CSRF de forma opcional (para APIs).
   * 
   * @param sessionId ID de la sesión
   * @param submittedTokenOpt Token opcional enviado
   * @return true si no hay token esperado O si el token es válido, false si es inválido
   */
  def validateTokenOptional(sessionId: String, submittedTokenOpt: Option[String]): Boolean = synchronized {
    (csrfTokens.get(sessionId), submittedTokenOpt) match {
      case (None, _) => true // No se esperaba token
      case (Some(_), None) => false // Se esperaba token pero no se envió
      case (Some(valid), Some(submitted)) => valid == submitted
    }
  }
  
  /**
   * Regenera el token CSRF para una sesión (útil después de login).
   * 
   * @param sessionId ID de la sesión
   * @return Nuevo token CSRF
   */
  def regenerateToken(sessionId: String): String = synchronized {
    val newToken = generateToken()
    csrfTokens(sessionId) = newToken
    newToken
  }
  
  /**
   * Elimina el token CSRF de una sesión (útil en logout).
   * 
   * @param sessionId ID de la sesión
   */
  def removeToken(sessionId: String): Unit = synchronized {
    csrfTokens.remove(sessionId)
  }
  
  /**
   * Genera el HTML de un campo hidden con el token CSRF.
   * Para incluir en formularios.
   * 
   * @param sessionId ID de la sesión
   * @return HTML: <input type="hidden" name="csrfToken" value="...">
   */
  def hiddenFieldHtml(sessionId: String): String = {
    val token = getOrCreateToken(sessionId)
    s"""<input type="hidden" name="csrfToken" value="$token">"""
  }
  
  /**
   * Limpia todos los tokens CSRF (para testing o mantenimiento).
   */
  def clearAllTokens(): Unit = synchronized {
    csrfTokens.clear()
  }
  
  /**
   * Obtiene el número de tokens activos (para debugging).
   * 
   * @return Número de tokens CSRF activos
   */
  def activeTokensCount(): Int = synchronized {
    csrfTokens.size
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/util/UUID#