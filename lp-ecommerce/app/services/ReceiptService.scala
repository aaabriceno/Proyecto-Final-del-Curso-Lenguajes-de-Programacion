package services

import models._
import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import scala.util.Try
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import java.io.ByteArrayOutputStream
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder

case class GeneratedReceipt(receipt: Receipt, htmlContent: String, htmlRelativePath: String, pdfPath: Option[Path])

object ReceiptService {

  private val receiptsDir: Path = Paths.get(System.getProperty("user.dir"), "public", "receipts")
  private val relativeDir = "receipts"
  private val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")

  if (!Files.exists(receiptsDir)) {
    Files.createDirectories(receiptsDir)
  }

  def ensureReceiptFor(order: Order, forcePdf: Boolean = false): Option[GeneratedReceipt] = synchronized {
    if (order.totalNet <= 0) return None

    val user = UserRepo.findById(order.userId).getOrElse(return None)
    val baseSeries = s"LP-${order.createdAt.getYear}"
    val existing = ReceiptRepo.findByOrder(order.id)
    val receipt = existing.getOrElse {
      val qrPayload = buildQrPayload(baseSeries, order)
      ReceiptRepo.create(order.id, order.userId, baseSeries, UUID.randomUUID().toString.replace("-", ""), qrPayload, order.createdAt)
    }

    val qrDataUri = generateQrDataUri(receipt.qrData)
    val html = buildHtml(receipt, order, user, qrDataUri)
    val htmlRelative = s"$relativeDir/receipt-${receipt.id}.html"
    val htmlPath = receiptsDir.resolve(s"receipt-${receipt.id}.html")
    Files.writeString(htmlPath, html, StandardCharsets.UTF_8)

    val targetRelativePdf = s"$relativeDir/receipt-${receipt.id}.pdf"
    val pdfPath = receiptsDir.resolve(s"receipt-${receipt.id}.pdf")
    val needsPdf = forcePdf || receipt.pdfPath.isEmpty || !Files.exists(pdfPath)

    val pdfResultPath = if (needsPdf) {
      Try(renderPdf(html, pdfPath)).toOption.map(_ => pdfPath)
    } else if (Files.exists(pdfPath)) {
      Some(pdfPath)
    } else None

    val stored = ReceiptRepo.updateAssetPaths(
      receipt.id,
      Some(htmlRelative),
      pdfResultPath.map(_ => targetRelativePdf)
    )
    val generated = GeneratedReceipt(stored, html, htmlRelative, pdfResultPath)

    // Enviar boleta por correo SOLO cuando se crea por primera vez
    if (existing.isEmpty) {
      try {
        val subject = s"Tu boleta LP Studios #${stored.series}-${stored.number}"
        val baseUrl = sys.env.getOrElse("APP_BASE_URL", "http://localhost:9000")
        val absoluteUrl = s"$baseUrl/${generated.htmlRelativePath}"

        EmailService.send(
          to = user.email,
          subject = subject,
          htmlBody =
            s"""<p>Hola ${escape(user.name)},</p>
               |<p>Gracias por tu compra en LP Studios. Adjuntamos el resumen de tu boleta:</p>
               |<p><strong>Orden:</strong> #${order.id}<br/>
               |<strong>Total pagado:</strong> ${formatMoney(order.totalNet)}</p>
               |<p>Puedes descargar la boleta en PDF desde este adjunto.</p>
               |<hr/>
               |<p>Este es un correo generado automáticamente para fines de demostración.</p>
               |""".stripMargin,
          attachment = pdfResultPath
        )
      } catch {
        case e: Exception =>
          println(s"⚠️ Error al enviar boleta por correo: ${e.getMessage}")
      }
    }

    Some(generated)
  }

  def renderHtml(order: Order): Option[String] =
    ensureReceiptFor(order).map(_.htmlContent)

  def pdfPathFor(order: Order): Option[Path] =
    ensureReceiptFor(order).flatMap(_.pdfPath)

  private def buildQrPayload(series: String, order: Order): String =
    s"$series|${order.id}|${order.totalNet}|${order.createdAt}".replace(' ', '_')

  private def generateQrDataUri(content: String): String = {
    val writer = new QRCodeWriter()
    val matrix = writer.encode(content, BarcodeFormat.QR_CODE, 320, 320)
    val baos = new ByteArrayOutputStream()
    MatrixToImageWriter.writeToStream(matrix, "PNG", baos)
    val base64 = Base64.getEncoder.encodeToString(baos.toByteArray)
    s"data:image/png;base64,$base64"
  }

  private def buildHtml(receipt: Receipt, order: Order, user: User, qrImage: String): String = {
    val itemsRows = order.items.map { item =>
      s"""
         |<tr>
         |  <td>${escape(item.title)}</td>
         |  <td class=\"text-center\">${item.quantity}</td>
         |  <td class=\"text-end\">${formatMoney(item.unitPrice)}</td>
         |  <td class=\"text-end\">${formatMoney(item.netAmount)}</td>
         |</tr>
       """.stripMargin
    }.mkString("\n")

    s"""
      |<!DOCTYPE html>
      |<html lang="es">
      |<head>
      |  <meta charset="UTF-8" />
      |  <title>Boleta ${receipt.series}-${receipt.number}</title>
      |  <style>
      |    body { font-family: 'Helvetica', Arial, sans-serif; margin: 0; padding: 24px; background: #f6f7fb; color: #222; }
      |    .receipt { background: #fff; border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.08); padding: 32px; }
      |    .header { display:flex; justify-content:space-between; align-items:center; border-bottom:2px solid #f0f2f8; padding-bottom:16px; margin-bottom:24px; }
      |    .company { font-weight:600; font-size:20px; }
      |    table { width:100%; border-collapse:collapse; margin-top:16px; }
      |    th, td { padding:10px; }
      |    th { background:#f0f2f8; text-align:left; font-size:13px; text-transform:uppercase; letter-spacing:0.04em; }
      |    tr:nth-child(even) { background:#fafbfe; }
      |    .totals { text-align:right; margin-top:24px; }
      |    .totals div { margin-top:8px; }
      |    .qr { text-align:center; margin-top:24px; }
      |    .badge { display:inline-block; padding:4px 12px; border-radius:999px; background:#ffe69c; color:#5f4208; font-size:12px; }
      |  </style>
      |</head>
      |<body>
      |  <div class="receipt">
      |    <div class="header">
      |      <div>
      |        <div class="company">LP Studios</div>
      |        <small>Fearless Design · Timeless Sound</small>
      |      </div>
      |      <div>
      |        <div class="badge">Boleta electrónica</div>
      |        <div><strong>${receipt.series}-${receipt.number}</strong></div>
      |        <div>${formatter.format(receipt.createdAt)}</div>
      |      </div>
      |    </div>
      |    <div class="row" style="display:flex; justify-content:space-between;">
      |      <div>
      |        <strong>Cliente:</strong><br />${escape(user.name)}<br />${escape(user.email)}<br />${escape(user.phone)}
      |      |</div>
      |      <div style="text-align:right;">
      |        <strong>Orden:</strong> #${order.id}<br />
      |        Total pagado: ${formatMoney(order.totalNet)}
      |      </div>
      |    </div>
      |    <table>
      |      <thead>
      |        <tr>
      |          <th>Producto</th>
      |          <th class="text-center">Cant.</th>
      |          <th class="text-end">Precio</th>
      |          <th class="text-end">Subtotal</th>
      |        </tr>
      |      </thead>
      |      <tbody>
      |        $itemsRows
      |      </tbody>
      |    </table>
      |    <div class="totals">
      |      <div>Subtotal: ${formatMoney(order.totalGross)}</div>
      |      <div>Descuento aplicado: -${formatMoney(order.totalDiscount)}</div>
      |      <div><strong>Total neto: ${formatMoney(order.totalNet)}</strong></div>
      |    </div>
      |    <div class="qr">
      |      <p>Escanea para validar:</p>
      |      <img src="$qrImage" alt="QR de boleta" width="180" height="180" />
      |      <div style="font-size:12px; color:#888; margin-top:8px;">
      |        <a href="/receipts/${receipt.hash}" style="color:#555; text-decoration:none;">/receipts/${receipt.hash}</a>
      |      </div>
      |    </div>
      |  </div>
      |</body>
      |</html>
    """.stripMargin
  }

  private def renderPdf(html: String, pdfPath: Path): Unit = {
    val builder = new PdfRendererBuilder()
    builder.useFastMode()
    builder.withHtmlContent(html, null)
    val output = Files.newOutputStream(pdfPath)
    try {
      builder.toStream(output)
      builder.run()
    } finally {
      output.close()
    }
  }

  private def escape(str: String): String =
    Option(str).getOrElse("")
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")

  private def formatMoney(amount: BigDecimal): String = f"$$${amount}%.2f"
}
