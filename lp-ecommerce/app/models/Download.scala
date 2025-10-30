package models

import java.time.LocalDateTime
import scala.collection.mutable
import java.util.UUID

case class Download(
  id: Long,
  userId: Long,
  mediaId: Long,
  quantity: Int = 1,                    // Cantidad comprada
  price: BigDecimal,                     // Precio unitario
  discount: BigDecimal = 0,              // Descuento total
  finalPrice: BigDecimal,                // Precio final total
  downloadDate: LocalDateTime = LocalDateTime.now(),
  uniqueCode: String = UUID.randomUUID().toString // Código único por compra
)

object DownloadRepo {
  private var downloads: Vector[Download] = Vector(
    Download(1, 1, 1, 1, 9.99, 0, 9.99, LocalDateTime.now().minusDays(5), UUID.randomUUID().toString),
    Download(2, 1, 3, 2, 14.99, 0, 29.98, LocalDateTime.now().minusDays(3), UUID.randomUUID().toString),
    Download(3, 1, 2, 1, 4.00, 0.80, 3.20, LocalDateTime.now().minusDays(1), UUID.randomUUID().toString)
  )
  
  private var nextId = downloads.map(_.id).maxOption.getOrElse(0L) + 1
  
  def all: Vector[Download] = downloads
  
  def findById(id: Long): Option[Download] = 
    downloads.find(_.id == id)
  
  def findByUserId(userId: Long): Vector[Download] = 
    downloads.filter(_.userId == userId).sortBy(_.downloadDate).reverse
  
  def findByMediaId(mediaId: Long): Vector[Download] = 
    downloads.filter(_.mediaId == mediaId)
  
  // Ahora verificamos si ya compró, pero puede comprar más cantidad
  def userHasDownloaded(userId: Long, mediaId: Long): Boolean =
    downloads.exists(d => d.userId == userId && d.mediaId == mediaId)
  
  def add(userId: Long, mediaId: Long, quantity: Int, price: BigDecimal, discount: BigDecimal = 0): Download = {
    val totalPrice = price * quantity
    val finalPrice = totalPrice - discount
    val uniqueCode = UUID.randomUUID().toString
    val download = Download(nextId, userId, mediaId, quantity, price, discount, finalPrice, LocalDateTime.now(), uniqueCode)
    downloads = downloads :+ download
    nextId += 1
    download
  }
  
  // Estadísticas basadas en descargas REALES
  def totalRevenue: BigDecimal = 
    downloads.map(_.finalPrice).sum
    
  def totalDownloads: Int = 
    downloads.map(_.quantity).sum  // Suma de cantidades compradas
  
  def totalPurchases: Int = downloads.size  // Número de transacciones
  
  def revenueByUser(userId: Long): BigDecimal =
    downloads.filter(_.userId == userId).map(_.finalPrice).sum
    
  def downloadsByUser(userId: Long): Int =
    downloads.filter(_.userId == userId).map(_.quantity).sum
    
  def topBuyers(limit: Int = 10): Vector[(Long, BigDecimal)] =
    downloads.groupBy(_.userId)
      .mapValues(_.map(_.finalPrice).sum)
      .toVector
      .sortBy(-_._2)
      .take(limit)
      
  def downloadsByMedia(mediaId: Long): Int =
    downloads.filter(_.mediaId == mediaId).map(_.quantity).sum
    
  def revenueByMedia(mediaId: Long): BigDecimal =
    downloads.filter(_.mediaId == mediaId).map(_.finalPrice).sum
    
  def findByCode(code: String): Option[Download] =
    downloads.find(_.uniqueCode == code)
}
