package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class HomeController @Inject()(cc: ControllerComponents)
  extends AbstractController(cc) {

  def index = Action { implicit req: Request[AnyContent] =>
    Ok(views.html.main_view())   // ← tu landing convertida
  }
}
