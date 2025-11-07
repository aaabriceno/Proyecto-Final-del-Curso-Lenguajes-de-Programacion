error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala:getOrElse.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala
empty definition using pc, found symbol in pc: getOrElse.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -request/cookies/getOrElse.
	 -request/cookies/getOrElse#
	 -request/cookies/getOrElse().
	 -scala/Predef.request.cookies.getOrElse.
	 -scala/Predef.request.cookies.getOrElse#
	 -scala/Predef.request.cookies.getOrElse().
offset: 5832
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/ShopController.scala
text:
```scala
package controllers

import http.{HttpRequest, HttpResponse}
import models.{MediaRepo, CategoryRepo}
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

/**
 * Controlador de la Tienda (Shop)
 */

// Cart temporal para sesión
case class SessionCart(items: Map[Long, Int] = Map.empty) {
  def total: BigDecimal = items.map { case (id, qty) =>
    MediaRepo.find(id).map(_.price * qty).getOrElse(BigDecimal(0))
  }.sum
  def isEmpty: Boolean = items.isEmpty
}

object SessionCartRepo {
  private var carts = Map[String, SessionCart]()
  
  def get(sessionId: String): SessionCart = carts.getOrElse(sessionId, SessionCart())
  def add(sessionId: String, media: models.Media, qty: Int): Unit = {
    val cart = get(sessionId)
    val updated = cart.copy(items = cart.items.updated(media.id, cart.items.getOrElse(media.id, 0) + qty))
    carts = carts.updated(sessionId, updated)
  }
  def remove(sessionId: String, id: Long): Unit = {
    val cart = get(sessionId)
    carts = carts.updated(sessionId, cart.copy(items = cart.items - id))
  }
  def updateQuantity(sessionId: String, id: Long, qty: Int): Unit = {
    val cart = get(sessionId)
    carts = carts.updated(sessionId, cart.copy(items = cart.items.updated(id, qty)))
  }
  def clear(sessionId: String): Unit = {
    carts = carts - sessionId
  }
}

object ShopController {
  
  /**
   * Sirve un archivo HTML estático desde app/views/
   */
  private def serveHtml(filename: String): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = Paths.get(projectDir, "app", "views", s"$filename.html")
    
    Try(Files.readString(path)) match {
      case Success(html) => HttpResponse.ok(html)
      case Failure(exception) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${exception.getMessage}<br>Ruta intentada: $path")
    }
  }
  
  /**
   * Lista de productos en la tienda
   * GET /shop
   * PÚBLICO - no requiere autenticación
   */
  def shop(request: HttpRequest): HttpResponse = {
    // Obtener todos los medios y categorías
    val allMedia = MediaRepo.all
    val categories = CategoryRepo.all
    
    // Filtrar por categoría si se especifica
    val categoryId = request.queryParams.get("category").flatMap(_.toLongOption)
    val filteredMedia = categoryId match {
      case Some(catId) => allMedia.filter(_.categoryId.contains(catId))
      case None => allMedia
    }
    
    serveHtml("media_list")
  }
  
  /**
   * Detalle de un producto específico
   * GET /shop/:id
   */
  def detail(id: Long)(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            serveHtml("media_detail")
          case None =>
            HttpResponse.notFound("<h1>Producto no encontrado</h1>")
        }
        
      case Left(response) => response
    }
  }
  
  /**
   * Ver carrito de compras
   * GET /cart
   */
  def viewCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        
        serveHtml("cart")
        
      case Left(response) => response
    }
  }
  
  /**
   * Agregar producto al carrito
   * POST /cart/add/:id
   */
  def addToCart(id: Long)(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            val sessionId = request.cookies.getOrElse("sessionId", "")
            SessionCartRepo.add(sessionId, media, 1)
            HttpResponse.redirect("/cart?success=Producto+agregado+al+carrito")
            
          case None =>
            HttpResponse.redirect("/shop?error=Producto+no+encontrado")
        }
        
      case Left(response) => response
    }
  }
  
  /**
   * Eliminar producto del carrito
   * POST /cart/remove/:id
   */
  def removeFromCart(id: Long)(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.remove(sessionId, id)
        HttpResponse.redirect("/cart?success=Producto+eliminado")
        
      case Left(response) => response
    }
  }
  
  /**
   * Actualizar cantidad de producto en carrito
   * POST /cart/update/:id
   */
  def updateCartQuantity(id: Long)(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val quantity = request.formData.get("quantity").flatMap(_.toIntOption).getOrElse(1)
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.updateQuantity(sessionId, id, quantity)
        HttpResponse.redirect("/cart")
        
      case Left(response) => response
    }
  }
  
  /**
   * Vaciar carrito
   * POST /cart/clear
   */
  def clearCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        SessionCartRepo.clear(sessionId)
        HttpResponse.redirect("/cart?success=Carrito+vaciado")
        
      case Left(response) => response
    }
  }
  
  /**
   * Página de compra/checkout
   * GET /purchase
   */
  def purchasePage(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.get@@OrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        
        if (cart.isEmpty) {
          return HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        }
        
        serveHtml("purchase_page")
        
      case Left(response) => response
    }
  }
  
  /**
   * Procesar compra
   * POST /purchase
   */
  def processPurchase(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        
        if (cart.isEmpty) {
          return HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        }
        
        // TODO: Procesar la compra (deducir saldo, crear download, etc.)
        // Por ahora, solo limpiamos el carrito
        SessionCartRepo.clear(sessionId)
        
        HttpResponse.redirect("/shop?success=Compra+realizada+exitosamente")
        
      case Left(response) => response
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: getOrElse.