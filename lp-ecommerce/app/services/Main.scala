package services

import http.HttpServer
import java.nio.charset.Charset

/**
 * E-Commerce Manual HTTP Server
 * 
 * Sistema de comercio electrónico SIN frameworks
 */
object Main {

  def main(args: Array[String]): Unit = {
    printBanner()

    // Cierre limpio al presionar Ctrl+C
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("\n🛑 Cerrando servidor...")
        HttpServer.stop()
      }
    })

    // Iniciar servidor
    try {
      HttpServer.start()
    } catch {
      case e: Exception =>
        println(s"\n❌ Error fatal: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    }
  }

  /** 
   * Muestra un banner bonito y compatible con cualquier terminal.
   */
  private def printBanner(): Unit = {
    val charset = Charset.defaultCharset().name().toLowerCase

    // Detectar si el entorno soporta UTF-8 (para decidir si usamos bordes bonitos o ASCII)
    val isUtf8 = charset.contains("utf")

    val title = "E-COMMERCE MANUAL HTTP SERVER"
    val subtitle = "Proyecto Final — Lenguajes de Programación"

    if (isUtf8) {
      println(
        s"""
           |╔════════════════════════════════════════════════════════════════════╗
           |║                                                                    ║
           |║   🛒  $title
           |║                                                                    ║
           |║  ⚡ Servidor HTTP SIN frameworks                                    ║
           |║  🔧 Implementación desde cero con Scala + java.net.*                ║
           |║  📚 $subtitle
           |║                                                                    ║
           |║  ✅ NO frameworks web (Play, http4s, Akka HTTP)                     ║
           |║  ✅ NO librerías HTTP externas                                      ║
           |║  ✅ SOLO Scala stdlib + java.net.ServerSocket                       ║
           |║                                                                    ║
           |╚════════════════════════════════════════════════════════════════════╝
           |""".stripMargin)
    } else {
      // Fallback limpio para terminales sin soporte UTF-8
      println(
        s"""
           |======================================================================
           |                         $title
           |======================================================================
           |  * Servidor HTTP sin frameworks
           |  * Implementación desde cero con Scala + java.net.*
           |  * $subtitle
           |----------------------------------------------------------------------
           |  NO frameworks web (Play, http4s, Akka HTTP)
           |  NO librerías HTTP externas
           |  SOLO Scala stdlib + java.net.ServerSocket
           |======================================================================
           |""".stripMargin)
    }
  }
}
