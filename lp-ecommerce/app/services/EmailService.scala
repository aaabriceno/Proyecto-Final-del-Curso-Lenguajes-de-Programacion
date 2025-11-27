package services

import scala.util.Try
import java.util.Properties
import jakarta.mail._
import jakarta.mail.internet._

object EmailService {

  case class EmailConfig(
    host: String,
    port: Int,
    username: String,
    password: String,
    from: String,
    useTls: Boolean
  )

  /** Lee configuración SMTP desde variables de entorno.
    * Si faltan datos mínimos, se trabaja en modo DEMO (solo imprime en consola).
    */
  private def loadConfig(): Option[EmailConfig] = {
    val host = sys.env.getOrElse("SMTP_HOST", "")
    val port = sys.env.get("SMTP_PORT").flatMap(p => Try(p.toInt).toOption).getOrElse(587)
    val user = sys.env.getOrElse("SMTP_USER", "")
    val pass = sys.env.getOrElse("SMTP_PASS", "")
    val from = sys.env.getOrElse("SMTP_FROM", user)
    val useTls = sys.env.get("SMTP_TLS").forall(_.trim.toLowerCase != "false")

    if (host.nonEmpty && user.nonEmpty && pass.nonEmpty && from.nonEmpty)
      Some(EmailConfig(host, port, user, pass, from, useTls))
    else
      None
  }

  /**
   * Envía un correo HTML, opcionalmente con un adjunto (por ejemplo, PDF).
   * - Si no hay config SMTP: modo DEMO, imprime en consola.
   * - Si hay config: usa Jakarta Mail para enviar vía SMTP.
   */
  def send(to: String, subject: String, htmlBody: String, attachment: Option[java.nio.file.Path] = None): Unit = {
    loadConfig() match {
      case None =>
        println("[EMAIL DEMO] No hay configuración SMTP. Imprimiendo correo simulado:")
        println(s"  Para: $to")
        println(s"  Asunto: $subject")
        println("  Contenido (HTML):")
        println(htmlBody.take(500) + (if (htmlBody.length > 500) "..." else ""))

      case Some(cfg) =>
        try {
          val props = new Properties()
          props.put("mail.smtp.host", cfg.host)
          props.put("mail.smtp.port", cfg.port.toString)
          props.put("mail.smtp.auth", "true")
          if (cfg.useTls) {
            props.put("mail.smtp.starttls.enable", "true")
          }

          val auth = new Authenticator {
            override def getPasswordAuthentication: PasswordAuthentication =
              new PasswordAuthentication(cfg.username, cfg.password)
          }

          val session = Session.getInstance(props, auth)
          val message = new MimeMessage(session)
          message.setFrom(new InternetAddress(cfg.from))
          message.setRecipients(Message.RecipientType.TO, to)
          message.setSubject(subject, "UTF-8")

          attachment match {
            case Some(path) =>
              val multipart = new MimeMultipart()

              // Parte 1: cuerpo HTML
              val bodyPart = new MimeBodyPart()
              bodyPart.setContent(htmlBody, "text/html; charset=UTF-8")
              multipart.addBodyPart(bodyPart)

              // Parte 2: adjunto PDF (u otro)
              val attachPart = new MimeBodyPart()
              val datasource = new jakarta.activation.FileDataSource(path.toFile)
              attachPart.setDataHandler(new jakarta.activation.DataHandler(datasource))
              attachPart.setFileName(path.getFileName.toString)
              multipart.addBodyPart(attachPart)

              message.setContent(multipart)

            case None =>
              message.setContent(htmlBody, "text/html; charset=UTF-8")
          }

          println(s"[EMAIL] Enviando correo real a $to usando ${cfg.host}:${cfg.port} como ${cfg.username}")
          Transport.send(message)
        } catch {
          case e: Exception =>
            println(s"Error enviando correo real: ${e.getMessage}")
            println("Volviendo a modo DEMO (solo consola).")
            println(s"[EMAIL FALLBACK] Para: $to, Asunto: $subject")
            println(htmlBody.take(500) + (if (htmlBody.length > 500) "..." else ""))
        }
    }
  }
}
