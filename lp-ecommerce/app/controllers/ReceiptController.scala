package controllers

import http.{HttpRequest, HttpResponse}
import models.{OrderRepo, ReceiptRepo}
import services.ReceiptService
import java.nio.file.Files

object ReceiptController {

  def download(orderId: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        OrderRepo.findById(orderId) match {
          case Some(order) if order.userId == user.id =>
            val needPdf = request.queryParams.get("format").exists(_.equalsIgnoreCase("pdf"))
            ReceiptService.ensureReceiptFor(order, forcePdf = needPdf) match {
              case Some(generated) =>
                request.queryParams.get("format").map(_.toLowerCase) match {
                  case Some("pdf") =>
                    generated.pdfPath.filter(path => Files.exists(path)) match {
                      case Some(pdf) =>
                        val bytes = Files.readAllBytes(pdf)
                        HttpResponse(
                          status = 200,
                          statusText = "OK",
                          headers = Map(
                            "Content-Type" -> "application/pdf",
                            "Content-Disposition" -> s"attachment; filename=boleta-${order.id}.pdf"
                          ),
                          body = "",
                          binaryBody = Some(bytes)
                        )
                      case None =>
                        HttpResponse.internalError("No se pudo generar el PDF de la boleta")
                    }
                  case _ =>
                    HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), generated.htmlContent)
                }
              case None => HttpResponse.internalError("No se pudo preparar la boleta para esta orden")
            }
          case Some(_) => HttpResponse.forbidden("No puedes descargar esta boleta")
          case None     => HttpResponse.notFound("Orden no encontrada")
        }
      case Left(resp) => resp
    }
  }

  def publicView(hash: String, request: HttpRequest): HttpResponse = {
    ReceiptRepo.findByHash(hash) match {
      case Some(receipt) =>
        OrderRepo.findById(receipt.orderId) match {
          case Some(order) =>
            ReceiptService.ensureReceiptFor(order) match {
              case Some(generated) =>
                HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), generated.htmlContent)
              case None => HttpResponse.notFound("Boleta no disponible")
            }
          case None => HttpResponse.notFound("Orden asociada no existe")
        }
      case None => HttpResponse.notFound("Boleta no encontrada")
    }
  }
}
