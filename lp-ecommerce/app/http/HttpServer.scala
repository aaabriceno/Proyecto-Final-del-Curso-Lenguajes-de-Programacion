package http

import java.net.{ServerSocket, Socket}
import java.io.{BufferedReader, InputStreamReader, PrintWriter}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

/**
 * Servidor HTTP manual usando java.net.ServerSocket
 * SIN frameworks - implementación desde cero
 */
object HttpServer {
  
  private val PORT = 9000
  private var running = false
  private var serverSocket: Option[ServerSocket] = None
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  def start(): Unit = {
    println(s"🚀 Iniciando servidor HTTP en puerto $PORT...")
    println(s"📂 Servidor SIN frameworks - Implementación manual")
    println(s"🌐 Accede en: http://localhost:$PORT")
    println()
    
    try {
      val socket = new ServerSocket(PORT)
      serverSocket = Some(socket)
      running = true
      
      println("✅ Servidor iniciado correctamente")
      println("⏳ Esperando conexiones...\n")
      
      while (running) {
        try {
          val clientSocket = socket.accept()
          // Manejar cada conexión en un Future (asíncrono)
          Future {
            handleClient(clientSocket)
          }
        } catch {
          case e: Exception if !running =>
            println("🛑 Servidor detenido")
        }
      }
    } catch {
      case e: Exception =>
        println(s"❌ Error al iniciar servidor: ${e.getMessage}")
        e.printStackTrace()
    }
  }
  
  def stop(): Unit = {
    println("\n🛑 Deteniendo servidor...")
    running = false
    serverSocket.foreach(_.close())
  }
  
  private def handleClient(socket: Socket): Unit = {
    try {
      val reader = new BufferedReader(new InputStreamReader(socket.getInputStream))
      val writer = new PrintWriter(socket.getOutputStream, true)
      
      // Parsear request
      val request = HttpRequest.parse(reader)
      
      println(s"📥 ${request.method} ${request.path}")
      
      // Rutear y obtener response
      val response = Router.route(request)
      
      // Enviar response
      writer.println(response.toHttpString)
      writer.flush()
      
      // Cerrar conexión
      reader.close()
      writer.close()
      socket.close()
      
    } catch {
      case e: Exception =>
        println(s"❌ Error manejando cliente: ${e.getMessage}")
        e.printStackTrace()
        
        try {
          val writer = new PrintWriter(socket.getOutputStream, true)
          writer.println("HTTP/1.1 500 Internal Server Error\r\n\r\nError interno del servidor")
          writer.close()
          socket.close()
        } catch {
          case _: Exception => // Ignorar errores al enviar error
        }
    }
  }
}
