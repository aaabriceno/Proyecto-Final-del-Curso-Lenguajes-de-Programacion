package models

case class CartItem(product: Product, qty: Int) {
  def subtotal: BigDecimal = product.price * qty
}

case class Cart(items: Seq[CartItem]) {
  def total: BigDecimal = items.map(_.subtotal).sum
}
