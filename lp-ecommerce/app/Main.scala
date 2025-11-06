import http.HttpServer

/**
 * E-Commerce Manual HTTP Server
 * 
 * Sistema de comercio electrónico SIN frameworks
 * Implementación desde cero con java.net.ServerSocket
 * 
 * Autor: [Tu nombre]
 * Fecha: 6 de noviembre de 2025
 * Curso: Lenguajes de Programación
 */
object Main {
  
  def main(args: Array[String]): Unit = {
    printBanner()
    
    // Registrar shutdown hook para cerrar servidor limpiamente
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("\n\n🛑 Cerrando servidor...")
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
  
  private def printBanner(): Unit = {
    println("""
      |╔═══════════════════════════════════════════════════════════════╗
      |║                                                               ║
      |║           🛒  E-COMMERCE MANUAL HTTP SERVER                   ║
      |║                                                               ║
      |║  ⚡ Servidor HTTP SIN frameworks                              ║
      |║  🔧 Implementación desde cero con Scala + java.net.*          ║
      |║  📚 Proyecto Final - Lenguajes de Programación                ║
      |║                                                               ║
      |║  ✅ NO frameworks web (Play, http4s, Akka HTTP)               ║
      |║  ✅ NO librerías HTTP externas                                ║
      |║  ✅ SOLO Scala stdlib + java.net.ServerSocket                 ║
      |║                                                               ║
      |╚═══════════════════════════════════════════════════════════════╝
      |
      |""".stripMargin)
  }
}
