package controllers

import javax.inject._
import play.api.mvc._
import models._

@Singleton
class ShopController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {

  // GET /shop
  def list = Action { implicit req =>
    val items = MediaRepo.all
    Ok(views.html.media_list(items))
  }

  // GET /shop/:id
  def detail(id: Long) = Action { implicit req =>
    MediaRepo.find(id)
      .map(m => Ok(views.html.media_detail(m)))
      .getOrElse(NotFound("Item no encontrado"))
  }
}
