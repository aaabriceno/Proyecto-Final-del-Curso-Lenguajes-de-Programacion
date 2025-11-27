package http

import java.net.{ServerSocket, Socket, InetAddress, NetworkInterface}
import java.io.{BufferedReader, InputStreamReader, BufferedWriter, OutputStreamWriter, PrintWriter}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Failure}
import scala.jdk.CollectionConverters._

/**
 * Servidor HTTP manual usando java.net.ServerSocket
 * Implementación 100 % nativa en Scala (sin frameworks).
 */
object HttpServer {

  /** Puerto*/
  private val PORT = 9000

  /** Estado de ejecución del servidor */
  @volatile private var running = false

  /** Socket principal del servidor */
  private var serverSocket: Option[ServerSocket] = None

  /** Contexto de ejecución para manejar múltiples clientes simultáneamente */
  implicit val ec: ExecutionContext = ExecutionContext.global

  /**
   * Obtiene todas las IPs locales del sistema
   */
  private def getLocalIPs: List[String] = {
    NetworkInterface.getNetworkInterfaces.asScala.toList
      .flatMap(_.getInetAddresses.asScala)
      .filter(addr => !addr.isLoopbackAddress && addr.getAddress.length == 4)
      .map(_.getHostAddress)
  }

  /**
   * Inicia el servidor HTTP y queda escuchando indefinidamente.
   */
  def start(): Unit = {
    println(s"Iniciando servidor HTTP en puerto $PORT...")
    println(s"Acceso LOCAL: http://localhost:$PORT")
    
    // Mostrar todas las IPs para acceso en red
    val localIPs = getLocalIPs
    if (localIPs.nonEmpty) {
      println(s"Acceso en RED LOCAL:")
      localIPs.foreach(ip => println(s"http://$ip:$PORT"))
    }
    println()

    // Crear ServerSocket que acepta conexiones de cualquier IP (0.0.0.0)
    Try(new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"))) match {
      case scala.util.Success(socket) =>
        serverSocket = Some(socket)
        running = true
        println("Servidor iniciado correctamente.")
        println("Esperando conexiones...\n")

        while (running) {
          try {
            val clientSocket = socket.accept()
            Future {
              handleClient(clientSocket)
            }
          } catch {
            case _: java.net.SocketException if !running =>
              println("Servidor detenido correctamente.")
            case e: Exception =>
              println(s"Error aceptando conexión: ${e.getMessage}")
          }
        }

      case Failure(e) =>
        println(s"Error al iniciar el servidor: ${e.getMessage}")
        e.printStackTrace()
    }
  }

  /**
   * Detiene el servidor y cierra el socket principal.
   */
  def stop(): Unit = {
    println("\nDeteniendo servidor...")
    running = false
    serverSocket.foreach { s =>
      Try(s.close())
      println("Socket cerrado correctamente.")
    }
  }

  /**
   * Maneja una conexión HTTP entrante.
   */
  private def handleClient(socket: Socket): Unit = {
    val clientIp = socket.getInetAddress.getHostAddress
    try {
      val reader = new BufferedReader(new InputStreamReader(socket.getInputStream, "UTF-8"))
      val writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream, "UTF-8"))

      // Parsear request
      val request = HttpRequest.parse(reader)
      println(s"[${clientIp}] ${request.method} ${request.path}")

      // Enviar la respuesta
      val response = Router.route(request)
      writer.write(response.toHttpString)
      writer.flush()
      
      // Si hay binaryBody, enviarlo directamente como bytes
      response.binaryBody.foreach { bytes =>
        socket.getOutputStream.write(bytes)
        socket.getOutputStream.flush()
      }

    } catch {
      case e: Exception =>
        println(s"Error manejando cliente [$clientIp]: ${e.getMessage}")
        try {
          val errorWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream, "UTF-8"))
          val errorMsg = "HTTP/1.1 500 Internal Server Error\r\nContent-Type: text/plain\r\n\r\nError interno del servidor."
          errorWriter.write(errorMsg)
          errorWriter.flush()
          errorWriter.close()
        } catch {
          case _: Exception => // ignora si no se puede enviar error
        }
    } finally {
      Try(socket.close())
    }
  }
}
