package services

import http.HttpServer
import db.MongoConnection
import java.nio.charset.Charset

/**
 * E-Commerce Manual HTTP Server
 */
object Main {

  private def envFlag(name: String, default: Boolean): Boolean =
    sys.env
      .get(name)
      .map(_.trim.toLowerCase)
      .filter(_.nonEmpty)
      .map(v => v == "1" || v == "true" || v == "yes")
      .getOrElse(default)

  def main(args: Array[String]): Unit = {
    printBanner()

    // Probar conexión a MongoDB
    println("Conectando a MongoDB...")
    if (!MongoConnection.testConnection()) {
      println("\n No se pudo conectar a MongoDB")
      println("PowerShell: Start-Service MongoDB")
      System.exit(1)
    }

    // Configuración para tareas opcionales de bootstrap/mantenimiento
    val bootstrapOptions = MongoConnection.BootstrapOptions(
      seedExamples = envFlag("LP_SEED_SAMPLE_DATA", default = true),
      runSchemaFixes = envFlag("LP_RUN_SCHEMA_FIXES", default = true),
      seedPromotions = envFlag("LP_SEED_PROMOTIONS", default = true)
    )

    if (bootstrapOptions.isDisabled) {
      println("\nModo produccion: se omiten tareas automáticas de inicialización.")
    } else {
      // Inicializar datos de ejemplo o aplicar migraciones si corresponde
      MongoConnection.initializeData(bootstrapOptions)
    }

    // REORGANIZAR CATEGORÍAS con estructura jerárquica (COMENTADO - ya ejecutado)
    // println("\nReorganizando categorías...")
    // scripts.ReorganizeCategories.run()

    // ACTUALIZAR productos y promociones con nuevas categorías (COMENTADO - ya ejecutado)
    // println("\nActualizando productos y promociones...")
    // scripts.UpdateProductsAndPromotions.run()

    // Purga de solicitudes de saldo (solo si se solicita vía variable de entorno)
    val purgeBalanceRequests = envFlag("LP_PURGE_BALANCE_REQUESTS", default = false)
    if (purgeBalanceRequests) {
      println("\nLimpiando solicitudes de balance (LP_PURGE_BALANCE_REQUESTS=true)...")
      models.BalanceRequestRepo.deleteAll()
    } else {
      println("\nSolicitudes de balance se conservaran (LP_PURGE_BALANCE_REQUESTS=false).")
    }
    
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
           -----------------------------------------------------
                                                                               
              $title                                                         
                                                                               
              Implementacion desde cero con Scala + java.net.*                 
              Base de datos: MongoDB                                          
              $subtitle                                                        
                                                                               
           -----------------------------------------------------
           """.stripMargin)
    } else {
      // Fallback limpio para terminales sin soporte UTF-8
      println(
        s"""
           ----------------------------------------------------------------------
                                    $title
           ----------------------------------------------------------------------
               Servidor HTTP sin frameworks
               Implementación desde cero con Scala + java.net.*
               $subtitle
           ----------------------------------------------------------------------
           """.stripMargin)
    }
  }
}
