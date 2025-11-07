error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/SessionManager.scala:scala/collection/MapOps#contains().
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/session/SessionManager.scala
empty definition using pc, found symbol in pc: scala/collection/MapOps#contains().
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -sessions/contains.
	 -sessions/contains#
	 -sessions/contains().
	 -scala/Predef.sessions.contains.
	 -scala/Predef.sessions.contains#
	 -scala/Predef.sessions.contains().
offset: 2721
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

  /** Mapa de sesiones activas: sessionId -> email del usuario */
  private val sessions: mutable.Map[String, String] = mutable.Map.empty

  /** Generador seguro para IDs */
  private val secureRandom = new SecureRandom()

  /** Longitud del bloque aleatorio en bytes */
  private val RANDOM_BYTES = 16

  /** Habilitar logs de consola */
  private val LOG_ENABLED = true

  /** 
   * Genera un session ID único y criptográficamente seguro.
   * Usa UUID v4 + timestamp + bytes aleatorios.
   */
  def generateSessionId(): String = {
    val uuid = UUID.randomUUID().toString.replace("-", "")
    val timestamp = System.currentTimeMillis().toHexString
    val randomBytes = new Array[Byte](RANDOM_BYTES)
    secureRandom.nextBytes(randomBytes)
    val randomHex = randomBytes.map("%02x".format(_)).mkString
    s"$uuid$timestamp$randomHex"
  }

  /** Crea una nueva sesión para un usuario. */
  def createSession(email: String): String = synchronized {
    val sessionId = generateSessionId()
    sessions(sessionId) = email
    log(s"🟢 Sesión creada: $email (${short(sessionId)})")
    sessionId
  }

  /** Obtiene el email asociado a un sessionId. */
  def getSession(sessionId: String): Option[String] = synchronized {
    sessions.get(sessionId)
  }

  /** Verifica si una sesión existe. */
  def isValidSession(sessionId: String): Boolean = synchronized {
    sessions.contains(sessionId)
  }

  /** Destruye una sesión (logout). */
  def destroySession(sessionId: String): Boolean = synchronized {
    sessions.remove(sessionId) match {
      case Some(email) =>
        log(s"🔴 Sesión destruida: $email (${short(sessionId)})")
        true
      case None => false
    }
  }

  /** Número de sesiones activas. */
  def activeSessionsCount(): Int = synchronized(sessions.size)

  /** Lista de todas las sesiones activas. */
  def listActiveSessions(): Map[String, String] = synchronized(sessions.toMap)

  /** Limpia todas las sesiones activas (solo mantenimiento/test). */
  def clearAllSessions(): Unit = synchronized {
    val count = sessions.size
    sessions.clear()
    log(s"⚠️  Todas las sesiones eliminadas ($count).")
  }

  /** Actualiza el email asociado a una sesión. */
  def updateSessionEmail(sessionId: String, newEmail: String): Boolean = synchronized {
    if (sessions.cont@@ains(sessionId)) {
      sessions(sessionId) = newEmail
      log(s"🟡 Sesión actualizada: ${short(sessionId)} -> $newEmail")
      true
    } else false
  }

  /** Destruye todas las sesiones de un usuario específico. */
  def destroyAllSessionsForUser(email: String): Int = synchronized {
    val ids = sessions.filter(_._2 == email).keys.toList
    ids.foreach(sessions.remove)
    log(s"🔴 ${ids.size} sesión(es) cerradas para $email")
    ids.size
  }

  // =============================================================
  // 🔒 Métodos privados auxiliares
  // =============================================================

  private def short(id: String): String = id.take(10) + "..." // para logs legibles

  private def log(msg: String): Unit = if (LOG_ENABLED) println(msg)
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: scala/collection/MapOps#contains().