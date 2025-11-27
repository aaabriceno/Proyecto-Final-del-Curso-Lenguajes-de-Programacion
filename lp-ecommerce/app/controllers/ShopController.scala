package controllers

import http.{HttpRequest, HttpResponse}
import java.net.URLEncoder
import models.{MediaRepo, CategoryRepo, PromotionRepo, PromotionTarget, CartRepo, DownloadRepo, UserRepo, Media, User, TransactionRepo, TransactionType, ProductType, OrderRepo, OrderItem}
import services.ReceiptService
import session.SessionManager
import scala.io.Source
import scala.util.{Try, Success, Failure}
import scala.math.BigDecimal.RoundingMode

/**
 * Controlador de la Tienda (Shop)
 */

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
    //  DEBUG: Ver qué cookie recibimos
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
            val updatedHtml = html.replace("<!-- NAVBAR_BUTTONS -->", navbarButtons)
            
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
        println(s" [SHOP] requireAuth FALLÓ, redirigiendo a login")
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
            
            val actionBlock =
              if (user.isAdmin) {
                s"""
                <a href="/admin/media/${media.id}/edit" class="btn btn-warning w-100 mb-3">
                  <i class="bi bi-pencil-square me-2"></i>Editar producto
                </a>
                """
              } else {
                s"""
                <form method="POST" action="/shop/${media.id}/purchase" class="mb-3">
                  <button type="submit" class="btn btn-primary btn-lg w-100 ${if (media.isOutOfStock) "disabled" else ""}">
                    <i class="bi bi-cart-plus me-2"></i>Comprar ahora ($$${finalPrice})
                  </button>
                </form>
                <button type="button" class="btn btn-warning w-100 mb-3 ${if (media.isOutOfStock) "disabled" else ""}" data-gift-button data-media-id="${media.id}" data-media-title="${escapeHtml(media.title)}">
                  <i class="bi bi-gift me-2"></i>Regalar este producto
                </button>
                <button onclick="addToCart(${media.id})" class="btn btn-success w-100 mb-3 ${if (media.isOutOfStock) "disabled" else ""}">
                  <i class="bi bi-cart me-2"></i>Agregar al carrito
                </button>
                """
              }

            val projectDir = System.getProperty("user.dir")
            val path = s"$projectDir/app/views/media_detail.html"

            val assetUrl = {
              val raw = media.assetPath
              if (raw.startsWith("/assets/")) raw else s"/assets/${raw.stripPrefix("/")}"
            }

            val previewHtml: String = {
              val lower = assetUrl.toLowerCase
              if (media.productType == ProductType.Digital && (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".flac"))) {
                s"""
                   |<div class="card bg-dark border-secondary shadow-sm p-4">
                   |  <h5 class="mb-3">🎧 Previsualización de audio</h5>
                   |  <audio controls class="w-100">
                   |    <source src="$assetUrl">
                   |    Tu navegador no soporta audio HTML5.
                   |  </audio>
                   |</div>
                   |""".stripMargin
              } else if (media.productType == ProductType.Digital && (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".mkv"))) {
                s"""
                   |<div class="ratio ratio-16x9 shadow-sm">
                   |  <video src="$assetUrl" class="w-100 h-100 rounded" controls preload="metadata">
                   |    Tu navegador no soporta video HTML5.
                   |  </video>
                   |</div>
                   |""".stripMargin
              } else {
                s"""
                   |<div class="ratio ratio-16x9 shadow-sm">
                   |  <img src="${media.getCoverImageUrl}" alt="Producto" class="img-fluid rounded object-fit-cover">
                   |</div>
                   |""".stripMargin
              }
            }

            Try(Source.fromFile(path, "UTF-8").mkString) match {
              case Success(html) =>
                // Reemplazar navbar, botón de compra, datos del producto y previsualización
                val errorMsgJs = request.queryParams.get("error").map(escapeJsString).getOrElse("")
                val updatedHtml = html
                  .replace("<!-- NAVBAR_PLACEHOLDER -->", navbarButtons)
                  .replace("<!-- CTA_PLACEHOLDER -->", actionBlock)
                  .replace("<!-- MEDIA_PREVIEW_PLACEHOLDER -->", previewHtml)
                  .replace("🎵 Nombre del Producto", escapeHtml(media.title))
                  .replace("$99.99", priceDisplay)
                  .replace("Descripción detallada del producto. Aquí puedes incluir características, inspiración o información del autor.",
                           escapeHtml(media.description))
                  .replace("__DETAIL_ERROR__", errorMsgJs)
                
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

  private def formatMoney(amount: BigDecimal): String =
    f"$$${amount}%.2f"

  private def formatDiscount(amount: BigDecimal): String =
    if (amount <= 0) "$0.00" else s"-${formatMoney(amount)}"

  private case class PricingResult(unitPrice: BigDecimal, discountPerUnit: BigDecimal)

  private def escapeJsString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ")

  private def registerTransaction(
    transactionType: TransactionType,
    fromUserId: Option[Long],
    toUserId: Option[Long],
    media: Media,
    quantity: Int,
    grossAmount: BigDecimal,
    discount: BigDecimal,
    referenceId: Option[Long] = None,
    note: Option[String] = None,
    orderId: Option[Long] = None
  ): Unit = {
    TransactionRepo.create(
      transactionType = transactionType,
      fromUserId = fromUserId,
      toUserId = toUserId,
      mediaId = Some(media.id),
      quantity = quantity,
      grossAmount = grossAmount,
      discount = discount,
      referenceId = referenceId,
      notes = note.filter(_.nonEmpty),
      orderId = orderId
    )
  }

  private def registerDigitalDownload(
    media: Media,
    userId: Long,
    quantity: Int,
    lineDiscount: BigDecimal
  ): Unit = {
    if (media.productType == ProductType.Digital) {
      DownloadRepo.add(userId, media.id, quantity, media.price, lineDiscount)
    }
  }

  private def calculatePricing(media: Media, user: User): PricingResult = {
    val basePrice = media.activePromotion.map(_.applyDiscount(media.price)).getOrElse(media.price)
    val vipPrice =
      if (media.activePromotion.isEmpty && user.totalSpent >= 100)
        (basePrice * BigDecimal(0.80)).setScale(2, RoundingMode.HALF_UP)
      else
        basePrice.setScale(2, RoundingMode.HALF_UP)

    val discountPerUnit = (media.price - vipPrice).max(BigDecimal(0)).setScale(2, RoundingMode.HALF_UP)
    PricingResult(vipPrice, discountPerUnit)
  }

  /** GET /cart */
  def viewCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val html = serveHtml("cart", request).body
        val cartItems = CartRepo.entriesWithMedia(user.id)
        val hasItems = cartItems.nonEmpty
        val subtotalAmount = cartItems.map { case (entry, media) => media.price * entry.quantity }.sum
        val discountAmount = BigDecimal(0)
        val totalToPay = subtotalAmount - discountAmount

        val cartRows = if (hasItems) {
          cartItems.map { case (entry, media) =>
            val subtotal = media.price * entry.quantity
            val maxAttr = if (media.managesStock) s" max='${media.stock}'" else ""
            val stockBadgeClass =
              if (!media.managesStock) "bg-info"
              else if (media.isOutOfStock) "bg-danger"
              else "bg-success"
            val stockLabel =
              if (!media.managesStock) "ILIMITADO"
              else if (media.isOutOfStock) "AGOTADO"
              else media.stock.toString
            s"""
              <tr>
                <td>
                  <div class="d-flex align-items-center">
                    <div class="ms-2">
                      <h6 class="mb-0">${escapeHtml(media.title)}</h6>
                      <small class="text-muted">${escapeHtml(media.description.take(50))}...</small>
                    </div>
                  </div>
                </td>
                <td class="text-center align-middle">${formatMoney(media.price)}</td>
                <td class="text-center align-middle">
                  <form method="POST" action="/cart/update/${media.id}" class="d-inline">
                    <div class="input-group input-group-sm">
                      <input type="number" name="quantity" class="form-control text-center" value="${entry.quantity}" min="1"${maxAttr}>
                      <button type="submit" class="btn btn-sm btn-secondary">&#10003;</button>
                    </div>
                  </form>
                </td>
                <td class="text-center align-middle"><span class="badge ${stockBadgeClass}">${stockLabel}</span></td>
                <td class="text-end align-middle"><strong>${formatMoney(subtotal)}</strong></td>
                <td class="text-center align-middle">
                  <div class="btn-group" role="group">
                    <button type="button" class="btn btn-sm btn-warning" data-gift-button data-media-id="${media.id}" data-media-title="${escapeHtml(media.title)}">&#127873;</button>
                    <form method="POST" action="/cart/remove/${media.id}" class="d-inline">
                      <button type="submit" class="btn btn-sm btn-danger">&#128465;</button>
                    </form>
                  </div>
                </td>
              </tr>
            """
          }.mkString("\n")
        } else {
          """
          <tr>
            <td colspan="6" class="text-center text-muted">Tu carrito esta vacio.</td>
          </tr>
          """
        }
        val updatedHtml = html
          .replace("const hasItems = false;", s"const hasItems = ${hasItems};")
          .replace("__ITEM_COUNT__", cartItems.size.toString)
          .replace("<!-- CART_ROWS -->", cartRows)
          .replace("__SUBTOTAL__", formatMoney(subtotalAmount))
          .replace("__DISCOUNT__", formatDiscount(discountAmount))
          .replace("__TOTAL__", formatMoney(totalToPay))
          .replace("__BALANCE__", formatMoney(user.balance))

        val errorMsgJs = request.queryParams.get("error").map(escapeJsString).getOrElse("")
        val finalHtml = updatedHtml.replace("__CART_ERROR__", errorMsgJs)

        HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), finalHtml)
      case Left(resp) => resp
    }
  }

  /** POST /cart/add/:id */
  def addToCart(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        CartRepo.addOrIncrement(user.id, id, 1) match {
          case Right(_) => HttpResponse.redirect("/cart?success=Producto+agregado+al+carrito")
          case Left(error) => HttpResponse.redirect("/shop?error=" + java.net.URLEncoder.encode(error, "UTF-8"))
        }
      case Left(resp) => resp
    }
  }

  /** POST /cart/remove/:id */
  def removeFromCart(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        CartRepo.remove(user.id, id)
        HttpResponse.redirect("/cart?success=Producto+eliminado")
      case Left(resp) => resp
    }
  }

  /** POST /cart/update/:id */
  def updateCartQuantity(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val qty = request.formData.get("quantity").flatMap(_.toIntOption).getOrElse(1)
        CartRepo.setQuantity(user.id, id, qty)
        HttpResponse.redirect("/cart")
      case Left(resp) => resp
    }
  }

  /** POST /cart/clear */
  def clearCart(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        CartRepo.clear(user.id)
        HttpResponse.redirect("/cart?success=Carrito+vaciado")
      case Left(resp) => resp
    }
  }

  /** GET /purchase */
  def purchasePage(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val cartItems = CartRepo.entriesWithMedia(user.id)
        if (cartItems.isEmpty)
          HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        else
          serveHtml("purchase_page", request)
      case Left(resp) => resp
    }
  }

  /** POST /purchase */
  def processPurchase(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val cartItems = CartRepo.entriesWithMedia(user.id)
        if (cartItems.isEmpty)
          HttpResponse.redirect("/cart?error=El+carrito+est%C3%A1+vac%C3%ADo")
        else {
          cartItems.find { case (_, media) => media.isOutOfStock } match {
            case Some((_, media)) =>
              HttpResponse.redirect("/cart?error=" + URLEncoder.encode(s"${media.title} no tiene stock suficiente", "UTF-8"))
            case None =>
              val pricingData = cartItems.map { case (entry, media) =>
                val pricing = calculatePricing(media, user)
                val originalTotal = media.price * entry.quantity
                val finalTotal = pricing.unitPrice * entry.quantity
                (entry, media, pricing, originalTotal, finalTotal)
              }

              val totalOriginal = pricingData.map(_._4).foldLeft(BigDecimal(0))(_ + _)
              val totalFinal = pricingData.map(_._5).foldLeft(BigDecimal(0))(_ + _)
              val discountAmount = (totalOriginal - totalFinal).max(BigDecimal(0)).setScale(2, RoundingMode.HALF_UP)

              def completePurchase(userAfterCharge: User): HttpResponse = {
                val processed = scala.collection.mutable.ListBuffer.empty[(Long, Int)]
                val processedItems = scala.collection.mutable.ListBuffer.empty[(Media, Int, BigDecimal)]
                val result = pricingData.foldLeft[Either[String, Unit]](Right(())) {
                  case (Left(err), _) => Left(err)
                  case (Right(_), (entry, media, pricing, _, _)) =>
                    MediaRepo.reduceStock(media.id, entry.quantity) match {
                      case Right(_) =>
                        processed += media.id -> entry.quantity
                        val lineDiscount = (pricing.discountPerUnit * BigDecimal(entry.quantity)).setScale(2, RoundingMode.HALF_UP)
                        processedItems += ((media, entry.quantity, lineDiscount))
                        Right(())
                      case Left(errorMsg) =>
                        Left(errorMsg)
                    }
                }

                result match {
                  case Left(errorMsg) =>
                    processed.foreach { case (mediaId, qty) => MediaRepo.addStock(mediaId, qty) }
                    if (totalFinal > 0) UserRepo.refundBalance(user.id, totalFinal)
                    HttpResponse.redirect("/cart?error=" + URLEncoder.encode(s"No se pudo completar la compra: $errorMsg", "UTF-8"))
                  case Right(_) =>
                    val orderItems = processedItems.toVector.map { case (media, quantity, lineDiscount) =>
                      val gross = media.price * quantity
                      OrderItem(
                        mediaId = media.id,
                        title = media.title,
                        quantity = quantity,
                        unitPrice = media.price,
                        discount = lineDiscount,
                        netAmount = (gross - lineDiscount).max(BigDecimal(0)),
                        productType = media.productType
                      )
                    }
                    val order = OrderRepo.create(user.id, orderItems)
                    ReceiptService.ensureReceiptFor(order)
                    processedItems.foreach { case (media, quantity, lineDiscount) =>
                      registerTransaction(
                        transactionType = TransactionType.Purchase,
                        fromUserId = Some(user.id),
                        toUserId = None,
                        media = media,
                        quantity = quantity,
                        grossAmount = media.price * quantity,
                        discount = lineDiscount,
                        referenceId = None,
                        note = Some("Compra carrito"),
                        orderId = Some(order.id)
                      )
                      registerDigitalDownload(media, user.id, quantity, lineDiscount)
                    }
                    CartRepo.clear(user.id)
                    val successMsg = s"Compra realizada por ${formatMoney(totalFinal)}. Descuento aplicado: ${formatMoney(discountAmount)}"
                    HttpResponse.redirect("/shop?success=" + URLEncoder.encode(successMsg, "UTF-8"))
                }
              }

              if (totalFinal > 0) {
                UserRepo.deductBalance(user.id, totalFinal) match {
                  case Some(updatedUser) => completePurchase(updatedUser)
                  case None =>
                    HttpResponse.redirect("/cart?error=" + URLEncoder.encode("Saldo insuficiente para completar la compra", "UTF-8"))
                }
              } else {
                completePurchase(user)
              }
          }
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
            if (media.isOutOfStock)
              return HttpResponse.redirect(s"/shop/${id}?error=Sin+stock+disponible")

            val pricing = calculatePricing(media, user)
            val quantity = 1
            val lineDiscount = (pricing.discountPerUnit * BigDecimal(quantity)).setScale(2, RoundingMode.HALF_UP)
            val finalPrice = pricing.unitPrice * quantity

            UserRepo.deductBalance(user.id, finalPrice) match {
              case Some(updatedUser) =>
                MediaRepo.reduceStock(media.id, quantity) match {
                  case Right(_) =>
                    val orderItem = OrderItem(
                      mediaId = media.id,
                      title = media.title,
                      quantity = quantity,
                      unitPrice = media.price,
                      discount = lineDiscount,
                      netAmount = (media.price * quantity - lineDiscount).max(BigDecimal(0)),
                      productType = media.productType
                    )
                    val order = OrderRepo.create(user.id, Vector(orderItem))
                    ReceiptService.ensureReceiptFor(order)
                    registerTransaction(
                      transactionType = TransactionType.Purchase,
                      fromUserId = Some(user.id),
                      toUserId = None,
                      media = media,
                      quantity = quantity,
                      grossAmount = media.price * quantity,
                      discount = lineDiscount,
                      referenceId = None,
                      note = Some("Compra directa"),
                      orderId = Some(order.id)
                    )
                    registerDigitalDownload(media, user.id, quantity, lineDiscount)
                    HttpResponse.redirect(s"/shop/${id}?success=Compra+realizada.+Nuevo+saldo:+$$${updatedUser.balance}")
                  case Left(errorMsg) =>
                    UserRepo.refundBalance(user.id, finalPrice)
                    HttpResponse.redirect(s"/shop/${id}?error=" + URLEncoder.encode(errorMsg, "UTF-8"))
                }
              case None =>
                HttpResponse.redirect(s"/shop/${id}?error=Saldo+insuficiente.+Necesitas+$$${finalPrice},+tienes+$$${user.balance}")
            }
          case None =>
            HttpResponse.redirect("/shop?error=Producto+no+encontrado")
        }
      case Left(resp) => resp
    }
  }
}
