error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/check_categories.sc:
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/check_categories.sc
empty definition using pc, found symbol in pc: 
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -org/mongodb/scala/doc/getLong.
	 -org/mongodb/scala/doc/getLong#
	 -org/mongodb/scala/doc/getLong().
	 -org/mongodb/scala/model/Filters.doc.getLong.
	 -org/mongodb/scala/model/Filters.doc.getLong#
	 -org/mongodb/scala/model/Filters.doc.getLong().
	 -scala/concurrent/duration/doc/getLong.
	 -scala/concurrent/duration/doc/getLong#
	 -scala/concurrent/duration/doc/getLong().
	 -doc/getLong.
	 -doc/getLong#
	 -doc/getLong().
	 -scala/Predef.doc.getLong.
	 -scala/Predef.doc.getLong#
	 -scala/Predef.doc.getLong().
offset: 636
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/check_categories.sc
text:
```scala
import $file.build

import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import scala.concurrent.Await
import scala.concurrent.duration._

// Conectar a MongoDB
val mongoClient = MongoClient("mongodb://localhost:27017")
val database = mongoClient.getDatabase("lp_ecommerce")
val collection = database.getCollection("categories")

println("=" * 60)
println("VERIFICANDO CATEGORÍAS EN MONGODB")
println("=" * 60)

// Obtener todas las categorías
val docs = Await.result(collection.find().toFuture(), 10.seconds)

println(s"\nTotal de categorías: ${docs.size}\n")

docs.foreach { doc =>
  val id = doc.@@getLong("_id")
  val name = doc.getString("name")
  val parentId = try {
    val pid = doc.getLong("parentId")
    s"$pid"
  } catch {
    case _: Exception => 
      if (doc.containsKey("parentId")) "null" else "CAMPO NO EXISTE"
  }
  
  println(f"ID: $id%-4s | Name: $name%-20s | ParentID: $parentId")
}

println("\n" + "=" * 60)

mongoClient.close()

```


#### Short summary: 

empty definition using pc, found symbol in pc: 