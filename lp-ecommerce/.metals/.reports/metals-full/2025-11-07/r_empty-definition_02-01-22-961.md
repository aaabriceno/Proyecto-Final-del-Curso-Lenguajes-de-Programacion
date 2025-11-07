error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/SessionManager.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/SessionManager.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -scala/Predef.
	 -scala/Predef#
	 -scala/Predef().
offset: 897
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/SessionManager.scala
text:
```scala
package session

import scala.collection.mutable
import java.util.UUID
import java.security.SecureRandom

/**
 * Gestor de sesiones manual sin frameworks.
 * 
 * Mantiene un mapa de sessionId -> userEmail para rastrear usuarios autenticados.
 * Thread-safe usando synchronized para operaciones concurrentes.
 */
object SessionManager {
  
  // Mapa de sesiones activas: sessionId -> email del usuario
  private val sessions: mutable.Map[String, String] = mutable.Map.empty
  
  // Generador de números aleatorios seguros para session IDs
  private val secureRandom = new SecureRandom()
  
  /**
   * Genera un session ID único y criptográficamente seguro.
   * Usa UUID v4 + timestamp + random bytes para máxima entropía.
   * 
   * @return Session ID de 64 caracteres hexadecimales
   */
  def generateSessionId(): String = {
    val uuid = UUID.randomUUID().toString.r@@eplace("-", "")
    val timestamp = System.currentTimeMillis().toHexString
    val randomBytes = new Array[Byte](16)
    secureRandom.nextBytes(randomBytes)
    val randomHex = randomBytes.map("%02x".format(_)).mkString
    
    s"$uuid$timestamp$randomHex"
  }
  
  /**
   * Crea una nueva sesión para un usuario.
   * 
   * @param email Email del usuario autenticado
   * @return Session ID generado
   */
  def createSession(email: String): String = synchronized {
    val sessionId = generateSessionId()
    sessions(sessionId) = email
    println(s"✓ Sesión creada: $sessionId -> $email")
    sessionId
  }
  
  /**
   * Obtiene el email del usuario asociado a una sesión.
   * 
   * @param sessionId ID de la sesión
   * @return Some(email) si la sesión existe, None si no
   */
  def getSession(sessionId: String): Option[String] = synchronized {
    sessions.get(sessionId)
  }
  
  /**
   * Verifica si una sesión existe y es válida.
   * 
   * @param sessionId ID de la sesión
   * @return true si la sesión existe, false si no
   */
  def isValidSession(sessionId: String): Boolean = synchronized {
    sessions.contains(sessionId)
  }
  
  /**
   * Destruye una sesión (logout).
   * 
   * @param sessionId ID de la sesión a destruir
   * @return true si se destruyó la sesión, false si no existía
   */
  def destroySession(sessionId: String): Boolean = synchronized {
    sessions.remove(sessionId) match {
      case Some(email) =>
        println(s"✓ Sesión destruida: $sessionId ($email)")
        true
      case None =>
        false
    }
  }
  
  /**
   * Obtiene el número de sesiones activas (para debugging/admin).
   * 
   * @return Número de sesiones activas
   */
  def activeSessionsCount(): Int = synchronized {
    sessions.size
  }
  
  /**
   * Lista todas las sesiones activas (para debugging/admin).
   * 
   * @return Mapa de sessionId -> email
   */
  def listActiveSessions(): Map[String, String] = synchronized {
    sessions.toMap
  }
  
  /**
   * Limpia todas las sesiones (para testing o mantenimiento).
   */
  def clearAllSessions(): Unit = synchronized {
    val count = sessions.size
    sessions.clear()
    println(s"✓ Sesiones limpiadas: $count sesiones destruidas")
  }
  
  /**
   * Actualiza el email asociado a una sesión (útil para actualización de perfil).
   * 
   * @param sessionId ID de la sesión
   * @param newEmail Nuevo email del usuario
   * @return true si se actualizó, false si la sesión no existe
   */
  def updateSessionEmail(sessionId: String, newEmail: String): Boolean = synchronized {
    if (sessions.contains(sessionId)) {
      sessions(sessionId) = newEmail
      println(s"✓ Sesión actualizada: $sessionId -> $newEmail")
      true
    } else {
      false
    }
  }
  
  /**
   * Destruye todas las sesiones de un usuario específico (útil para cierre de cuenta).
   * 
   * @param email Email del usuario
   * @return Número de sesiones destruidas
   */
  def destroyAllSessionsForUser(email: String): Int = synchronized {
    val sessionIds = sessions.filter(_._2 == email).keys.toList
    sessionIds.foreach(sessions.remove)
    println(s"✓ Sesiones destruidas para $email: ${sessionIds.size} sesiones")
    sessionIds.size
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.