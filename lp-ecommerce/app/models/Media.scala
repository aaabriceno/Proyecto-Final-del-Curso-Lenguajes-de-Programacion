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
    Media(nextId(), "Corto “City Rush”", "Clip FullHD 30s", MediaType.Video, 11.99, 4.4, 89,
      "media/video/city_rush.mp4")
  )

  def all: Vector[Media] = data.sortBy(-_.downloads)
  def find(id: Long): Option[Media] = data.find(_.id == id)
}