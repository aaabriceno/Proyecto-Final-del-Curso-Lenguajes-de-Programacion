package controllers

import javax.inject._
import play.api.mvc._
import models._

@Singleton
class AdminController @Inject()(cc: MessagesControllerComponents)
  extends MessagesAbstractController(cc) {

  private val SessionKey = "userEmail"

  // Helper: Verificar si el usuario es admin
  private def isAdmin(implicit req: RequestHeader): Boolean = {
    req.session.get(SessionKey)
      .flatMap(UserRepo.findByEmail)
      .exists(_.isAdmin)
  }

  // Helper: Obtener usuario actual
  private def currentUser(implicit req: RequestHeader): Option[User] = {
    req.session.get(SessionKey).flatMap(UserRepo.findByEmail)
  }

  // Action helper que requiere admin
  private def AdminAction(f: Request[AnyContent] => Result): Action[AnyContent] = 
    Action { implicit req =>
      if (isAdmin) f(req)
      else Redirect(routes.HomeController.index)
        .flashing("error" -> "Acceso denegado. Solo administradores.")
    }

  // GET /admin - Panel principal
  def dashboard = AdminAction { implicit req =>
    val users = UserRepo.all
    val media = MediaRepo.all
    Ok(views.html.admin_dashboard(users, media, currentUser.get))
  }

  // GET /admin/users - Lista de usuarios
  def userList = AdminAction { implicit req =>
    val users = UserRepo.all
    Ok(views.html.admin_users(users, currentUser.get))
  }

  // GET /admin/media - Lista de productos (para editar)
  def mediaList = AdminAction { implicit req =>
    val query = req.getQueryString("q")
    val typeFilter = req.getQueryString("type")
    
    val media = (query, typeFilter) match {
      case (Some(q), _) => MediaRepo.search(q)
      case (_, Some(t)) => MediaRepo.filterByType(MediaType.from(t))
      case _ => MediaRepo.all
    }
    
    Ok(views.html.admin_media(media, currentUser.get, query, typeFilter))
  }

  // GET /admin/stats - Estadísticas avanzadas
  def statistics = AdminAction { implicit req =>
    Ok(views.html.admin_statistics(currentUser.get))
  }

  // GET /admin/media/new - Formulario para crear producto
  def newMediaForm = AdminAction { implicit req =>
    Ok(views.html.admin_media_form(None, currentUser.get))
  }

  // POST /admin/media/new - Crear nuevo producto
  def createMedia = AdminAction { implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val title = data.get("title").flatMap(_.headOption).getOrElse("")
    val description = data.get("description").flatMap(_.headOption).getOrElse("")
    val mtype = data.get("type").flatMap(_.headOption).getOrElse("image")
    val price = data.get("price").flatMap(_.headOption).flatMap(p => scala.util.Try(BigDecimal(p)).toOption).getOrElse(BigDecimal(0))
    val assetPath = data.get("assetPath").flatMap(_.headOption).getOrElse("")

    MediaRepo.add(title, description, MediaType.from(mtype), price, assetPath)
    
    Redirect(routes.AdminController.mediaList)
      .flashing("success" -> s"Producto '$title' creado exitosamente")
  }

  // GET /admin/media/:id/edit - Formulario para editar producto
  def editMediaForm(id: Long) = AdminAction { implicit req =>
    MediaRepo.find(id) match {
      case Some(media) => Ok(views.html.admin_media_form(Some(media), currentUser.get))
      case None => Redirect(routes.AdminController.mediaList)
        .flashing("error" -> "Producto no encontrado")
    }
  }

  // POST /admin/media/:id/edit - Actualizar producto
  def updateMedia(id: Long) = AdminAction { implicit req =>
    val data = req.body.asFormUrlEncoded.getOrElse(Map.empty)
    val title = data.get("title").flatMap(_.headOption).getOrElse("")
    val description = data.get("description").flatMap(_.headOption).getOrElse("")
    val mtype = data.get("type").flatMap(_.headOption).getOrElse("image")
    val price = data.get("price").flatMap(_.headOption).flatMap(p => scala.util.Try(BigDecimal(p)).toOption).getOrElse(BigDecimal(0))
    val assetPath = data.get("assetPath").flatMap(_.headOption).getOrElse("")

    MediaRepo.update(id, title, description, MediaType.from(mtype), price, assetPath)
    
    Redirect(routes.AdminController.mediaList)
      .flashing("success" -> s"Producto actualizado exitosamente")
  }

  // POST /admin/media/:id/delete - Eliminar producto
  def deleteMedia(id: Long) = AdminAction { implicit req =>
    MediaRepo.find(id) match {
      case Some(media) =>
        MediaRepo.delete(id)
        Redirect(routes.AdminController.mediaList)
          .flashing("success" -> s"Producto '${media.title}' eliminado")
      case None =>
        Redirect(routes.AdminController.mediaList)
          .flashing("error" -> "Producto no encontrado")
    }
  }

  // POST /admin/users/:id/toggle-active - Activar/desactivar usuario
  def toggleUserActive(id: Long) = AdminAction { implicit req =>
    UserRepo.findById(id) match {
      case Some(user) =>
        UserRepo.toggleActive(id)
        val status = if (user.isActive) "desactivado" else "activado"
        Redirect(routes.AdminController.userList)
          .flashing("success" -> s"Usuario '${user.name}' $status")
      case None =>
        Redirect(routes.AdminController.userList)
          .flashing("error" -> "Usuario no encontrado")
    }
  }
}
