error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/db/MongoConnection.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/db/MongoConnection.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -org/mongodb/scala/Collections.categories.insertOne.
	 -org/mongodb/scala/Collections.categories.insertOne#
	 -org/mongodb/scala/Collections.categories.insertOne().
	 -org/mongodb/scala/model/Filters.Collections.categories.insertOne.
	 -org/mongodb/scala/model/Filters.Collections.categories.insertOne#
	 -org/mongodb/scala/model/Filters.Collections.categories.insertOne().
	 -org/mongodb/scala/model/Updates.Collections.categories.insertOne.
	 -org/mongodb/scala/model/Updates.Collections.categories.insertOne#
	 -org/mongodb/scala/model/Updates.Collections.categories.insertOne().
	 -scala/concurrent/duration/Collections.categories.insertOne.
	 -scala/concurrent/duration/Collections.categories.insertOne#
	 -scala/concurrent/duration/Collections.categories.insertOne().
	 -Collections.categories.insertOne.
	 -Collections.categories.insertOne#
	 -Collections.categories.insertOne().
	 -scala/Predef.Collections.categories.insertOne.
	 -scala/Predef.Collections.categories.insertOne#
	 -scala/Predef.Collections.categories.insertOne().
offset: 8638
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/db/MongoConnection.scala
text:
```scala
package db

import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Conexión a MongoDB
 * Base de datos: lp_ecommerce
 */
object MongoConnection {

  // URI de conexión (cambiar si usas MongoDB Atlas o servidor remoto)
  private val uri = "mongodb://localhost:27017"
  
  // Cliente MongoDB
  private val client: MongoClient = MongoClient(uri)
  
  // Base de datos principal
  val database: MongoDatabase = client.getDatabase("lp_ecommerce")
  
  // Colecciones
  object Collections {
    val users: MongoCollection[Document] = database.getCollection("users")
    val media: MongoCollection[Document] = database.getCollection("media")
    val categories: MongoCollection[Document] = database.getCollection("categories")
    val carts: MongoCollection[Document] = database.getCollection("carts")
    val downloads: MongoCollection[Document] = database.getCollection("downloads")
    val promotions: MongoCollection[Document] = database.getCollection("promotions")
    val balanceRequests: MongoCollection[Document] = database.getCollection("balance_requests")
    val ratings: MongoCollection[Document] = database.getCollection("ratings")
    val gifts: MongoCollection[Document] = database.getCollection("gifts")
    val rankings: MongoCollection[Document] = database.getCollection("rankings")
  }
  
  /**
   * Verifica la conexión a MongoDB
   */
  def testConnection(): Boolean = {
    try {
      val result = Await.result(
        database.listCollectionNames().toFuture(),
        10.seconds
      )
      println(" Conexión a MongoDB exitosa")
      println(s" Colecciones existentes: ${result.mkString(", ")}")
      true
    } catch {
      case e: Exception =>
        println(s" Error conectando a MongoDB: ${e.getMessage}")
        println(s" Asegúrate de que MongoDB esté corriendo en localhost:27017")
        false
    }
  }
  
  /**
   * Cierra la conexión
   */
  def close(): Unit = {
    client.close()
    println("🔌 Conexión a MongoDB cerrada")
  }
  
  /**
   * Inicializa datos de ejemplo (solo si la BD está vacía)
   */
  def initializeData(): Unit = {
    println("🔍 Verificando si hay datos iniciales...")
    
    val userCount = Await.result(
      Collections.users.countDocuments().toFuture(),
      5.seconds
    )
    
    if (userCount == 0) {
      println(" Insertando datos iniciales...")
      insertInitialData()
    } else {
      println(s" Ya existen $userCount usuarios en la base de datos")
    }
    
    // Agregar categorías adicionales si solo existe 1
    val categoryCount = Await.result(
      Collections.categories.countDocuments().toFuture(),
      5.seconds
    )
    
    if (categoryCount == 1) {
      println(" Agregando categorías adicionales...")
      val moreCategories = Seq(
        Document("_id" -> 2L, "name" -> "Video", "description" -> "Contenido audiovisual", "isActive" -> true),
        Document("_id" -> 3L, "name" -> "Diseño", "description" -> "Imágenes y recursos gráficos", "isActive" -> true),
        Document("_id" -> 4L, "name" -> "LoFi", "parentId" -> 1L, "description" -> "Música LoFi y chill", "isActive" -> true),
        Document("_id" -> 5L, "name" -> "Rock", "parentId" -> 1L, "description" -> "Rock y metal", "isActive" -> true),
        Document("_id" -> 6L, "name" -> "Cortos", "parentId" -> 2L, "description" -> "Cortometrajes", "isActive" -> true),
        Document("_id" -> 7L, "name" -> "Pósters", "parentId" -> 3L, "description" -> "Pósters y carteles", "isActive" -> true)
      )
      
      Await.result(
        Collections.categories.insertMany(moreCategories).toFuture(),
        5.seconds
      )
      println(" Categorías adicionales insertadas")
    }
    
    // Actualizar documentos de media que no tienen isActive/promotionId
    val mediaCount = Await.result(
      Collections.media.countDocuments().toFuture(),
      5.seconds
    )
    
    if (mediaCount > 0) {
      println(s"🔍 Verificando estructura de $mediaCount productos...")
      val allMedia = Await.result(Collections.media.find().toFuture(), 5.seconds)
      
      allMedia.foreach { doc =>
        var needsUpdate = false
        val updates = scala.collection.mutable.ListBuffer[org.mongodb.scala.bson.conversions.Bson]()
        
        // Agregar isActive si no existe
        if (!doc.containsKey("isActive")) {
          updates += org.mongodb.scala.model.Updates.set("isActive", true)
          needsUpdate = true
        }
        
        // Agregar promotionId: null si no existe
        if (!doc.containsKey("promotionId")) {
          updates += org.mongodb.scala.model.Updates.set("promotionId", null)
          needsUpdate = true
        }
        
        // Renombrar campos viejos si existen
        if (doc.containsKey("mediaType") && !doc.containsKey("mtype")) {
          updates += org.mongodb.scala.model.Updates.rename("mediaType", "mtype")
          needsUpdate = true
        }
        if (doc.containsKey("url") && !doc.containsKey("assetPath")) {
          updates += org.mongodb.scala.model.Updates.rename("url", "assetPath")
          needsUpdate = true
        }
        
        // Eliminar campos obsoletos (author, downloads, averageRating si existen)
        if (doc.containsKey("author")) {
          updates += org.mongodb.scala.model.Updates.unset("author")
          needsUpdate = true
        }
        if (doc.containsKey("downloads")) {
          updates += org.mongodb.scala.model.Updates.unset("downloads")
          needsUpdate = true
        }
        if (doc.containsKey("averageRating")) {
          updates += org.mongodb.scala.model.Updates.unset("averageRating")
          needsUpdate = true
        }
        
        if (needsUpdate) {
          Await.result(
            Collections.media.updateOne(
              org.mongodb.scala.model.Filters.equal("_id", doc.getLong("_id")),
              org.mongodb.scala.model.Updates.combine(updates.toSeq: _*)
            ).toFuture(),
            5.seconds
          )
          println(s"   Actualizado producto ID ${doc.getLong("_id")}")
        }
      }
    }
    
    // Actualizar categorías existentes que no tienen isActive
    val categoryCountForUpdate = Await.result(
      Collections.categories.countDocuments().toFuture(),
      5.seconds
    )
    
    if (categoryCountForUpdate > 0) {
      println(s" Verificando estructura de $categoryCountForUpdate categorías...")
      val allCategories = Await.result(Collections.categories.find().toFuture(), 5.seconds)
      
      allCategories.foreach { doc =>
        if (!doc.containsKey("isActive")) {
          Await.result(
            Collections.categories.updateOne(
              org.mongodb.scala.model.Filters.equal("_id", doc.getLong("_id")),
              org.mongodb.scala.model.Updates.set("isActive", true)
            ).toFuture(),
            5.seconds
          )
          println(s"   Actualizada categoría ID ${doc.getLong("_id")}")
        }
      }
    }
  }
  
  /**
   * Inserta datos de ejemplo
   */
  private def insertInitialData(): Unit = {
    import org.mongodb.scala.bson.BsonDateTime
    import java.time.Instant
    
    // Admin user
    val adminDoc = Document(
      "_id" -> 1L,
      "name" -> "Administrador",
      "email" -> "admin@lpstudios.com",
      "password" -> "admin123", // TODO: Hash con BCrypt
      "phone" -> "555-0000",
      "balance" -> 1000.0,
      "totalSpent" -> 0.0,
      "isAdmin" -> true,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    // Usuario ejemplo
    val userDoc = Document(
      "_id" -> 2L,
      "name" -> "Usuario Ejemplo",
      "email" -> "user@example.com",
      "password" -> "user123",
      "phone" -> "555-0001",
      "balance" -> 100.0,
      "totalSpent" -> 0.0,
      "isAdmin" -> false,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    // Insertar usuarios
    Await.result(
      Collections.users.insertMany(Seq(adminDoc, userDoc)).toFuture(),
      5.seconds
    )
    
    // Categoría ejemplo
    val categoryDoc = Document(
      "_id" -> 1L,
      "name" -> "Música Electrónica",
      "description" -> "Beats y sonidos modernos",
      "isActive" -> true
    )
    
    Await.result(
      Collections.categories.i@@nsertOne(categoryDoc).toFuture(),
      5.seconds
    )
    
    // Crear más categorías jerárquicas
    val moreCategories = Seq(
      Document("_id" -> 2L, "name" -> "Video", "description" -> "Contenido audiovisual", "isActive" -> true),
      Document("_id" -> 3L, "name" -> "Diseño", "description" -> "Imágenes y recursos gráficos", "isActive" -> true),
      Document("_id" -> 4L, "name" -> "LoFi", "parentId" -> 1L, "description" -> "Música LoFi y chill", "isActive" -> true),
      Document("_id" -> 5L, "name" -> "Rock", "parentId" -> 1L, "description" -> "Rock y metal", "isActive" -> true),
      Document("_id" -> 6L, "name" -> "Cortos", "parentId" -> 2L, "description" -> "Cortometrajes", "isActive" -> true),
      Document("_id" -> 7L, "name" -> "Pósters", "parentId" -> 3L, "description" -> "Pósters y carteles", "isActive" -> true)
    )
    
    Await.result(
      Collections.categories.insertMany(moreCategories).toFuture(),
      5.seconds
    )
    
    // Productos ejemplo
    val mediaDoc1 = Document(
      "_id" -> 1L,
      "title" -> "Summer Vibes",
      "description" -> "Beat energético perfecto para el verano",
      "mtype" -> "audio",
      "price" -> 9.99,
      "rating" -> 4.5,
      "categoryId" -> 1L,
      "assetPath" -> "/media/audio/summer-vibes.mp3",
      "stock" -> 100,
      "promotionId" -> None,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    val mediaDoc2 = Document(
      "_id" -> 2L,
      "title" -> "Neon Dreams",
      "description" -> "Synthwave atmosférico con vibes retro",
      "mtype" -> "audio",
      "price" -> 12.99,
      "rating" -> 4.8,
      "categoryId" -> 1L,
      "assetPath" -> "/media/audio/neon-dreams.mp3",
      "stock" -> 50,
      "promotionId" -> None,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    val mediaDoc3 = Document(
      "_id" -> 3L,
      "title" -> "Cyberpunk 2077",
      "description" -> "Sonidos futuristas para tus proyectos",
      "mtype" -> "audio",
      "price" -> 15.99,
      "rating" -> 4.3,
      "categoryId" -> 1L,
      "assetPath" -> "/media/audio/cyberpunk.mp3",
      "stock" -> 75,
      "promotionId" -> None,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    Await.result(
      Collections.media.insertMany(Seq(mediaDoc1, mediaDoc2, mediaDoc3)).toFuture(),
      5.seconds
    )
    
    // Promociones de ejemplo
    import org.mongodb.scala.bson.{BsonArray, BsonInt64, BsonDocument, BsonString, BsonInt32, BsonBoolean}
    
    val now = Instant.now().toEpochMilli
    val oneDayMs = 24L * 60 * 60 * 1000
    
    val bsonPromo1 = new BsonDocument()
    bsonPromo1.append("_id", BsonInt64(1L))
    bsonPromo1.append("name", BsonString("Black Friday Música"))
    bsonPromo1.append("description", BsonString("30% de descuento en toda la música"))
    bsonPromo1.append("discountPercent", BsonInt32(30))
    bsonPromo1.append("startDate", BsonDateTime(now - oneDayMs))
    bsonPromo1.append("endDate", BsonDateTime(now + (5 * oneDayMs)))
    bsonPromo1.append("targetType", BsonString("category"))
    bsonPromo1.append("targetIds", BsonArray(BsonInt64(1L)))
    bsonPromo1.append("isActive", BsonBoolean(true))
    bsonPromo1.append("createdAt", BsonDateTime(now))
    val promo1 = Document(bsonPromo1)
    
    val bsonPromo2 = new BsonDocument()
    bsonPromo2.append("_id", BsonInt64(2L))
    bsonPromo2.append("name", BsonString("Lanzamiento Videos"))
    bsonPromo2.append("description", BsonString("20% OFF en categoría Video"))
    bsonPromo2.append("discountPercent", BsonInt32(20))
    bsonPromo2.append("startDate", BsonDateTime(now))
    bsonPromo2.append("endDate", BsonDateTime(now + (10 * oneDayMs)))
    bsonPromo2.append("targetType", BsonString("category"))
    bsonPromo2.append("targetIds", BsonArray(BsonInt64(2L)))
    bsonPromo2.append("isActive", BsonBoolean(true))
    bsonPromo2.append("createdAt", BsonDateTime(now))
    val promo2 = Document(bsonPromo2)
    
    Await.result(
      Collections.promotions.insertMany(Seq(promo1, promo2)).toFuture(),
      5.seconds
    )
    
    println("✅ Datos iniciales insertados correctamente")
    println("   - 2 usuarios (1 admin)")
    println("   - 7 categorías (3 raíz + 4 subcategorías)")
    println("   - 3 productos")
    println("   - 2 promociones activas")
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.