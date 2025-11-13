package scripts

import models.{MediaRepo, PromotionRepo}
import org.mongodb.scala.model.Updates._
import org.mongodb.scala.model.Filters._
import db.MongoConnection
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Script para actualizar productos y promociones después de reorganizar categorías
 */
object UpdateProductsAndPromotions {
  
  def run(): Unit = {
    println("🔧 Actualizando productos y promociones...")
    
    // Actualizar categoryId de productos existentes
    updateProducts()
    
    // Actualizar targetIds de promociones existentes
    updatePromotions()
    
    println("✅ Productos y promociones actualizados!")
  }
  
  private def updateProducts(): Unit = {
    println("\n📦 Actualizando categorías de productos...")
    
    val mediaCollection = MongoConnection.Collections.media
    
    // Producto 1: Summer Vibes → Rock (ID: 111)
    Await.result(
      mediaCollection.updateOne(
        equal("_id", 1L),
        set("categoryId", 111L)
      ).toFuture(),
      5.seconds
    )
    println("  ✅ Summer Vibes → Rock (111)")
    
    // Producto 2: Neon Dreams → Electrónica (ID: 112)
    Await.result(
      mediaCollection.updateOne(
        equal("_id", 2L),
        set("categoryId", 112L)
      ).toFuture(),
      5.seconds
    )
    println("  ✅ Neon Dreams → Electrónica (112)")
    
    // Producto 3: Cyberpunk 2077 → Películas (ID: 21)
    Await.result(
      mediaCollection.updateOne(
        equal("_id", 3L),
        set("categoryId", 21L)
      ).toFuture(),
      5.seconds
    )
    println("  ✅ Cyberpunk 2077 → Películas (21)")
    
    // Producto 4: jajajaja → LoFi (ID: 113) - asumiendo que es música
    Await.result(
      mediaCollection.updateOne(
        equal("_id", 4L),
        set("categoryId", 113L)
      ).toFuture(),
      5.seconds
    )
    println("  ✅ jajajaja → LoFi (113)")
  }
  
  private def updatePromotions(): Unit = {
    println("\n🔥 Actualizando targetIds de promociones...")
    
    val promotionsCollection = MongoConnection.Collections.promotions
    
    // Promoción 1: "Black Friday Música" → categoría Música (ID: 11)
    Await.result(
      promotionsCollection.updateOne(
        equal("_id", 1L),
        set("targetIds", java.util.Arrays.asList(11L))
      ).toFuture(),
      5.seconds
    )
    println("  ✅ Black Friday Música → Categoría Música (11)")
    
    // Promoción 2: "Lanzamiento Videos" → categoría Video (ID: 20)
    Await.result(
      promotionsCollection.updateOne(
        equal("_id", 2L),
        set("targetIds", java.util.Arrays.asList(20L))
      ).toFuture(),
      5.seconds
    )
    println("  ✅ Lanzamiento Videos → Categoría Video (20)")
    
    // Las promociones 4 y 6 ya son de productos específicos, no necesitan actualización
    println("  ℹ️  Promociones de productos (4, 6) no requieren cambios")
  }
}
