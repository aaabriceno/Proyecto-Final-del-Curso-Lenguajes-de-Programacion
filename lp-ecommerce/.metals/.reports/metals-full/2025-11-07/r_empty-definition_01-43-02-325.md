error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -http/HttpResponse#
	 -HttpResponse#
	 -scala/Predef.HttpResponse#
offset: 5404
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala
text:
```scala
package controllers

import http.{HttpRequest, HttpResponse}
import models.{MediaRepo, CategoryRepo}
import scala.io.Source
import scala.util.{Try, Success, Failure}

/**
 * Controlador de la Tienda (Shop)
 */

// Carrito temporal por sesión
case class SessionCart(items: Map[Long, Int] = Map.empty) {
  def total: BigDecimal = items.map { case (id, qty) =>
    MediaRepo.find(id).map(_.price * qty).getOrElse(BigDecimal(0))
  }.sum

  def isEmpty: Boolean = items.isEmpty
}

object SessionCartRepo {
  private var carts = Map[String, SessionCart]()

  def get(sessionId: String): SessionCart =
    carts.getOrElse(sessionId, SessionCart())

  def add(sessionId: String, media: models.Media, qty: Int): Unit = {
    val cart = get(sessionId)
    val updated = cart.copy(items = cart.items.updated(
      media.id, cart.items.getOrElse(media.id, 0) + qty
    ))
    carts += sessionId -> updated
  }

  def remove(sessionId: String, id: Long): Unit = {
    val cart = get(sessionId)
    carts += sessionId -> cart.copy(items = cart.items - id)
  }

  def updateQuantity(sessionId: String, id: Long, qty: Int): Unit = {
    val cart = get(sessionId)
    carts += sessionId -> cart.copy(items = cart.items.updated(id, qty))
  }

  def clear(sessionId: String): Unit =
    carts -= sessionId
}

object ShopController {

  /** Sirve un archivo HTML desde /app/views/ */
  private def serveHtml(filename: String): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = s"$projectDir/app/views/$filename.html"

    Try(Source.fromFile(path, "UTF-8").mkString) match {
      case Success(html) => HttpResponse.ok(html)
      case Failure(e) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${e.getMessage}<br>Ruta intentada: $path")
    }
  }

  /** GET /shop */
  def shop(request: HttpRequest): HttpResponse = {
    val allMedia = MediaRepo.all
    val categories = CategoryRepo.all

    val categoryId = request.queryParams.get("category").flatMap(_.toLongOption)
    val filteredMedia = categoryId match {
      case Some(catId) => allMedia.filter(_.categoryId.contains(catId))
      case None => allMedia
    }

    serveHtml("media_list")
  }

  /** GET /shop/:id */
  def detail(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        MediaRepo.find(id) match {
          case Some(_) => serveHtml("media_detail")
          case None    => HttpResponse.notFound("<h1>Producto no encontrado</h1>")
        }
      case Left(resp) => resp
    }
  }

  /** GET /cart */
  def viewCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val _ = SessionCartRepo.get(sessionId)
        serveHtml("cart")
      case Left(resp) => resp
    }
  }

  /** POST /cart/add/:id */
  def addToCart(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            val sessionId = request.cookies.getOrElse("sessionId", "")
            SessionCartRepo.add(sessionId, media, 1)
            HttpResponse.redirect("/cart?success=Producto+agregado+al+carrito")
          case None =>
            HttpResponse.redirect("/shop?error=Producto+no+encontrado")
        }
      case Left(resp) => resp
    }
  }

  /** POST /cart/remove/:id */
  def removeFromCart(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.remove(sessionId, id)
        HttpResponse.redirect("/cart?success=Producto+eliminado")
      case Left(resp) => resp
    }
  }

  /** POST /cart/update/:id */
  def updateCartQuantity(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val qty = request.formData.get("quantity").flatMap(_.toIntOption).getOrElse(1)
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.updateQuantity(sessionId, id, qty)
        HttpResponse.redirect("/cart")
      case Left(resp) => resp
    }
  }

  /** POST /cart/clear */
  def clearCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.clear(sessionId)
        HttpResponse.redirect("/cart?success=Carrito+vaciado")
      case Left(resp) => resp
    }
  }

  /** GET /purchase */
  def purchasePage(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        if (cart.isEmpty)
          HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        else
          serveHtml("purchase_page")
      case Left(resp) => resp
    }
  }

  /** POST /purchase */
  def processPurchase(request: HttpRequest): HttpRes@@ponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        if (cart.isEmpty)
          HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        else {
          SessionCartRepo.clear(sessionId)
          HttpResponse.redirect("/shop?success=Compra+realizada+exitosamente")
        }
      case Left(resp) => resp
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.