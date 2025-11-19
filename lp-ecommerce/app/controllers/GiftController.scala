package controllers

import http.{HttpRequest, HttpResponse}
import models._

object GiftController {

  private def renderGiftsPage(user: User, pending: Vector[Gift], sent: Vector[Gift]): HttpResponse = {
    def renderList(title: String, gifts: Vector[Gift], emptyMessage: String): String = {
      if (gifts.isEmpty) s"<p class='text-muted'>$emptyMessage</p>"
      else {
        val rows = gifts.map { gift =>
          val mediaTitle = MediaRepo.find(gift.mediaId).map(_.title).getOrElse(s"Contenido ${gift.mediaId}")
          val status = if (gift.claimed) "Reclamado" else "Pendiente"
          val paid = f"${gift.pricePaid}%.2f"
          val badge = if (gift.claimed) "success" else "warning"
          s"""
             |<div class="list-group-item bg-dark text-light border-secondary">
             |  <div class="d-flex justify-content-between">
             |    <div>
             |      <strong>$mediaTitle</strong><br>
             |      <small>Pagado: $$${paid}</small>
             |    </div>
             |    <span class="badge bg-$badge">$status</span>
             |  </div>
             |</div>
          """.stripMargin
        }.mkString("\n")
        s"<div class='list-group'>$rows</div>"
      }
    }

    val html = s"""
      |<!DOCTYPE html>
      |<html lang="es">
      |<head>
      |  <meta charset="UTF-8">
      |  <title>Mis regalos</title>
      |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
      |</head>
      |<body class="bg-dark text-light">
      |  <div class="container py-4">
      |    <h1 class="mb-4">🎁 Mis regalos</h1>
      |    <h4>Pendientes</h4>
      |    %s
      |    <h4 class="mt-4">Enviados</h4>
      |    %s
      |    <a href="/user/account" class="btn btn-outline-light mt-4">Volver</a>
      |  </div>
      |</body>
      |</html>
    """.stripMargin.format(
      renderList("Pendientes", pending, "No tienes regalos pendientes."),
      renderList("Enviados", sent, "Aún no has enviado regalos.")
    )

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
            }
            HttpResponse.json(200, Map("success" -> true))
          case None =>
            HttpResponse.json(404, Map("success" -> false, "error" -> "Regalo no encontrado"))
        }
      case Left(resp) => resp
    }
  }
}
