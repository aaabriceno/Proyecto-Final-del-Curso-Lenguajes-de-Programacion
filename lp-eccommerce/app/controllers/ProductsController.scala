package controllers

import javax.inject._
import play.api.mvc._
import models._

@Singleton
class ProductsController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc) {

  private val CartSessionKey = "cart"

  // Muestra todos los productos
  def list = Action { implicit req =>
    Ok(views.html.products(ProductRepo.all))
  }

  // Muestra un producto específico
  def details(id: Long) = Action { implicit req =>
    ProductRepo.find(id).map { p =>
      Ok(views.html.product(p))
    }.getOrElse(NotFound("Producto no encontrado"))
  }

  // Agregar al carrito
  def addToCart(id: Long) = Action { implicit req =>
    val updated = addOne(parseCart(req.session.get(CartSessionKey)), id)
    Redirect(routes.ProductsController.viewCart())
      .addingToSession(CartSessionKey -> serializeCart(updated))
      .flashing("success" -> "Agregado al carrito")
  }

  // Ver carrito
  def viewCart = Action { implicit req =>
    val cart = parseCart(req.session.get(CartSessionKey))
    Ok(views.html.cart(cart))
  }

  // Checkout demo
  def checkout = Action { implicit req =>
    Redirect(routes.ProductsController.list())
      .withNewSession
      .flashing("success" -> "¡Gracias! Pedido registrado (demo)")
  }

  // ---- Helpers ----
  private def addOne(current: Cart, productId: Long): Cart = {
    ProductRepo.find(productId) match {
      case Some(p) =>
        val (hit, rest) = current.items.partition(_.product.id == productId)
        val newItem = hit.headOption.map(i => i.copy(qty = i.qty + 1)).getOrElse(CartItem(p, 1))
        Cart(newItem +: rest)
      case None => current
    }
  }

  private def parseCart(raw: Option[String]): Cart = {
    val items = raw.toSeq
      .flatMap(_.split(",").toSeq)
      .flatMap { pair =>
        pair.split(":") match {
          case Array(idStr, qtyStr) =>
            (idStr.toLongOption, qtyStr.toIntOption) match {
              case (Some(id), Some(qty)) if qty > 0 => ProductRepo.find(id).map(p => CartItem(p, qty))
              case _ => None
            }
          case _ => None
        }
      }
    Cart(items)
  }

  private def serializeCart(cart: Cart): String =
    cart.items.map(i => s"${i.product.id}:${i.qty}").mkString(",")
}
