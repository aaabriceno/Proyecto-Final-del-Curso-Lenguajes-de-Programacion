package models

sealed trait MediaType { def asString: String }
object MediaType {
  case object Image extends MediaType { val asString = "image" }
  case object Audio extends MediaType { val asString = "audio" }
  case object Video extends MediaType { val asString = "video" }

  def from(s: String): MediaType = s.toLowerCase match {
    case "image" => Image
    case "audio" => Audio
    case "video" => Video
    case _       => Image
  }
}

case class Media(
  id: Long,
  title: String,
  description: String,
  mtype: MediaType,
  price: BigDecimal,
  rating: Double,
  downloads: Int,
  assetPath: String          // ruta en /public (p.ej. "images/hero.jpg" o "media/video/clip.mp4")
)

object MediaRepo {
  private var seq: Long = 0L
  private def nextId(): Long = { seq += 1; seq }

  // Dummy seed
  private var data: Vector[Media] = Vector(
    Media(nextId(), "Poster Aurora", "Póster en alta resolución", MediaType.Image, 7.50, 4.6, 132,
      "images/poster_aurora.jpg"),
    Media(nextId(), "Beat LoFi #12", "Pista LoFi 48kHz", MediaType.Audio, 4.00, 4.8, 245,
      "media/audio/lofi12.mp3"),
    Media(nextId(), "Corto City Rush", "Clip FullHD 30s", MediaType.Video, 11.99, 4.4, 89,
      "media/video/city_rush.mp4")
  )

  def all: Vector[Media] = data.sortBy(-_.downloads)
  def find(id: Long): Option[Media] = data.find(_.id == id)
  
  // Búsqueda de productos
  def search(query: String): Vector[Media] = {
    val q = query.toLowerCase.trim
    if (q.isEmpty) all
    else data.filter { m =>
      m.title.toLowerCase.contains(q) ||
      m.description.toLowerCase.contains(q) ||
      m.id.toString == q
    }.sortBy(-_.downloads)
  }
  
  // Filtrar por tipo
  def filterByType(mtype: MediaType): Vector[Media] = 
    data.filter(_.mtype == mtype).sortBy(-_.downloads)
  
  // Estadísticas
  def totalProducts: Int = data.size
  def totalRevenue: BigDecimal = data.map(m => m.price * m.downloads).sum
  def averagePrice: BigDecimal = if (data.nonEmpty) data.map(_.price).sum / data.size else BigDecimal(0)
  def averageRating: Double = if (data.nonEmpty) data.map(_.rating).sum / data.size else 0.0
  def totalDownloads: Int = data.map(_.downloads).sum
  def countByType(mtype: MediaType): Int = data.count(_.mtype == mtype)
  def topProducts(limit: Int = 5): Vector[Media] = data.sortBy(-_.downloads).take(limit)
  
  // CRUD operations for admin
  def add(title: String, description: String, mtype: MediaType, price: BigDecimal, assetPath: String): Media = {
    val media = Media(nextId(), title, description, mtype, price, 0.0, 0, assetPath)
    data = data :+ media
    media
  }
  
  def update(id: Long, title: String, description: String, mtype: MediaType, price: BigDecimal, assetPath: String): Option[Media] = {
    data.find(_.id == id).map { oldMedia =>
      val updated = oldMedia.copy(title = title, description = description, mtype = mtype, price = price, assetPath = assetPath)
      data = data.filterNot(_.id == id) :+ updated
      updated
    }
  }
  
  def delete(id: Long): Boolean = {
    val sizeBefore = data.size
    data = data.filterNot(_.id == id)
    data.size < sizeBefore
  }
}