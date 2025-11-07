package models

import java.time.LocalDateTime
import java.util.UUID

/**
 * Representa una compra o descarga realizada por un usuario.
 * Cada registro equivale a una transacción.
 */
case class Download(
  id: Long,
  userId: Long,
  mediaId: Long,
  quantity: Int = 1,                     // Cantidad comprada
  price: BigDecimal,                     // Precio unitario
  discount: BigDecimal = 0,              // Descuento total aplicado
  finalPrice: BigDecimal,                // Precio final total (price * quantity - discount)
  downloadDate: LocalDateTime = LocalDateTime.now(),
  uniqueCode: String = UUID.randomUUID().toString
)

/**
 * Repositorio en memoria de descargas (transacciones).
 * Thread-safe y compatible con Scala 2.13 puro.
 */
object DownloadRepo {

  private var downloads: Vector[Download] = Vector(
    Download(1, 1, 1, 1, 9.99, 0, 9.99, LocalDateTime.now().minusDays(5), UUID.randomUUID().toString),
    Download(2, 1, 3, 2, 14.99, 0, 29.98, LocalDateTime.now().minusDays(3), UUID.randomUUID().toString),
    Download(3, 1, 2, 1, 4.00, 0.80, 3.20, LocalDateTime.now().minusDays(1), UUID.randomUUID().toString)
  )

  private var nextId: Long = downloads.map(_.id).maxOption.getOrElse(0L) + 1L

  // ==============================
  // CONSULTAS BÁSICAS
  // ==============================

  def all: Vector[Download] = downloads.sortBy(_.downloadDate)(Ordering[LocalDateTime].reverse)

  def findById(id: Long): Option[Download] =
    downloads.find(_.id == id)

  def findByUserId(userId: Long): Vector[Download] =
    downloads.filter(_.userId == userId).sortBy(_.downloadDate)(Ordering[LocalDateTime].reverse)

  def findByMediaId(mediaId: Long): Vector[Download] =
    downloads.filter(_.mediaId == mediaId)

  def findByCode(code: String): Option[Download] =
    downloads.find(_.uniqueCode == code)

  def userHasDownloaded(userId: Long, mediaId: Long): Boolean =
    downloads.exists(d => d.userId == userId && d.mediaId == mediaId)

  // ==============================
  // CREACIÓN / AGREGADO
  // ==============================

  /** Agrega una nueva compra/descarga */
  def add(
    userId: Long,
    mediaId: Long,
    quantity: Int,
    price: BigDecimal,
    discount: BigDecimal = 0
  ): Download = synchronized {
    val totalPrice = price * quantity
    val finalPrice = totalPrice - discount
    val uniqueCode = UUID.randomUUID().toString

    val newDownload = Download(nextId, userId, mediaId, quantity, price, discount, finalPrice, LocalDateTime.now(), uniqueCode)
    downloads :+= newDownload
    nextId += 1
    newDownload
  }

  // ==============================
  // ESTADÍSTICAS
  // ==============================

  /** Ingresos totales (de todas las descargas) */
  def totalRevenue: BigDecimal =
    downloads.map(_.finalPrice).sum

  /** Total de unidades descargadas (suma de cantidades) */
  def totalDownloads: Int =
    downloads.map(_.quantity).sum

  /** Total de transacciones realizadas */
  def totalPurchases: Int =
    downloads.size

  /** Ingresos generados por un usuario */
  def revenueByUser(userId: Long): BigDecimal =
    downloads.filter(_.userId == userId).map(_.finalPrice).sum

  /** Cantidad total de descargas por usuario */
  def downloadsByUser(userId: Long): Int =
    downloads.filter(_.userId == userId).map(_.quantity).sum

  /** Top compradores (por ingreso total) */
  def topBuyers(limit: Int = 10): Vector[(Long, BigDecimal)] = {
    downloads
      .groupBy(_.userId)
      .view
      .mapValues(_.map(_.finalPrice).sum)
      .toVector
      .sortBy(-_._2)
      .take(limit)
  }

  /** Cantidad total de descargas por producto */
  def downloadsByMedia(mediaId: Long): Int =
    downloads.filter(_.mediaId == mediaId).map(_.quantity).sum

  /** Ingresos totales generados por un producto */
  def revenueByMedia(mediaId: Long): BigDecimal =
    downloads.filter(_.mediaId == mediaId).map(_.finalPrice).sum
}
