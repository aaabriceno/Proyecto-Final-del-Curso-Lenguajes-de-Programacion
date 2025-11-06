package controllers

import javax.inject._
import play.api.mvc._
import play.api.i18n._
import models._
import services.UserService

@Singleton
class ShopController @Inject()(cc: MessagesControllerComponents) extends MessagesAbstractController(cc) {

  // Helper para verificar usuario logueado
  private def getLoggedUser(implicit req: RequestHeader): Option[User] = {
    req.session.get("userEmail").flatMap(email => UserRepo.findByEmail(email))
  }

  // Action para requerir usuario logueado
  private def UserAction(f: User => MessagesRequest[AnyContent] => Result): Action[AnyContent] = Action { implicit req =>
    val messagesRequest = new MessagesRequest(req, messagesApi)
    getLoggedUser(req) match {
      case Some(user) => f(user)(messagesRequest)
      case None => Redirect(routes.AuthController.loginForm)
        .flashing("error" -> "Debes iniciar sesión para acceder")
    }
  }

  // GET /shop - Lista de productos con filtros
  def list = Action { implicit req =>
    val query = req.getQueryString("q")
    val typeFilter = req.getQueryString("type").flatMap { t =>
      scala.util.Try(MediaType.from(t)).toOption
    }
    val categoryFilter = req.getQueryString("category").flatMap { c =>
      scala.util.Try(c.toLong).toOption
    }
    
    val items = if (query.isDefined || typeFilter.isDefined || categoryFilter.isDefined) {
      MediaRepo.searchAdvanced(query, typeFilter, categoryFilter)
    } else {
      MediaRepo.all
    }
    
    val categories = CategoryRepo.getRoots
    val selectedCategory = categoryFilter.flatMap(CategoryRepo.find)
    val user = getLoggedUser
    val cartItemCount = user.map(u => CartRepo.countItems(u.id)).getOrElse(0)
    
    Ok(views.html.media_list(items, categories, selectedCategory, query, typeFilter, user, cartItemCount))
  }

  // GET /shop/:id
  def detail(id: Long) = Action { implicit req =>
    MediaRepo.find(id)
      .map(m => Ok(views.html.media_detail(m)))
      .getOrElse(NotFound("Item no encontrado"))
  }

  // POST /cart/add - Agregar producto al carrito
  def addToCart = UserAction { user => implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val mediaId = data.get("mediaId").flatMap(_.headOption).flatMap(id => scala.util.Try(id.toLong).toOption)
    val quantity = data.get("quantity").flatMap(_.headOption).flatMap(q => scala.util.Try(q.toInt).toOption).getOrElse(1)

    mediaId match {
      case None => 
        Redirect(routes.ShopController.list)
          .flashing("error" -> "Producto no válido")
      
      case Some(id) =>
        CartRepo.addOrUpdate(user.id, id, quantity) match {
          case Right(_) =>
            Redirect(routes.ShopController.list)
              .flashing("success" -> "Producto agregado al carrito")
          
          case Left(error) =>
            Redirect(routes.ShopController.list)
              .flashing("error" -> error)
        }
    }
  }

  // GET /cart - Ver carrito
  def viewCart = UserAction { user => implicit req =>
    val cartItems = CartRepo.getByUser(user.id)
    val total = CartRepo.getTotal(user.id)
    val cartItemCount = CartRepo.countItems(user.id)
    
    Ok(views.html.cart(cartItems, total, user, cartItemCount))
  }

  // POST /cart/update/:id - Actualizar cantidad
  def updateCartItem(itemId: Long) = UserAction { user => implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val quantity = data.get("quantity").flatMap(_.headOption).flatMap(q => scala.util.Try(q.toInt).toOption).getOrElse(1)

    CartRepo.updateQuantity(itemId, user.id, quantity) match {
      case Right(_) =>
        Redirect(routes.ShopController.viewCart)
          .flashing("success" -> "Cantidad actualizada")
      
      case Left(error) =>
        Redirect(routes.ShopController.viewCart)
          .flashing("error" -> error)
    }
  }

  // POST /cart/remove/:id - Remover del carrito
  def removeFromCart(itemId: Long) = UserAction { user => implicit req =>
    if (CartRepo.remove(itemId, user.id)) {
      Redirect(routes.ShopController.viewCart)
        .flashing("success" -> "Producto eliminado del carrito")
    } else {
      Redirect(routes.ShopController.viewCart)
        .flashing("error" -> "No se pudo eliminar el producto")
    }
  }

  // POST /cart/checkout - Finalizar compra (SYNCHRONIZED para race conditions)
  def checkout = UserAction { user => implicit req =>
    val cartItems = CartRepo.getByUser(user.id)
    
    if (cartItems.isEmpty) {
      Redirect(routes.ShopController.viewCart)
        .flashing("error" -> "El carrito está vacío")
    } else {
      // Validar balance usando finalPrice (incluye promociones)
      val total = cartItems.map { case (item, media) => 
        media.finalPrice(Some(user)) * item.quantity 
      }.sum

      if (user.balance < total) {
        Redirect(routes.ShopController.viewCart)
          .flashing("error" -> s"Saldo insuficiente. Necesitas $$$total pero solo tienes $$$${user.balance}")
      } else {
        // ZONA CRÍTICA: Validar stock y procesar compra (SYNCHRONIZED)
        val purchaseResult = this.synchronized {
          // Validar que todos los productos tengan stock disponible
          val stockErrors = cartItems.flatMap { case (item, media) =>
            if (!media.hasStock(item.quantity)) {
              Some(s"${media.title}: solo quedan ${media.stock} unidades")
            } else {
              None
            }
          }

          if (stockErrors.nonEmpty) {
            Left(stockErrors.mkString(", "))
          } else {
            // Reducir stock de todos los productos
            val stockReductions = cartItems.map { case (item, media) =>
              MediaRepo.reduceStock(media.id, item.quantity)
            }

            // Si alguna reducción falla (race condition), revertir
            if (stockReductions.exists(_.isLeft)) {
              Left("Error al procesar la compra. Algunos productos se agotaron.")
            } else {
              // Deducir balance
              UserRepo.deductBalance(user.id, total)

              // Registrar compras con precio final (incluye promociones)
              cartItems.foreach { case (item, media) =>
                val finalPrice = media.finalPrice(Some(user))
                val originalPrice = media.price
                val totalDiscount = (originalPrice - finalPrice) * item.quantity
                
                DownloadRepo.add(user.id, media.id, item.quantity, finalPrice, totalDiscount)
              }

              // Limpiar carrito
              CartRepo.clearByUser(user.id)

              Right(())
            }
          }
        }

        purchaseResult match {
          case Right(_) =>
            Redirect(routes.AuthController.account)
              .flashing("success" -> s"¡Compra realizada exitosamente! Total: $$$total")
          
          case Left(error) =>
            Redirect(routes.ShopController.viewCart)
              .flashing("error" -> error)
        }
      }
    }
  }
}
