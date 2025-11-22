package controllers

import http.{HttpRequest, HttpResponse}
import models._
import java.time.format.DateTimeFormatter

object GiftController {

  private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

  private def escapeHtml(s: String): String =
    Option(s).getOrElse("")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")

  private def formatMoney(amount: BigDecimal): String = f"$$${amount}%.2f"

  private def formatDate(date: java.time.LocalDateTime): String = dateFormatter.format(date)

  private def giftBadge(isClaimed: Boolean): String =
    if (isClaimed) "<span class=\"badge bg-success\">Reclamado</span>"
    else "<span class=\"badge bg-warning text-dark\">Pendiente</span>"

  private def renderGiftItem(gift: Gift, isPendingSection: Boolean): String = {
    val mediaTitle = MediaRepo.find(gift.mediaId).map(_.title).getOrElse(s"Contenido #${gift.mediaId}")
    val counterpart =
      if (isPendingSection) UserRepo.findById(gift.fromUserId).map(_.name).getOrElse("Un usuario")
      else UserRepo.findById(gift.toUserId).map(_.name).getOrElse("Un usuario")
    val counterpartLabel =
      if (isPendingSection) s"Te lo envió ${escapeHtml(counterpart)}"
      else s"Destinatario: ${escapeHtml(counterpart)}"

    val messageBlock = gift.message.filter(_.nonEmpty).map { msg =>
      s"""
         |<div class="mt-2 small text-muted fst-italic border-start ps-2">
         |  "${escapeHtml(msg)}"
         |</div>
       """.stripMargin
    }.getOrElse("")

    val claimButton =
      if (isPendingSection && !gift.claimed)
        s"""
          |<form method="POST" action="/gifts/${gift.id}/claim" class="mt-3">
          |  <button type="submit" class="btn btn-success btn-sm">
          |    <i class="bi bi-check-circle me-1"></i>Reclamar regalo
          |  </button>
          |</form>
        """.stripMargin
      else ""

    s"""
      |<div class="list-group-item py-3">
      |  <div class="d-flex justify-content-between flex-wrap gap-2">
      |    <div>
      |      <strong>${escapeHtml(mediaTitle)}</strong><br>
      |      <small class="text-muted">$counterpartLabel</small>
      |    </div>
      |    <div class="text-end">
      |      ${giftBadge(gift.claimed)}
      |      <div class="text-muted small">${formatDate(gift.createdAt)}</div>
      |    </div>
      |  </div>
      |  <div class="mt-2 d-flex flex-wrap gap-3 small">
      |    <span>Pagado: <strong>${formatMoney(gift.pricePaid)}</strong></span>
      |    <span>Precio original: <strong>${formatMoney(gift.originalPrice)}</strong></span>
      |  </div>
      |  $messageBlock
      |  $claimButton
      |</div>
    """.stripMargin
  }

  private def renderGiftsPage(user: User, pending: Vector[Gift], sent: Vector[Gift]): HttpResponse = {
    def renderList(gifts: Vector[Gift], emptyMessage: String, isPendingSection: Boolean): String = {
      if (gifts.isEmpty)
        s"""
          |<div class="alert alert-secondary text-muted" role="alert">$emptyMessage</div>
        """.stripMargin
      else
        s"""
          |<div class="list-group shadow-sm">
          |  ${gifts.map(g => renderGiftItem(g, isPendingSection)).mkString("\n")}
          |</div>
        """.stripMargin
    }

    val html = s"""
      |<!DOCTYPE html>
      |<html lang="es">
      |<head>
      |  <meta charset="UTF-8">
      |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
      |  <title>Mis regalos — LP Studios</title>
      |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
      |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
      |  <link rel="stylesheet" href="/assets/stylesheets/fearless.css">
      |</head>
      |<body class="bg-light text-dark">
      |  <nav class="navbar border-bottom shadow-sm" style="background:#e3eaf2; color:#1976d2;">
      |    <div class="container">
      |      <a class="navbar-brand fw-semibold" href="/" style="color:#1976d2 !important;">LP Studios</a>
      |      <div class="ms-auto d-flex gap-2">
      |        <a class="btn btn-outline-light btn-sm" href="/shop" style="background:#1976d2; color:#fff; border-color:#1976d2;">🛍️ Tienda</a>
      |        <a class="btn btn-outline-warning btn-sm position-relative" id="notificationBtn" href="/user/notifications" title="Notificaciones">
      |          <i class="bi bi-bell-fill"></i>
      |        </a>
      |        <a class="btn btn-outline-secondary btn-sm" href="/user/account">👤 Mi cuenta</a>
      |        <a class="btn btn-outline-danger btn-sm" href="/logout">🚪 Salir</a>
      |      </div>
      |    </div>
      |  </nav>
      |
      |  <main class="container py-5">
      |    <div class="row justify-content-center">
      |      <div class="col-lg-10 col-xl-9">
      |        <div class="d-flex flex-wrap justify-content-between align-items-start gap-3 mb-4">
      |          <div>
      |            <h1 class="fw-bold mb-1">🎁 Mis regalos</h1>
      |            <p class="text-muted mb-0">Visualiza los regalos recibidos y los que enviaste a otros usuarios.</p>
      |          </div>
      |          <a href="/user/account" class="btn btn-primary">Volver a mi cuenta</a>
      |        </div>
      |
      |        <section class="mb-5">
      |          <div class="d-flex justify-content-between align-items-center mb-3">
      |            <h4 class="mb-0">Regalos recibidos</h4>
      |            <span class="badge bg-warning text-dark">${pending.size}</span>
      |          </div>
      |          ${renderList(pending, "No tienes regalos pendientes.", isPendingSection = true)}
      |        </section>
      |
      |        <section class="mb-5">
      |          <div class="d-flex justify-content-between align-items-center mb-3">
      |            <h4 class="mb-0">Regalos enviados</h4>
      |            <span class="badge bg-info text-dark">${sent.size}</span>
      |          </div>
      |          ${renderList(sent, "Aún no has enviado regalos.", isPendingSection = false)}
      |        </section>
      |      </div>
      |    </div>
      |  </main>
      |
      |  <footer class="text-center text-muted py-4 mt-5 border-top">
      |    <small>© 2025 LP Studios — Fearless Design. Timeless Sound.</small>
      |  </footer>
      |  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
      |  <script src="/assets/javascripts/notifications.js"></script>
      |</body>
      |</html>
    """.stripMargin

    HttpResponse.ok(html)
  }

  def list(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val pending = GiftRepo.pendingFor(user.id)
        val sent = GiftRepo.sentBy(user.id)
        renderGiftsPage(user, pending, sent)
      case Left(resp) => resp
    }
  }

  def send(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val payload = request.jsonBody.getOrElse(Map.empty)
        val mediaIdOpt = payload.get("id").orElse(payload.get("mediaId")).flatMap(v => v.toString.toLongOption)
          .orElse(request.formData.get("mediaId").flatMap(_.toLongOption))
        val recipientEmail = payload.get("destinatario").orElse(payload.get("emailDestinatario")).map(_.toString)
          .orElse(request.formData.get("email"))
        val message = payload.get("mensaje").map(_.toString).orElse(request.formData.get("mensaje"))

        (mediaIdOpt, recipientEmail) match {
          case (Some(mediaId), Some(email)) =>
            if (email.equalsIgnoreCase(user.email))
              return HttpResponse.json(400, Map("success" -> false, "error" -> "No puedes enviarte un regalo a ti mismo"))

            (for {
              recipient <- UserRepo.findByEmail(email.trim.toLowerCase).toRight("Usuario destinatario no encontrado")
              media <- MediaRepo.find(mediaId).toRight("Producto no encontrado")
              _ <- Either.cond(!media.isOutOfStock, (), "Producto sin stock disponible")
            } yield {
              val finalPrice = media.finalPrice(Some(user)).setScale(2, BigDecimal.RoundingMode.HALF_UP)
              val discount = (media.price - finalPrice).max(BigDecimal(0)).setScale(2, BigDecimal.RoundingMode.HALF_UP)

              UserRepo.deductBalance(user.id, finalPrice) match {
                case Some(_) =>
                  MediaRepo.reduceStock(media.id, 1) match {
                    case Right(_) =>
                      val gift = GiftRepo.create(user.id, recipient.id, media.id, media.price, finalPrice, discount, message)
                      TransactionRepo.create(
                        transactionType = TransactionType.GiftSent,
                        fromUserId = Some(user.id),
                        toUserId = Some(recipient.id),
                        mediaId = Some(media.id),
                        quantity = 1,
                        grossAmount = media.price,
                        discount = discount,
                        referenceId = Some(gift.id),
                        notes = message.filter(_.nonEmpty)
                      )
                      OrderRepo.create(
                        user.id,
                        Vector(OrderItem(
                          mediaId = media.id,
                          title = media.title,
                          quantity = 1,
                          unitPrice = media.price,
                          discount = discount,
                          netAmount = finalPrice,
                          productType = media.productType,
                          isGift = true,
                          giftRecipient = Some(recipient.email)
                        ))
                      )
                      NotificationRepo.create(
                        recipient.id,
                        s"${user.name} te regaló ${media.title}",
                        NotificationType.GiftReceived
                      )
                      HttpResponse.json(200, Map("success" -> true, "giftId" -> gift.id))
                    case Left(errorMsg) =>
                      UserRepo.refundBalance(user.id, finalPrice)
                      HttpResponse.json(400, Map("success" -> false, "error" -> errorMsg))
                  }
                case None => HttpResponse.json(400, Map("success" -> false, "error" -> "Saldo insuficiente"))
              }
            }) match {
              case Right(response) => response
              case Left(error) => HttpResponse.json(400, Map("success" -> false, "error" -> error))
            }
          case _ => HttpResponse.json(400, Map("success" -> false, "error" -> "Datos incompletos"))
        }
      case Left(resp) => resp
    }
  }

  def claim(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        GiftRepo.find(id) match {
          case Some(gift) if gift.toUserId != user.id =>
            HttpResponse.json(403, Map("success" -> false, "error" -> "No puedes reclamar este regalo"))
          case Some(gift) if gift.claimed =>
            HttpResponse.json(400, Map("success" -> false, "error" -> "El regalo ya fue reclamado"))
          case Some(gift) =>
            GiftRepo.markClaimed(id)
            MediaRepo.find(gift.mediaId).foreach { media =>
              val discount = (gift.originalPrice - gift.pricePaid).max(BigDecimal(0))
              TransactionRepo.create(
                transactionType = TransactionType.GiftClaimed,
                fromUserId = Some(user.id),
                toUserId = Some(gift.fromUserId),
                mediaId = Some(gift.mediaId),
                quantity = 1,
                grossAmount = gift.originalPrice,
                discount = discount,
                referenceId = Some(gift.id),
                notes = Some("Regalo reclamado")
              )
              if (media.productType == ProductType.Digital) {
                DownloadRepo.add(user.id, media.id, 1, gift.originalPrice, discount)
              }
              val senderEmail = UserRepo.findById(gift.fromUserId).map(_.email)
              OrderRepo.create(
                user.id,
                Vector(OrderItem(
                  mediaId = media.id,
                  title = media.title,
                  quantity = 1,
                  unitPrice = gift.originalPrice,
                  discount = gift.originalPrice,
                  netAmount = BigDecimal(0),
                  productType = media.productType,
                  isGift = true,
                  giftSender = senderEmail
                ))
              )
            }
            HttpResponse.redirect("/user/gifts?success=Regalo+reclamado")
          case None =>
            HttpResponse.json(404, Map("success" -> false, "error" -> "Regalo no encontrado"))
        }
      case Left(resp) => resp
    }
  }
}
