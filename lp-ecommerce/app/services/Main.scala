package services

import http.HttpServer
import db.MongoConnection
import java.nio.charset.Charset

/**
 * E-Commerce Manual HTTP Server
 * 
 * Sistema de comercio electrónico SIN frameworks + MongoDB
 */
object Main {

  def main(args: Array[String]): Unit = {
    printBanner()

    // Probar conexión a MongoDB
    println("Conectando a MongoDB...")
    if (!MongoConnection.testConnection()) {
      println("\n No se pudo conectar a MongoDB")
      println("Asegúrate de que MongoDB esté corriendo:")
      println("PowerShell: Start-Service MongoDB")
      println("O instala MongoDB siguiendo: INSTALACION_MONGODB.md")
      System.exit(1)
    }

    // Inicializar datos de ejemplo si la BD está vacía
    MongoConnection.initializeData()

    // REORGANIZAR CATEGORÍAS con estructura jerárquica (COMENTADO - ya ejecutado)
    // println("\nReorganizando categorías...")
    // scripts.ReorganizeCategories.run()

    // ACTUALIZAR productos y promociones con nuevas categorías (COMENTADO - ya ejecutado)
    // println("\nActualizando productos y promociones...")
    // scripts.UpdateProductsAndPromotions.run()

    // LIMPIAR solicitudes corruptas (SOLO para desarrollo/debugging)
    // Una vez arreglado el problema, comentar esta línea
    println("\nLimpiando solicitudes de balance corruptas...")
    models.BalanceRequestRepo.deleteAll()
    
    // Cierre limpio al presionar Ctrl+C
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        println("\n Cerrando servidor...")
        HttpServer.stop()
        MongoConnection.close()
      }
    })

    // Iniciar servidor HTTP
    try {
      HttpServer.start()
    } catch {
      case e: Exception =>
        println(s"\n Error fatal: ${e.getMessage}")
        e.printStackTrace()
        MongoConnection.close()
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

    val title = "E-COMMERCE HTTP SERVER"
    val subtitle = "Proyecto Final - Lenguajes de Programacion"

    if (isUtf8) {
      println(
        s"""
           =====================================================
                                                                               
              $title                                                         
                                                                               
              Implementacion desde cero con Scala + java.net.*                 
              Base de datos: MongoDB                                          
              $subtitle                                                        
                                                                               
           ======================================================
           """.stripMargin)
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
