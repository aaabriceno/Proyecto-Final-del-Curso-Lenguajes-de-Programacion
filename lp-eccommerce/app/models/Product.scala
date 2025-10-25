package models

case class Product(
  id: Long,
  name: String,
  description: String,
  price: BigDecimal,
  imageUrl: String
)

object ProductRepo {
  private val data: Vector[Product] = Vector(
    Product(1, "Polera UCSP", "Polera algodón peinado", 89.90, "/assets/images/polera.jpg"),
    Product(2, "Taza edición CS", "Taza cerámica 350ml", 29.50, "/assets/images/taza.jpg"),
    Product(3, "Stickers pack", "10 unidades resistentes al agua", 15.00, "/assets/images/stickers.jpg")
  )

  def all: Seq[Product] = data
  def find(id: Long): Option[Product] = data.find(_.id == id)
}
