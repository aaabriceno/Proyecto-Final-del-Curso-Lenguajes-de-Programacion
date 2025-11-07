package models

import java.time.LocalDateTime

/** Representa un ítem dentro del carrito */
case class CartItem(
  id: Long,
  userId: Long,
  mediaId: Long,
  quantity: Int,
  dateAdded: LocalDateTime
)

/** Repositorio in-memory para manejar carritos */
object CartRepo {

  private var cartItems: Vector[CartItem] = Vector.empty
  private var nextId: Long = 1L

  /** Agregar item al carrito (o actualizar cantidad si ya existe) */
  def addOrUpdate(userId: Long, mediaId: Long, quantity: Int): Either[String, CartItem] = synchronized {
    if (quantity <= 0)
      Left("La cantidad debe ser mayor a 0")
    else {
      MediaRepo.find(mediaId) match {
        case None => Left("Producto no encontrado")
        case Some(_) =>
          cartItems.find(i => i.userId == userId && i.mediaId == mediaId) match {
            case Some(existing) =>
              val updated = existing.copy(quantity = existing.quantity + quantity)
              cartItems = cartItems.map(i => if (i.id == existing.id) updated else i)
              Right(updated)

            case None =>
              val newItem = CartItem(nextId, userId, mediaId, quantity, LocalDateTime.now())
              cartItems :+= newItem
              nextId += 1
              Right(newItem)
          }
      }
    }
  }

  /** Actualizar cantidad de un item existente */
  def updateQuantity(itemId: Long, userId: Long, newQuantity: Int): Either[String, CartItem] = synchronized {
    if (newQuantity <= 0)
      Left("La cantidad debe ser mayor a 0")
    else {
      cartItems.find(i => i.id == itemId && i.userId == userId) match {
        case None => Left("Item no encontrado en tu carrito")
        case Some(item) =>
          val updated = item.copy(quantity = newQuantity)
          cartItems = cartItems.map(i => if (i.id == itemId) updated else i)
          Right(updated)
      }
    }
  }

  /** Eliminar un ítem del carrito */
  def remove(itemId: Long, userId: Long): Boolean = synchronized {
    val initialSize = cartItems.size
    cartItems = cartItems.filterNot(i => i.id == itemId && i.userId == userId)
    cartItems.size < initialSize
  }

  /** Obtener carrito completo de un usuario */
  def getByUser(userId: Long): Vector[(CartItem, Media)] = synchronized {
    cartItems
      .filter(_.userId == userId)
      .sortBy(_.dateAdded)
      .flatMap(item => MediaRepo.find(item.mediaId).map(media => (item, media)))
  }

  /** Limpiar carrito de un usuario */
  def clearByUser(userId: Long): Unit = synchronized {
    cartItems = cartItems.filterNot(_.userId == userId)
  }

  /** Calcular el total del carrito */
  def getTotal(userId: Long): BigDecimal = synchronized {
    getByUser(userId).map { case (item, media) => media.price * item.quantity }.sum
  }

  /** Contar total de unidades en el carrito */
  def countItems(userId: Long): Int = synchronized {
    cartItems.filter(_.userId == userId).map(_.quantity).sum
  }

  /** Buscar un ítem por ID */
  def findById(itemId: Long): Option[CartItem] = synchronized {
    cartItems.find(_.id == itemId)
  }
}
