package controllers

import http.{HttpRequest, HttpResponse}
import models.{MediaRepo, CategoryRepo, PromotionRepo, PromotionTarget}
import session.SessionManager
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
  private def serveHtml(filename: String, request: HttpRequest = null): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = s"$projectDir/app/views/$filename.html"

    Try(Source.fromFile(path, "UTF-8").mkString) match {
      case Success(html) => 
        val response = HttpResponse.ok(html)
        // Preservar la cookie de sesión si existe
        if (request != null && request.cookies.contains("sessionId")) {
          response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
        } else {
          response
        }
      case Failure(e) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${e.getMessage}<br>Ruta intentada: $path")
    }
  }

  /** GET /shop */
  def shop(request: HttpRequest): HttpResponse = {
    // 🔍 DEBUG: Ver qué cookie recibimos
    println(s"🔍 [SHOP] Cookies recibidas: ${request.cookies}")
    val sessionId = request.cookies.get("sessionId")
    println(s"🔍 [SHOP] SessionID: $sessionId")
    sessionId.foreach(sid => println(s"🔍 [SHOP] Sesión válida: ${SessionManager.isValidSession(sid)}"))
    
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val allMedia = MediaRepo.all
        val categories = CategoryRepo.all

        val categoryId = request.queryParams.get("category").flatMap(_.toLongOption)
        val filteredMedia = categoryId match {
          case Some(catId) => allMedia.filter(_.categoryId.contains(catId))
          case None => allMedia
        }

        // Generar navbar dinámico según el usuario
        val navbarButtons = if (user.isAdmin) {
          """<a class="btn btn-warning btn-sm" href="/admin">👨‍💼 Admin</a>
             <a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
             <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
        } else {
          """<a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
             <a class="btn btn-success btn-sm" href="/cart">🛒 Carrito</a>
             <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
        }

        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/media_list.html"
        
        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            // Reemplazar los botones del navbar
            val updatedHtml = html.replace(
              """<a class="btn btn-outline-light btn-sm" href="/login">🔑 Login</a>
        <a class="btn btn-warning btn-sm text-dark" href="/register">🧾 Registro</a>""",
              navbarButtons
            )
            
            val response = HttpResponse.ok(updatedHtml)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
          case Failure(e) =>
            HttpResponse.notFound(s"Error cargando tienda: ${e.getMessage}")
        }
        
      case Left(resp) => 
        println(s"🔴 [SHOP] requireAuth FALLÓ, redirigiendo a login")
        resp
    }
  }

  /** GET /shop/:id */
  def detail(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            // Buscar promoción activa para este producto (por producto O por categoría)
            import java.time.LocalDateTime
            val now = LocalDateTime.now()
            val activePromotion = PromotionRepo.all.find { promo =>
              val isActive = !promo.startDate.isAfter(now) && !promo.endDate.isBefore(now)
              if (!isActive) false
              else {
                promo.targetType match {
                  case PromotionTarget.Product => promo.targetIds.contains(media.id)
                  case PromotionTarget.Category => 
                    media.categoryId.exists(catId => promo.targetIds.contains(catId))
                  case _ => false
                }
              }
            }
            
            val (finalPrice, priceDisplay) = activePromotion match {
              case Some(promo) =>
                val discount = promo.discountPercent
                val discountedPrice = media.price * (100 - discount) / 100
                (discountedPrice, 
                 s"""<div class="mb-3">
                      <span class="badge bg-danger fs-5">🔥 ${discount}% OFF</span>
                    </div>
                    <div>
                      <span class="text-secondary text-decoration-line-through fs-6">Precio original: $$${media.price}</span><br>
                      <span class="text-warning fw-bold" style="font-size: 2rem;">$$${discountedPrice}</span>
                    </div>""")
              case None =>
                (media.price, s"""<span class="text-success fw-bold" style="font-size: 2rem;">$$${media.price}</span>""")
            }
            
            // Generar navbar dinámico
            val navbarButtons = if (user.isAdmin) {
              """<a class="btn btn-warning btn-sm" href="/admin">👨‍💼 Admin</a>
                 <a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
                 <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
            } else {
              """<a class="btn btn-info btn-sm text-white" href="/user/account">👤 Cuenta</a>
                 <a class="btn btn-success btn-sm" href="/cart">🛒 Carrito</a>
                 <a class="btn btn-danger btn-sm" href="/logout">🚪 Salir</a>"""
            }
            
            // Botón de compra (usuario logueado) - con precio final
            val purchaseButton = s"""
              <form method="POST" action="/shop/${media.id}/purchase" class="mb-3">
                <button type="submit" class="btn btn-primary btn-lg w-100 ${if (media.stock <= 0) "disabled" else ""}">
                  <i class="bi bi-cart-plus me-2"></i>Comprar ahora ($$${finalPrice})
                </button>
              </form>
              
              <button onclick="addToCart(${media.id})" class="btn btn-success w-100 mb-3 ${if (media.stock <= 0) "disabled" else ""}">
                <i class="bi bi-cart me-2"></i>Agregar al carrito
              </button>
            """

            val projectDir = System.getProperty("user.dir")
            val path = s"$projectDir/app/views/media_detail.html"
            
            Try(Source.fromFile(path, "UTF-8").mkString) match {
              case Success(html) =>
                // Reemplazar navbar, botón de compra y datos del producto
                val updatedHtml = html
                  .replace(
                    """<a class="btn btn-outline-light btn-sm" href="/login">
          🔑 Login
        </a>
        <a class="btn btn-warning btn-sm text-dark" href="/register">
          🧾 Registro
        </a>""",
                    navbarButtons
                  )
                  .replace(
                    """<a href="/login" class="btn btn-primary btn-lg w-100 mb-3">
          <i class="bi bi-box-arrow-in-right me-2"></i>Inicia sesión para comprar
        </a>""",
                    purchaseButton
                  )
                  .replace("/assets/images/placeholder.jpg", media.getCoverImageUrl)
                  .replace("🎵 Nombre del Producto", escapeHtml(media.title))
                  .replace("$99.99", priceDisplay)
                  .replace("Descripción detallada del producto. Aquí puedes incluir características, inspiración o información del autor.", 
                           escapeHtml(media.description))
                
                val response = HttpResponse.ok(updatedHtml)
                if (request.cookies.contains("sessionId")) {
                  response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
                } else {
                  response
                }
              case Failure(e) =>
                HttpResponse.notFound(s"Error cargando detalle: ${e.getMessage}")
            }
          case None => HttpResponse.notFound("<h1>Producto no encontrado</h1>")
        }
      case Left(resp) => resp
    }
  }
  
  /** Escapa HTML para prevenir XSS */
  private def escapeHtml(s: String): String =
    s.replace("&", "&amp;")
     .replace("<", "&lt;")
     .replace(">", "&gt;")
     .replace("\"", "&quot;")

  /** GET /cart */
  def viewCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val sessionId = request.cookies.getOrElse("sessionId", "")
        val cart = SessionCartRepo.get(sessionId)
        
        val html = serveHtml("cart", request).body
        
        // Inyectar datos del carrito
        val hasItems = !cart.isEmpty
        val cartItemsHtml = if (hasItems) {
          cart.items.map { case (mediaId, qty) =>
            MediaRepo.find(mediaId).map { media =>
              val subtotal = media.price * qty
              s"""
              <tr>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="ms-2">
                      <h6 class="mb-0">${media.title}</h6>
                      <small class="text-muted">${media.description.take(50)}...</small>
                    </div>
                  </div>
                </td>
                <td class="text-center align-middle">$$${media.price}</td>
                <td class="text-center align-middle">
                  <form method="POST" action="/cart/update/${media.id}" class="d-inline">
                    <div class="input-group input-group-sm">
                      <input type="number" name="quantity" class="form-control text-center" value="${qty}" min="1" max="${media.stock}">
                      <button type="submit" class="btn btn-sm btn-secondary">✓</button>
                    </div>
                  </form>
                </td>
                <td class="text-center align-middle"><span class="badge bg-success">${media.stock}</span></td>
                <td class="text-end align-middle"><strong>$$${subtotal}</strong></td>
                <td class="text-center align-middle">
                  <form method="POST" action="/cart/remove/${media.id}" class="d-inline">
                    <button type="submit" class="btn btn-sm btn-danger">🗑️</button>
                  </form>
                </td>
              </tr>
              """
            }.getOrElse("")
          }.mkString("\n")
        } else ""
        
        val totalPrice = cart.total
        val itemCount = cart.items.size
        
        val updatedHtml = html
          .replace("const hasItems = false;", s"const hasItems = ${hasItems};")
          .replace("📦 Productos en tu carrito (2)", s"📦 Productos en tu carrito ($itemCount)")
          .replace("<!-- Ejemplo Producto -->", cartItemsHtml)
          .replace("$180.00", s"$$$totalPrice")
          .replace("💵 Tu saldo actual: <strong>$500.00</strong>", s"💵 Tu saldo actual: <strong>$$${user.balance}</strong>")
        
        HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), updatedHtml)
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
          serveHtml("purchase_page", request)
      case Left(resp) => resp
    }
  }

  /** POST /purchase */
  def processPurchase(request: HttpRequest): HttpResponse = {
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

  /** POST /shop/:id/purchase - Compra directa de un producto */
  def purchaseItem(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            // Calcular precio final con promoción si aplica (por producto O por categoría)
            import java.time.LocalDateTime
            val now = LocalDateTime.now()
            val activePromotion = PromotionRepo.all.find { promo =>
              val isActive = !promo.startDate.isAfter(now) && !promo.endDate.isBefore(now)
              if (!isActive) false
              else {
                promo.targetType match {
                  case PromotionTarget.Product => promo.targetIds.contains(media.id)
                  case PromotionTarget.Category => 
                    media.categoryId.exists(catId => promo.targetIds.contains(catId))
                  case _ => false
                }
              }
            }
            
            val finalPrice = activePromotion match {
              case Some(promo) =>
                val discount = promo.discountPercent
                media.price * (100 - discount) / 100
              case None =>
                media.price
            }
            
            models.UserRepo.deductBalance(user.id, finalPrice) match {
              case Some(updatedUser) =>
                // TODO: Registrar transacción en TransactionRepo cuando esté implementado
                // TODO: Agregar media a UserDownloads cuando esté implementado
                HttpResponse.redirect(s"/shop/${id}?success=Compra+realizada.+Nuevo+saldo:+$$${updatedUser.balance}")
              case None =>
                HttpResponse.redirect(s"/shop/${id}?error=Saldo+insuficiente.+Necesitas+$$${media.price},+tienes+$$${user.balance}")
            }
          case None =>
            HttpResponse.redirect("/shop?error=Producto+no+encontrado")
        }
      case Left(resp) => resp
    }
  }
}
