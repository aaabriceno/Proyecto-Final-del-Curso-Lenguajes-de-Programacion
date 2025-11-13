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
    println("🔌 Conectando a MongoDB...")
    if (!MongoConnection.testConnection()) {
      println("\n❌ No se pudo conectar a MongoDB")
      println("💡 Asegúrate de que MongoDB esté corriendo:")
      println("   PowerShell: Start-Service MongoDB")
      println("   O instala MongoDB siguiendo: INSTALACION_MONGODB.md")
      System.exit(1)
    }

    // Inicializar datos de ejemplo si la BD está vacía
    MongoConnection.initializeData()

    // REORGANIZAR CATEGORÍAS con estructura jerárquica
    println("\n🗂️  Reorganizando categorías...")
    scripts.ReorganizeCategories.run()

    // ACTUALIZAR productos y promociones con nuevas categorías
    println("\n🔧 Actualizando productos y promociones...")
    scripts.UpdateProductsAndPromotions.run()

    // LIMPIAR solicitudes corruptas (SOLO para desarrollo/debugging)
    // Una vez arreglado el problema, comentar esta línea
    println("\n🧹 Limpiando solicitudes de balance corruptas...")
    models.BalanceRequestRepo.deleteAll()
    
    // ACTUALIZAR productos con imágenes de portada
    println("\n🖼️  Actualizando productos con imágenes...")
    updateProductImages()

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
        println(s"\n❌ Error fatal: ${e.getMessage}")
        e.printStackTrace()
        MongoConnection.close()
        System.exit(1)
    }
  }

  /** Actualizar productos existentes con imágenes de portada */
  private def updateProductImages(): Unit = {
    import db.MongoConnection.Collections
    import org.mongodb.scala.model.Updates.set
    import org.mongodb.scala.model.Filters.equal
    import scala.concurrent.Await
    import scala.concurrent.duration._
    
    try {
      // Actualizar producto 1: Summer Vibes
      Await.result(
        Collections.media.updateOne(
          equal("_id", 1L),
          set("coverImage", "/assets/images/1.jpg")
        ).toFuture(),
        5.seconds
      )
      
      // Actualizar producto 2: Neon Dreams
      Await.result(
        Collections.media.updateOne(
          equal("_id", 2L),
          set("coverImage", "/assets/images/2.PNG")
        ).toFuture(),
        5.seconds
      )
      
      // Actualizar producto 3: Cyberpunk 2077
      Await.result(
        Collections.media.updateOne(
          equal("_id", 3L),
          set("coverImage", "/assets/images/hola.png")
        ).toFuture(),
        5.seconds
      )
      
      println("✅ Imágenes de portada actualizadas correctamente")
    } catch {
      case e: Exception =>
        println(s"⚠️  Error actualizando imágenes: ${e.getMessage}")
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
           |║  �️  Base de datos: MongoDB                                         ║
           |║  �📚 $subtitle
           |║                                                                    ║
           |║  ✅ NO frameworks web (Play, http4s, Akka HTTP)                     ║
           |║  ✅ NO librerías HTTP externas                                      ║
           |║  ✅ SOLO Scala stdlib + java.net.ServerSocket                       ║
           |║  ✅ MongoDB para persistencia de datos                              ║
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
