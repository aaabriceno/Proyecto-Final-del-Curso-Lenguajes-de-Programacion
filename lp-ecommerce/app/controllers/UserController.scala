package controllers

import http.{HttpRequest, HttpResponse}
import models.{UserRepo, BalanceRequestRepo, DownloadRepo, OrderRepo, Order, OrderItem, TransactionRepo, TransactionType, MediaRepo, Transaction}
import scala.io.Source
import scala.util.{Try, Success, Failure}
import java.net.URLEncoder
import java.time.temporal.ChronoUnit
import java.time.ZoneOffset

/**
 * Controlador de Usuario (perfil, saldo, descargas)
 */
object UserController {

  /** Sirve vistas HTML desde /app/views/ */
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
        HttpResponse.notFound(
          s"No se pudo cargar la página: ${e.getMessage}<br>Ruta intentada: $path"
        )
    }
  }

  /** GET /user/account */
  def account(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/user_account.html"
        val sessionId = request.cookies.getOrElse("sessionId", "")
        
        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            // Reemplazar datos hardcodeados con datos reales del usuario
            val updatedHtml = html
              .replace("Usuario Ejemplo", escapeHtml(user.name))
              .replace("usuario@ejemplo.com", escapeHtml(user.email))
              .replace("+123456789", escapeHtml(user.phone))
              .replace("$500.00", s"$$${user.balance}")
              .replace("$200.00", s"$$${user.totalSpent}")
              .replace("<!-- CSRF_TOKEN_PLACEHOLDER -->", session.CsrfProtection.hiddenFieldHtml(sessionId))
            
            val response = HttpResponse.ok(updatedHtml)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
          case Failure(e) =>
            HttpResponse.notFound(s"Error cargando cuenta: ${e.getMessage}")
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

  private def formatDateTime(dt: java.time.LocalDateTime): String =
    dt.toLocalDate.toString + " " + dt.toLocalTime.truncatedTo(ChronoUnit.MINUTES).toString

  /** GET /user/info */
  def info(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) => serveHtml("user_info", request)
      case Left(resp) => resp
    }
  }

  /** POST /user/info */
  def updateInfo(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val name = request.formData.getOrElse("name", user.name)
        val phone = request.formData.getOrElse("phone", user.phone)

        // Actualiza datos del usuario (cuando implementes la función)
        // UserRepo.updateInfo(user.id, name, phone)
        HttpResponse.redirect(
          "/user/info?success=" + URLEncoder.encode("Información actualizada", "UTF-8")
        )

      case Left(resp) => resp
    }
  }

  /** GET /user/downloads */
  def downloads(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val _ = DownloadRepo.findByUserId(user.id)
        serveHtml("user_downloads", request)
      case Left(resp) => resp
    }
  }

  /** GET /user/orders */
  def orders(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val savedOrders = OrderRepo.findByUser(user.id, 50)
        val legacyOrders = legacyOrdersForUser(user.id)
        val orders = sortOrdersByDate(savedOrders ++ legacyOrders)
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/user_orders.html"

        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            val content =
              if (orders.isEmpty) {
                """<p class="text-center text-muted mb-0">Aún no realizaste compras.</p>"""
              } else {
                orders.map(renderOrderCard).mkString("\n")
              }

            val updated = html
              .replace("<!-- ORDERS_CONTENT -->", content)
              .replace("data-order-count>0 compras", s"data-order-count>${orders.size} compras")

            HttpResponse.ok(updated)
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar la vista de compras: ${e.getMessage}")
        }
      case Left(resp) => resp
    }
  }

  private def renderOrderCard(order: Order): String = {
    val itemsHtml = order.items.map { item =>
      s"""
         |<li class="d-flex justify-content-between align-items-start py-2 border-bottom border-secondary">
         |  <div>
         |    <strong>${escapeHtml(item.title)}</strong><br>
         |    <small class="text-muted">${item.quantity} x ${formatMoney(item.unitPrice)} · ${item.productType.asString.toUpperCase}</small>
         |  </div>
         |  <div class="text-end">
         |    <span class="fw-semibold text-warning">${formatMoney(item.netAmount)}</span><br>
         |    ${if (item.discount > 0) s"<small class='text-success'>Desc: ${formatMoney(item.discount)}</small>" else ""}
         |  </div>
         |</li>
       """.stripMargin
    }.mkString("\n")

    s"""
       |<div class="bg-dark border border-secondary rounded-3 p-3 mb-3">
       |  <div class="bg-light text-dark border border-secondary rounded-3 p-3">
       |    <div class="d-flex justify-content-between flex-wrap gap-2">
       |      <div>
       |        <strong>Orden #${order.id}</strong><br>
       |        <small class="text-muted">${formatDateTime(order.createdAt)}</small>
       |      </div>
       |      <div class="text-end">
       |        <span class="text-muted d-block">Total pagado</span>
       |        <span class="fs-5 text-warning fw-semibold">${formatMoney(order.totalNet)}</span>
       |      </div>
       |    </div>
       |    <hr>
       |    <ul class="list-unstyled mb-0">
       |      $itemsHtml
       |    </ul>
       |    <div class="mt-3 small text-muted d-flex justify-content-between">
       |      <span>Subtotal: ${formatMoney(order.totalGross)}</span>
       |      <span>Descuento: ${formatMoney(order.totalDiscount)}</span>
       |    </div>
       |  </div>
      |</div>
     """.stripMargin
  }

  private def sortOrdersByDate(orders: Vector[Order]): Vector[Order] =
    orders.sortBy(_.createdAt.toEpochSecond(ZoneOffset.UTC))(Ordering[Long].reverse)

  private def legacyOrdersForUser(userId: Long): Vector[Order] = {
    TransactionRepo.purchasesWithoutOrder(userId, limit = 50).flatMap(legacyOrderFrom)
  }

  private def legacyOrderFrom(tx: Transaction): Option[Order] = {
    for {
      buyerId <- tx.fromUserId
      mediaId <- tx.mediaId
      media <- MediaRepo.find(mediaId)
    } yield {
      val item = OrderItem(
        mediaId = media.id,
        title = media.title,
        quantity = tx.quantity,
        unitPrice = tx.grossAmount / BigDecimal(tx.quantity).max(BigDecimal(1)),
        discount = tx.discount,
        netAmount = tx.netAmount,
        productType = media.productType
      )
      Order(
        id = -tx.id, // IDs negativos para diferenciar de órdenes persistidas
        userId = buyerId,
        items = Vector(item),
        totalGross = tx.grossAmount,
        totalDiscount = tx.discount,
        totalNet = tx.netAmount,
        createdAt = tx.createdAt
      )
    }
  }

  /** GET /user/balance/request */
  def balanceRequestForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        HttpResponse.ok(
          """<!DOCTYPE html>
<html>
<head>
  <title>Solicitar Recarga</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
</head>
<body class="bg-dark text-light">
  <div class="container mt-5">
    <h2>Solicitar Recarga de Saldo</h2>
    <form method="POST" action="/user/balance/request">
      <div class="mb-3">
        <label>Monto a recargar:</label>
        <input type="number" name="amount" class="form-control" step="0.01" required>
      </div>
      <button type="submit" class="btn btn-primary">Solicitar</button>
    </form>
  </div>
</body>
</html>"""
        )
      case Left(resp) => resp
    }
  }

  /** POST /user/balance/request */
  def createBalanceRequest(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val csrfToken = request.formData.getOrElse("csrfToken", "")
        val sessionId = request.cookies.getOrElse("sessionId", "")
        
        // 🔒 Validación CSRF obligatoria
        if (!session.CsrfProtection.validateToken(sessionId, csrfToken))
          return HttpResponse.redirect(
            "/user/account?error=" + URLEncoder.encode("Token CSRF inválido", "UTF-8")
          )
        
        val amount = request.formData.get("amount").flatMap(_.toDoubleOption).getOrElse(0.0)
        val paymentMethod = request.formData.getOrElse("payment_method", "transferencia")

        if (amount <= 0)
          return HttpResponse.redirect(
            "/user/account?error=" + URLEncoder.encode("Monto inválido", "UTF-8")
          )

        BalanceRequestRepo.add(user.id, BigDecimal(amount), paymentMethod)

        HttpResponse.redirect(
          "/user/account?success=" + URLEncoder.encode(
            "Solicitud enviada. Espera aprobación del administrador",
            "UTF-8"
          )
        )

      case Left(resp) => resp
    }
  }

  /** GET /user/transactions */
  def transactions(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) => serveHtml("transacciones", request)
      case Left(resp) => resp
    }
  }
}
