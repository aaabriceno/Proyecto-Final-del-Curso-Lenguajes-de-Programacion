error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/PagesController.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/PagesController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -javax/inject/views.
	 -play/api/mvc/views.
	 -views.
	 -scala/Predef.views.
offset: 805
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/PagesController.scala
text:
```scala
package controllers
import javax.inject._
import play.api.mvc._

@Singleton
class PagesController @Inject()(cc: ControllerComponents) extends AbstractController(cc) {
  def addContent = Action { implicit req => Ok(views.html.addContent()) }
  def admi_view = Action { implicit req => Ok(views.html.admi_view()) }
  def item_info_edit = Action { implicit req => Ok(views.html.item_info_edit()) }
  def item_shop = Action { implicit req => Ok(views.html.item_shop()) }
  def item_view = Action { implicit req => Ok(views.html.item_view()) }
  def item_view_admi = Action { implicit req => Ok(views.html.item_view_admi()) }
  def login = Action { implicit req => Ok(views.html.login()) }
  def main_view = Action { implicit req => Ok(views.html.main_view()) }
  def navbar = Action { implicit req => Ok(views@@.html.navbar()) }
  def register = Action { implicit req => Ok(views.html.register()) }
  def transacciones = Action { implicit req => Ok(views.html.transacciones()) }
  def user_account = Action { implicit req => Ok(views.html.user_account()) }
  def user_info = Action { implicit req => Ok(views.html.user_info()) }
  def user_view = Action { implicit req => Ok(views.html.user_view()) }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.