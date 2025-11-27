package db

import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Updates._
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Conexion a MongoDB
 * Base de datos: lp_ecommerce
 */
object MongoConnection {

  case class BootstrapOptions(
    seedExamples: Boolean = true,
    runSchemaFixes: Boolean = true,
    seedPromotions: Boolean = true
  ) {
    def isDisabled: Boolean = !seedExamples && !runSchemaFixes && !seedPromotions
  }

  // URI de conexion (cambiar si usas MongoDB Atlas o servidor remoto)

  private val uriLocal = "mongodb://localhost:27017"
  private val uriAtlas = "mongodb+srv://anthonybriceno_db_user:VvOjX7zqYxNULOZH@lp-ecommerce-cluster.cmr7cbl.mongodb.net/lp_ecommerce?authSource=admin"

  
  private val uri = uriLocal
  // Cliente MongoDB
  private val client: MongoClient = MongoClient(uri)
  
  // Base de datos principal
  val database: MongoDatabase = client.getDatabase("lp_ecommerce")
  
  // Colecciones
  object Collections {
    val users: MongoCollection[Document] = database.getCollection("users")
    val media: MongoCollection[Document] = database.getCollection("productos")  // Renombrado: media → productos
    val categories: MongoCollection[Document] = database.getCollection("categories")
    val carts: MongoCollection[Document] = database.getCollection("carts")
    val downloads: MongoCollection[Document] = database.getCollection("downloads")
    val promotions: MongoCollection[Document] = database.getCollection("promotions")
    val balanceRequests: MongoCollection[Document] = database.getCollection("balance_requests")
    val ratings: MongoCollection[Document] = database.getCollection("ratings")
    val gifts: MongoCollection[Document] = database.getCollection("gifts")
    val rankings: MongoCollection[Document] = database.getCollection("rankings")
    val transactions: MongoCollection[Document] = database.getCollection("transactions")
    val topups: MongoCollection[Document] = database.getCollection("topups")
    val orders: MongoCollection[Document] = database.getCollection("orders")
    val receipts: MongoCollection[Document] = database.getCollection("receipts")
    val passwordResetRequests: MongoCollection[Document] = database.getCollection("password_reset_requests")
    val passwordResetCodes: MongoCollection[Document] = database.getCollection("password_reset_codes")
  }
  
  /**
   * Verifica la conexion a MongoDB
   */
  def testConnection(): Boolean = {
    try {
      val result = Await.result(
        database.listCollectionNames().toFuture(),
        10.seconds
      )
      println("Conexion a MongoDB exitosa")
      println(s"Colecciones existentes: ${result.mkString(", ")}")
      true
    } catch {
      case e: Exception =>
        println(s" Error conectando a MongoDB: ${e.getMessage}")
        println(s" Verifica que la URI sea correcta:")
        println(s"  → $uri")
        false
    }
  }
  
  /**
   * Cierra la conexion
   */
  def close(): Unit = {
    client.close()
    println("Conexion a MongoDB cerrada")
  }
  
  /**
   * Migracion: Renombrar coleccion 'media' a 'productos' y limpiar campos obsoletos
   */
  private def migrateMediaToProductos(): Unit = {
    try {
      // Verificar si existe la coleccion 'media' (vieja)
      val collections = Await.result(database.listCollectionNames().toFuture(), 5.seconds)
      
      if (collections.contains("media")) {
        println("Migrando coleccion 'media' → 'productos'...")
        
        // Renombrar coleccion
        val mediaCollection = database.getCollection("media")
        Await.result(
          mediaCollection.renameCollection(MongoNamespace("lp_ecommerce", "productos")).toFuture(),
          5.seconds
        )
        println("Coleccion renombrada: 'media' → 'productos'")
        
        // Ahora trabajar con la coleccion 'productos'
        val productosCollection = database.getCollection("productos")
        
        // 1. Eliminar campos obsoletos (mtype, coverImage)
        println("Eliminando campos obsoletos (mtype, coverImage)...")
        Await.result(
          productosCollection.updateMany(
            Document(),
            org.mongodb.scala.model.Updates.combine(
              org.mongodb.scala.model.Updates.unset("mtype"),
              org.mongodb.scala.model.Updates.unset("coverImage")
            )
          ).toFuture(),
          5.seconds
        )
        
        // 2. Agregar productType a productos que no lo tienen
        println("Agregando campo 'productType' a productos viejos...")
        Await.result(
          productosCollection.updateMany(
            org.mongodb.scala.model.Filters.exists("productType", false),
            org.mongodb.scala.model.Updates.set("productType", "digital")
          ).toFuture(),
          5.seconds
        )
        
        println("Migracion completada exitosamente")
        
      } else if (collections.contains("productos")) {
        println("Coleccion 'productos' ya existe (migracion previa)")
        
        // Verificar si hay campos obsoletos y eliminarlos
        val productosCollection = database.getCollection("productos")
        val sampleDoc = Await.result(productosCollection.find().first().toFuture(), 5.seconds)
        
        if (sampleDoc != null && (sampleDoc.containsKey("mtype") || sampleDoc.containsKey("coverImage"))) {
          println("Limpiando campos obsoletos de productos existentes...")
          Await.result(
            productosCollection.updateMany(
              Document(),
              org.mongodb.scala.model.Updates.combine(
                org.mongodb.scala.model.Updates.unset("mtype"),
                org.mongodb.scala.model.Updates.unset("coverImage")
              )
            ).toFuture(),
            5.seconds
          )
          println("Campos obsoletos eliminados")
        }
        
        // Agregar productType a productos que no lo tienen
        val countWithoutProductType = Await.result(
          productosCollection.countDocuments(
            org.mongodb.scala.model.Filters.exists("productType", false)
          ).toFuture(),
          5.seconds
        )
        
        if (countWithoutProductType > 0) {
          println(s"Agregando 'productType' a $countWithoutProductType productos...")
          Await.result(
            productosCollection.updateMany(
              org.mongodb.scala.model.Filters.exists("productType", false),
              org.mongodb.scala.model.Updates.set("productType", "digital")
            ).toFuture(),
            5.seconds
          )
          println("Campo 'productType' agregado")
        }
      }
      
    } catch {
      case e: Exception =>
        println(s"Error durante migracion: ${e.getMessage}")
        // No detener la aplicacion, solo advertir
    }
  }
  
  /**
   * Inicializa datos de ejemplo (solo si la BD está vacía)
   */
  def initializeData(options: BootstrapOptions = BootstrapOptions()): Unit = {
    if (options.isDisabled) {
      println("BootstrapOptions desactivado: no se ejecutarán tareas de inicializacion.")
      return
    }

    println("Verificando si hay datos iniciales...")

    if (options.runSchemaFixes) {
      // ========= MIGRACIoN: Renombrar coleccion 'media' a 'productos' =========
      migrateMediaToProductos()
    } else {
      println("Migraciones legacy desactivadas (LP_RUN_SCHEMA_FIXES=false)")
    }

    if (options.seedExamples) {
      val userCount = Await.result(
        Collections.users.countDocuments().toFuture(),
        5.seconds
      )

      if (userCount == 0) {
        println("Insertando datos iniciales...")
        insertInitialData()
      } else {
        println(s"Ya existen $userCount usuarios en la base de datos")

        val users = Await.result(Collections.users.find().toFuture(), 5.seconds)
        val needsPasswordFix = users.exists { doc =>
          val password = doc.getString("password")
          password.length < 40
        }

        if (needsPasswordFix) {
          println("DETECTADAS CONTRASEÑAS SIN HASHEAR - Recreando usuarios con hashes correctos...")
          Await.result(Collections.users.deleteMany(Document()).toFuture(), 5.seconds)
          println("Usuarios viejos eliminados")
          insertInitialData(onlyUsers = true)
        }
      }
    } else {
      println("Insercion de datos de ejemplo desactivada (LP_SEED_SAMPLE_DATA=false)")
    }

    if (options.runSchemaFixes) {
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
          Document("_id" -> 7L, "name" -> "Posters", "parentId" -> 3L, "description" -> "Posters y carteles", "isActive" -> true)
        )

        Await.result(
          Collections.categories.insertMany(moreCategories).toFuture(),
          5.seconds
        )
        println("Categorías adicionales insertadas")
      }

      // Actualizar documentos de media que no tienen isActive/promotionId
      val mediaCount = Await.result(
        Collections.media.countDocuments().toFuture(),
        5.seconds
      )

      if (mediaCount > 0) {
        println(s"Verificando estructura de $mediaCount productos...")
        val allMedia = Await.result(Collections.media.find().toFuture(), 5.seconds)

        allMedia.foreach { doc =>
          var needsUpdate = false
          val updates = scala.collection.mutable.ListBuffer[org.mongodb.scala.bson.conversions.Bson]()

          if (!doc.containsKey("isActive")) {
            updates += org.mongodb.scala.model.Updates.set("isActive", true)
            needsUpdate = true
          }

          if (!doc.containsKey("promotionId")) {
            updates += org.mongodb.scala.model.Updates.set("promotionId", null)
            needsUpdate = true
          }

          if (doc.containsKey("mediaType") && !doc.containsKey("mtype")) {
            updates += org.mongodb.scala.model.Updates.rename("mediaType", "mtype")
            needsUpdate = true
          }
          if (doc.containsKey("url") && !doc.containsKey("assetPath")) {
            updates += org.mongodb.scala.model.Updates.rename("url", "assetPath")
            needsUpdate = true
          }

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

      val categoryCountForUpdate = Await.result(
        Collections.categories.countDocuments().toFuture(),
        5.seconds
      )

      if (categoryCountForUpdate > 0) {
        println(s"Verificando estructura de $categoryCountForUpdate categorías...")
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
            println(s"Actualizada categoría ID ${doc.getLong("_id")}")
          }
        }
      }
    } else {
      println("Normalizacion de categorías/productos desactivada (LP_RUN_SCHEMA_FIXES=false)")
    }

    if (options.seedPromotions) {
      val promotionCount = Await.result(Collections.promotions.countDocuments().toFuture(), 5.seconds)
      if (promotionCount == 0) {
        println("Insertando 2 promociones de ejemplo...")

        import org.mongodb.scala.bson.{BsonArray, BsonInt64, BsonDocument, BsonString, BsonInt32, BsonBoolean, BsonDateTime}
        import java.time.Instant

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
        println("2 promociones insertadas (Black Friday 30%, Videos 20%)")
      } else {
        println(s"Ya existen $promotionCount promociones en la base de datos")
      }
    } else {
      println("Insercion automática de promociones desactivada (LP_SEED_PROMOTIONS=false)")
    }
  }
  
  /**
   * Inserta datos de ejemplo
   */
  private def insertInitialData(onlyUsers: Boolean = false): Unit = {
    import org.mongodb.scala.bson.BsonDateTime
    import java.time.Instant
    import models.UserRepo
    
    // IMPORTANTE: Usar UserRepo.add() para hashear contraseñas automáticamente
    // (No insertar directamente con Document, sino usar el método del modelo)
    
    // Admin user - La contraseña se hasheará automáticamente
    UserRepo.add(
      name = "Administrador",
      email = "admin@lpstudios.com",
      phone = "555-0000",
      password = "admin123",  // Se hasheará con SHA-256
      isAdmin = true
    )
    
    // Usuario ejemplo - La contraseña se hasheará automáticamente
    UserRepo.add(
      name = "Usuario Ejemplo",
      email = "user@example.com",
      phone = "555-0001",
      password = "user123",  // Se hasheará con SHA-256
      isAdmin = false
    )
    
    println("2 usuarios iniciales creados (contraseñas hasheadas)")
    
    // Solo insertar categorías y productos si es la primera vez (no al recrear usuarios)
    if (onlyUsers) {
      return
    }
    
    // Categoría ejemplo
    val categoryDoc = Document(
      "_id" -> 1L,
      "name" -> "Música Electronica",
      "description" -> "Beats y sonidos modernos",
      "isActive" -> true
    )
    
    Await.result(
      Collections.categories.insertOne(categoryDoc).toFuture(),
      5.seconds
    )
    
    // Crear más categorías jerárquicas
    val moreCategories = Seq(
      Document("_id" -> 2L, "name" -> "Video", "description" -> "Contenido audiovisual", "isActive" -> true),
      Document("_id" -> 3L, "name" -> "Diseño", "description" -> "Imágenes y recursos gráficos", "isActive" -> true),
      Document("_id" -> 4L, "name" -> "LoFi", "parentId" -> 1L, "description" -> "Música LoFi y chill", "isActive" -> true),
      Document("_id" -> 5L, "name" -> "Rock", "parentId" -> 1L, "description" -> "Rock y metal", "isActive" -> true),
      Document("_id" -> 6L, "name" -> "Cortos", "parentId" -> 2L, "description" -> "Cortometrajes", "isActive" -> true),
      Document("_id" -> 7L, "name" -> "Posters", "parentId" -> 3L, "description" -> "Posters y carteles", "isActive" -> true)
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
      "coverImage" -> "/assets/images/1.jpg",
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
      "coverImage" -> "/assets/images/2.PNG",
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
      "coverImage" -> "/assets/images/hola.png",
      "stock" -> 75,
      "promotionId" -> None,
      "isActive" -> true,
      "createdAt" -> BsonDateTime(Instant.now().toEpochMilli)
    )
    
    Await.result(
      Collections.media.insertMany(Seq(mediaDoc1, mediaDoc2, mediaDoc3)).toFuture(),
      5.seconds
    )
    
    println("Datos iniciales insertados correctamente")
    println("   - 2 usuarios (1 admin)")
    println("   - 7 categorías (3 raíz + 4 subcategorías)")
    println("   - 3 productos")
  }
}
