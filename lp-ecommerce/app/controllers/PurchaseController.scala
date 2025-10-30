package controllers

import javax.inject._
import play.api.mvc._
import play.api.i18n._
import models._
import scala.math.BigDecimal.RoundingMode

@Singleton
class PurchaseController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  // Verificar si el usuario tiene acceso (está logueado y activo)
  private def UserAction(f: Long => MessagesRequest[AnyContent] => Result): Action[AnyContent] = 
    Action { implicit request: Request[AnyContent] =>
      val messagesRequest = new MessagesRequest(request, messagesApi)
      request.session.get("userEmail").flatMap { email =>
        UserRepo.findByEmail(email).filter(_.isActive)
      } match {
        case Some(user) if user.isAdmin =>
          Redirect(routes.AdminController.dashboard)
            .flashing("warning" -> "Los administradores no pueden realizar compras")
        case Some(user) =>
          f(user.id)(messagesRequest)
        case None =>
          Redirect(routes.AuthController.loginForm).flashing("error" -> "Debes iniciar sesión")
      }
    }
  
  // Mostrar página de compra de un producto
  def showPurchasePage(mediaId: Long) = UserAction { userId => implicit request =>
    MediaRepo.find(mediaId) match {
      case Some(media) =>
        UserRepo.findById(userId) match {
          case Some(user) =>
            // Ya NO bloqueamos si ya compró antes - puede comprar más
            val hasDiscount = user.totalSpent >= 100
            val discount = if (hasDiscount) 0.2 else 0.0 // 20% de descuento
            
            Ok(views.html.purchase_page(media, user, hasDiscount, discount))
          case None =>
            Redirect(routes.AuthController.loginForm).flashing("error" -> "Usuario no encontrado")
        }
      case None =>
        Redirect(routes.ShopController.list).flashing("error" -> "Producto no encontrado")
    }
  }
  
  // Procesar la compra (ahora con cantidad)
  def processPurchase(mediaId: Long) = UserAction { userId => implicit request =>
    // Obtener cantidad del formulario (por defecto 1)
    val quantity = request.body.asFormUrlEncoded
      .flatMap(_.get("quantity"))
      .flatMap(_.headOption)
      .flatMap(q => scala.util.Try(q.toInt).toOption)
      .filter(_ > 0)
      .getOrElse(1)
    
    MediaRepo.find(mediaId) match {
      case Some(media) =>
        UserRepo.findById(userId) match {
          case Some(user) =>
            // Calcular precio con descuento
            val hasDiscount = user.totalSpent >= 100
            val discountRate = if (hasDiscount) 0.2 else 0.0
            val totalPrice = media.price * quantity
            val discountAmount = (totalPrice * discountRate).setScale(2, RoundingMode.HALF_UP)
            val finalPrice = (totalPrice - discountAmount).setScale(2, RoundingMode.HALF_UP)
            
            // Verificar saldo suficiente
            if (user.balance >= finalPrice) {
              // Descontar saldo
              UserRepo.deductBalance(userId, finalPrice) match {
                case Some(updatedUser) =>
                  // Registrar descarga con cantidad
                  val download = DownloadRepo.add(userId, mediaId, quantity, media.price, discountAmount)
                  
                  Redirect(routes.PurchaseController.showDownloads)
                    .flashing("success" -> s"¡Compra exitosa! ${quantity}x ${media.title} - $$${finalPrice} | Código: ${download.uniqueCode.take(8)}")
                case None =>
                  Redirect(routes.PurchaseController.showPurchasePage(mediaId))
                    .flashing("error" -> "Error al procesar el pago")
              }
            } else {
              val faltante = (finalPrice - user.balance).setScale(2, RoundingMode.HALF_UP)
              Redirect(routes.PurchaseController.showPurchasePage(mediaId))
                .flashing("error" -> s"Saldo insuficiente. Te faltan $$${faltante} para comprar ${quantity} unidad(es)")
            }
          case None =>
            Redirect(routes.AuthController.loginForm).flashing("error" -> "Usuario no encontrado")
        }
      case None =>
        Redirect(routes.ShopController.list).flashing("error" -> "Producto no encontrado")
    }
  }
  
  // Mostrar historial de descargas/compras del usuario
  def showDownloads = UserAction { userId => implicit request =>
    val downloads = DownloadRepo.findByUserId(userId)
    val downloadsWithMedia = downloads.flatMap { download =>
      MediaRepo.find(download.mediaId).map(media => (download, media))
    }
    
    UserRepo.findById(userId) match {
      case Some(user) =>
        Ok(views.html.user_downloads(downloadsWithMedia, user))
      case None =>
        Redirect(routes.AuthController.loginForm).flashing("error" -> "Usuario no encontrado")
    }
  }
  
  // Descargar archivo (simula la descarga)
  def downloadFile(downloadId: Long) = UserAction { userId => implicit request =>
    DownloadRepo.findById(downloadId) match {
      case Some(download) if download.userId == userId =>
        MediaRepo.find(download.mediaId) match {
          case Some(media) =>
            // Aquí simularíamos la descarga del archivo
            // En producción, se enviaría el archivo real
            Redirect(routes.PurchaseController.showDownloads)
              .flashing("success" -> s"Descargando: ${media.title} (${media.assetPath})")
          case None =>
            Redirect(routes.PurchaseController.showDownloads)
              .flashing("error" -> "Archivo no encontrado")
        }
      case Some(_) =>
        Redirect(routes.PurchaseController.showDownloads)
          .flashing("error" -> "No tienes permiso para descargar este archivo")
      case None =>
        Redirect(routes.PurchaseController.showDownloads)
          .flashing("error" -> "Descarga no encontrada")
    }
  }
}
