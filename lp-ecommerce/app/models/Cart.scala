package models

import java.time.LocalDateTime

case class CartItem(
  id: Long,
  userId: Long,
  mediaId: Long,
  quantity: Int,
  dateAdded: LocalDateTime
)

object CartRepo {
  private var cartItems = Vector[CartItem]()
  private var nextId: Long = 1

  // Agregar item al carrito (o incrementar cantidad si ya existe)
  def addOrUpdate(userId: Long, mediaId: Long, quantity: Int): Either[String, CartItem] = {
    if (quantity <= 0) {
      return Left("La cantidad debe ser mayor a 0")
    }

    // Verificar que el producto existe
    MediaRepo.find(mediaId) match {
      case None => Left("Producto no encontrado")
      case Some(media) =>
        // Buscar si ya existe en el carrito
        cartItems.find(item => item.userId == userId && item.mediaId == mediaId) match {
          case Some(existingItem) =>
            // Actualizar cantidad
            val newQuantity = existingItem.quantity + quantity
            val updated = existingItem.copy(quantity = newQuantity)
            cartItems = cartItems.map(item =>
              if (item.id == existingItem.id) updated else item
            )
            Right(updated)
          
          case None =>
            // Crear nuevo item
            val item = CartItem(nextId, userId, mediaId, quantity, LocalDateTime.now())
            cartItems = cartItems :+ item
            nextId += 1
            Right(item)
        }
    }
  }

  // Actualizar cantidad de un item específico
  def updateQuantity(itemId: Long, userId: Long, newQuantity: Int): Either[String, CartItem] = {
    if (newQuantity <= 0) {
      return Left("La cantidad debe ser mayor a 0")
    }

    cartItems.find(item => item.id == itemId && item.userId == userId) match {
      case None => Left("Item no encontrado en tu carrito")
      case Some(item) =>
        val updated = item.copy(quantity = newQuantity)
        cartItems = cartItems.map(i => if (i.id == itemId) updated else i)
        Right(updated)
    }
  }

  // Remover item del carrito
  def remove(itemId: Long, userId: Long): Boolean = {
    val initialSize = cartItems.size
    cartItems = cartItems.filterNot(item => item.id == itemId && item.userId == userId)
    cartItems.size < initialSize
  }

  // Obtener carrito de un usuario con información de productos
  def getByUser(userId: Long): Vector[(CartItem, Media)] = {
    cartItems
      .filter(_.userId == userId)
      .flatMap { item =>
        MediaRepo.find(item.mediaId).map(media => (item, media))
      }
  }

  // Limpiar carrito de un usuario (después de comprar)
  def clearByUser(userId: Long): Unit = {
    cartItems = cartItems.filterNot(_.userId == userId)
  }

  // Calcular total del carrito (sin descuentos, eso se aplica en checkout)
  def getTotal(userId: Long): BigDecimal = {
    getByUser(userId).map { case (item, media) =>
      media.price * item.quantity
    }.sum
  }

  // Contar items en carrito
  def countItems(userId: Long): Int = {
    cartItems.filter(_.userId == userId).map(_.quantity).sum
  }

  // Obtener un item específico
  def findById(itemId: Long): Option[CartItem] = {
    cartItems.find(_.id == itemId)
  }
}
