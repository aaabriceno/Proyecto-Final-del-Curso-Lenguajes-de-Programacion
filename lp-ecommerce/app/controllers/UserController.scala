package controllers

import http.{HttpRequest, HttpResponse}
import models.{UserRepo, BalanceRequestRepo, DownloadRepo, OrderRepo, Order, OrderItem, TransactionRepo, TransactionType, MediaRepo, Transaction, NotificationRepo, Notification, NotificationType, Download, ProductType}
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

  private def escapeJsonValue(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

  private def formatMoney(amount: BigDecimal): String =
    f"$$${amount}%.2f"

  private def formatDateTime(dt: java.time.LocalDateTime): String =
    dt.toLocalDate.toString + " " + dt.toLocalTime.truncatedTo(ChronoUnit.MINUTES).toString

  /** GET /user/info */
  def info(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/user_info.html"

        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            val sessionId = request.cookies.getOrElse("sessionId", "")
            val updated = html
              .replace("__USER_NAME__", escapeHtml(user.name))
              .replace("__USER_EMAIL__", escapeHtml(user.email))
              .replace("__USER_PHONE__", escapeHtml(user.phone))
              .replace("<!-- CSRF_TOKEN_PLACEHOLDER -->", session.CsrfProtection.hiddenFieldHtml(sessionId))

            val response = HttpResponse.ok(updated)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar la página de perfil: ${e.getMessage}")
        }
      case Left(resp) => resp
    }
  }

  /** POST /user/info */
  def updateInfo(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val name  = request.formData.getOrElse("name", user.name).trim
        val phone = request.formData.getOrElse("phone", user.phone).trim

        UserRepo.updateBasicInfo(user.id, name, phone)
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
        val downloads = DownloadRepo.findByUserId(user.id)
        val templatePath = s"${System.getProperty("user.dir")}/app/views/user_downloads.html"

        Try(Source.fromFile(templatePath, "UTF-8").mkString) match {
          case Success(html) =>
            val totalUnits = downloads.map(_.quantity).sum
            val downloadCountLabel = s"${downloads.size} descargas"
            val vipBanner = buildVipBanner(user.totalSpent, totalUnits)
            val table = renderDownloadsTable(downloads)
            val updated = html
              .replace("__BALANCE__", formatMoney(user.balance))
              .replace("__TOTAL_SPENT__", formatMoney(user.totalSpent))
              .replace("__TOTAL_DOWNLOADS__", totalUnits.toString)
              .replace("__DOWNLOAD_COUNT__", downloadCountLabel)
              .replace("__VIP_BANNER__", vipBanner)
              .replace("__DOWNLOADS_TABLE__", table)

            HttpResponse.ok(updated)

          case Failure(err) =>
            HttpResponse.notFound(s"No se pudo cargar la vista de descargas: ${err.getMessage}")
        }
      case Left(resp) => resp
    }
  }

  /** GET /user/password */
  def changePasswordForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/user_change_password.html"
        val sessionId = request.cookies.getOrElse("sessionId", "")

        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            val updated = html.replace("<!-- CSRF_TOKEN_PLACEHOLDER -->", session.CsrfProtection.hiddenFieldHtml(sessionId))
            val response = HttpResponse.ok(updated)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar la página de cambio de contraseña: ${e.getMessage}")
        }
      case Left(resp) => resp
    }
  }

  /** POST /user/password */
  def changePassword(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val current = request.formData.getOrElse("currentPassword", "")
        val next    = request.formData.getOrElse("newPassword", "")
        val confirm = request.formData.getOrElse("confirmPassword", "")

        if (next.length < 6) {
          return HttpResponse.redirect("/user/password?error=" + URLEncoder.encode("La nueva contraseña debe tener al menos 6 caracteres", "UTF-8"))
        }
        if (next != confirm) {
          return HttpResponse.redirect("/user/password?error=" + URLEncoder.encode("La nueva contraseña y su confirmación no coinciden", "UTF-8"))
        }

        val approvedReqOpt = models.PasswordResetRequestRepo.findApprovedForUser(user.id)

        approvedReqOpt match {
          case Some(req) =>
            UserRepo.forceChangePassword(user.id, next)
            models.PasswordResetRequestRepo.markCompleted(req.id)
            HttpResponse.redirect("/user/password?success=" + URLEncoder.encode("Contraseña actualizada tras aprobación del administrador", "UTF-8"))
          case None =>
            UserRepo.changePassword(user.id, current, next) match {
              case Left(msg) =>
                HttpResponse.redirect("/user/password?error=" + URLEncoder.encode(msg, "UTF-8"))
              case Right(_)  =>
                HttpResponse.redirect("/user/password?success=" + URLEncoder.encode("Contraseña actualizada correctamente", "UTF-8"))
            }
        }

      case Left(resp) => resp
    }
  }

  /** POST /user/password/request - solicitud al admin con sesión iniciada */
  def requestPasswordChange(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val notes = request.formData.get("notes").map(_.trim).filter(_.nonEmpty)
        val existingPending = models.PasswordResetRequestRepo.findPending().exists(_.userId == user.id)
        if (existingPending) {
          return HttpResponse.redirect("/user/password?error=" + URLEncoder.encode("Ya tienes una solicitud de cambio de contraseña pendiente", "UTF-8"))
        }
        val req = models.PasswordResetRequestRepo.create(user.id, notes)
        NotificationRepo.create(
          user.id,
          s"Tu solicitud de cambio de contraseña (#${req.id}) fue registrada y está pendiente de revisión.",
          NotificationType.Info
        )
        // Notificar a todos los administradores que existe una nueva solicitud
        UserRepo.all.filter(_.isAdmin).foreach { adminUser =>
          NotificationRepo.create(
            adminUser.id,
            s"Nuevo pedido de cambio de contraseña del usuario ${user.email} (#${req.id}).",
            NotificationType.Info
          )
        }
        HttpResponse.redirect("/user/password?success=" + URLEncoder.encode("Solicitud enviada al administrador", "UTF-8"))
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

  def searchUsers(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val query = request.queryParams.get("q").map(_.trim.toLowerCase).getOrElse("")
        val results =
          if (query.length < 2) Vector.empty
          else UserRepo.all
            .filter(u => u.id != user.id && u.isActive &&
              (u.name.toLowerCase.contains(query) || u.email.toLowerCase.contains(query)))
            .take(10)

        val body = results.map { u =>
          s"""{"name":"${escapeJsonValue(u.name)}","email":"${escapeJsonValue(u.email)}"}"""
        }.mkString("[", ",", "]")

        HttpResponse.json(body)
      case Left(resp) => resp
    }
  }

  def notificationsPage(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val templatePath = s"${System.getProperty("user.dir")}/app/views/user_notifications.html"
        val html = Try(Source.fromFile(templatePath, "UTF-8").mkString).getOrElse("<h1>Error cargando vista</h1>")
        val notifications = NotificationRepo.getByUser(user.id, 50)
        val content = renderNotifications(notifications)
        HttpResponse.ok(html.replace("<!-- NOTIFICATIONS_PLACEHOLDER -->", content))
      case Left(resp) => resp
    }
  }

  def notificationsFeed(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val unread = NotificationRepo.countUnread(user.id)
        val notifications = NotificationRepo.getByUser(user.id, 20)
        val jsonItems = notifications.map { n =>
          s"""{"id":${n.id},"message":"${escapeJsonValue(n.message)}","type":"${n.notificationType.asString}","read":${n.read}}"""
        }.mkString("[", ",", "]")
        val body = s"""{"count":$unread,"notifications":$jsonItems}"""
        HttpResponse.json(body)
      case Left(resp) => resp
    }
  }

  def notificationsMarkRead(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        NotificationRepo.markAsRead(id, user.id)
        HttpResponse.json("{\"success\":true}")
      case Left(resp) => resp
    }
  }

  def notificationsMarkAll(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        NotificationRepo.markAllAsRead(user.id)
        HttpResponse.json("{\"success\":true}")
      case Left(resp) => resp
    }
  }

  private def buildVipBanner(totalSpent: BigDecimal, totalUnits: Int): String = {
    if (totalSpent >= BigDecimal(1000)) {
      """
        |<div class="alert alert-warning d-flex align-items-center gap-2" role="alert">
        |  <i class="bi bi-stars fs-4 text-warning"></i>
        |  <div>
        |    <strong>¡Nivel VIP desbloqueado!</strong><br>
        |    Tienes beneficios permanentes y 20% de descuento automático en contenidos digitales.
        |  </div>
        |</div>
      """.stripMargin
    } else if (totalUnits > 0) {
      s"""
        |<div class="alert alert-info" role="alert">
        |  <strong>Gracias por tus compras.</strong> Llevas $totalUnits descargas en tu cuenta. Sigue explorando nuevos contenidos digitales.
        |</div>
      """.stripMargin
    } else ""
  }

  private def renderDownloadsTable(downloads: Vector[Download]): String = {
    if (downloads.isEmpty) {
      return """
        |<div class="text-center text-muted py-5">
        |  <i class="bi bi-cloud-arrow-down display-4 mb-3"></i>
        |  <p class="mb-3">Todavía no tienes descargas registradas.</p>
        |  <a href="/shop" class="btn btn-primary">Explorar productos</a>
        |</div>
      """.stripMargin
    }

    val rows = downloads.map { download =>
      val mediaOpt = MediaRepo.find(download.mediaId)
      val title = mediaOpt.map(m => escapeHtml(m.title)).getOrElse(s"Producto #${download.mediaId}")
      val badge = downloadTypeBadge(mediaOpt.map(_.productType))
      val code = escapeHtml(download.uniqueCode.take(12))
      val discountCell =
        if (download.discount > 0) formatMoney(download.discount)
        else "<span class=\"text-muted\">—</span>"
      val downloadAction = mediaOpt match {
        case Some(media) if media.productType == ProductType.Digital =>
          val assetPath =
            if (media.assetPath.startsWith("/assets/")) media.assetPath
            else s"/assets/${media.assetPath.stripPrefix("/")}"
          s"""<a class="btn btn-sm btn-primary" href="$assetPath" download>
             |  <i class="bi bi-download"></i> Descargar
             |</a>""".stripMargin
        case _ =>
          "<span class=\"text-muted\">N/A</span>"
      }

      s"""
        |<tr>
        |  <td>
        |    <strong>$title</strong><br>
        |    <small class="text-muted">Código: $code</small><br>
        |    $badge
        |  </td>
        |  <td class="text-center">${download.quantity}</td>
        |  <td class="text-center">${formatMoney(download.price)}</td>
        |  <td class="text-center">$discountCell</td>
        |  <td class="text-end">${formatMoney(download.finalPrice)}</td>
        |  <td class="text-end">${formatDateTime(download.downloadDate)}</td>
        |  <td class="text-end">$downloadAction</td>
      |</tr>
      """.stripMargin
    }.mkString("\n")

    s"""
      |<div class="table-responsive">
      |  <table class="table table-hover align-middle">
      |    <thead class="table-light">
      |      <tr>
      |        <th>Contenido</th>
      |        <th class="text-center">Cantidad</th>
      |        <th class="text-center">Precio unitario</th>
      |        <th class="text-center">Descuento</th>
      |        <th class="text-end">Monto final</th>
      |        <th class="text-end">Fecha</th>
      |        <th class="text-end">Acciones</th>
      |      </tr>
      |    </thead>
      |    <tbody>$rows</tbody>
      |  </table>
      |</div>
    """.stripMargin
  }

  private def downloadTypeBadge(productType: Option[ProductType]): String =
    productType match {
      case Some(ProductType.Digital) => "<span class=\"badge bg-info text-dark mt-1\">Digital</span>"
      case Some(ProductType.Hardware) => "<span class=\"badge bg-secondary mt-1\">Hardware</span>"
      case None => "<span class=\"badge bg-light text-muted mt-1\">Sin tipo</span>"
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

    val giftLabels = order.items.flatMap { item =>
      val labels = scala.collection.mutable.ListBuffer.empty[String]
      if (item.isGift) {
        item.giftRecipient.foreach { r =>
          labels += s"<span class='badge bg-warning text-dark me-1'>🎁 Enviado a ${escapeHtml(r)}</span>"
        }
      }
      item.giftSender.foreach { s =>
        labels += s"<span class='badge bg-success text-dark me-1'>🎁 Recibido de ${escapeHtml(s)}</span>"
      }
      labels
    }.mkString(" ")

    val receiptButtons =
      s"""
         |<div class="d-flex flex-wrap gap-2">
         |  <a class="btn btn-outline-primary btn-sm" href="/orders/${order.id}/receipt?format=pdf">
         |    <i class='bi bi-download me-1'></i>Descargar boleta
         |  </a>
         |  <a class="btn btn-link btn-sm text-decoration-none" href="/orders/${order.id}/receipt" target="_blank">
         |    Ver en línea
         |  </a>
         |</div>
       """.stripMargin

    s"""
       |<div class="bg-dark border border-secondary rounded-3 p-3 mb-3">
       |  <div class="bg-light text-dark border border-secondary rounded-3 p-3">
       |    <div class="d-flex justify-content-between flex-wrap gap-2">
       |      <div>
       |        <strong>Orden #${order.id}</strong><br>
       |        <small class="text-muted">${formatDateTime(order.createdAt)}</small>
       |        ${if (giftLabels.nonEmpty) s"<div class=\"mt-1\">$giftLabels</div>" else ""}
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
       |    <div class="mt-3">$receiptButtons</div>
       |  </div>
      |</div>
     """.stripMargin
  }

  private def renderNotifications(notifications: Vector[Notification]): String = {
    if (notifications.isEmpty) {
      return """<p class='text-center text-muted mb-0'>Aún no tienes notificaciones.</p>"""
    }

    notifications.map { n =>
      val badge = n.notificationType match {
        case NotificationType.GiftReceived => "bg-success"
        case NotificationType.BalanceApproved => "bg-success"
        case NotificationType.BalanceRejected => "bg-danger"
        case NotificationType.PurchaseSuccess => "bg-primary"
        case _ => "bg-secondary"
      }
      val readClass = if (n.read) "bg-light text-muted" else "bg-white text-dark"
      s"""
         |<div class="list-group-item ${readClass} border-0 border-bottom border-light">
         |  <div class="d-flex justify-content-between align-items-center">
         |    <div>
         |      <span class="badge ${badge} me-2">${n.notificationType.asString.toUpperCase}</span>
         |      ${escapeHtml(n.message)}
         |    </div>
         |    <small class="text-muted">${formatDateTime(n.createdAt)}</small>
         |  </div>
         |</div>
       """.stripMargin
    }.mkString("\n")
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
