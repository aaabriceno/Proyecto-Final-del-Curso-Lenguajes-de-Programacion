package controllers

import javax.inject._
import play.api.mvc._
import play.api.i18n._
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
  private def currentUser(implicit req: RequestHeader): User = {
    req.session.get(SessionKey)
      .flatMap(UserRepo.findByEmail)
      .get // Asumimos que siempre existe porque AdminAction ya verificó
  }

  // Action helper que requiere admin
  private def AdminAction(f: MessagesRequest[AnyContent] => Result): Action[AnyContent] = 
    Action { implicit request: Request[AnyContent] =>
      if (isAdmin(request)) {
        val messagesRequest = new MessagesRequest(request, messagesApi)
        f(messagesRequest)
      } else {
        Redirect(routes.HomeController.index)
          .flashing("error" -> "Acceso denegado. Solo administradores.")
      }
    }

  // GET /admin - Panel principal
  def dashboard = AdminAction { implicit req =>
    val users = UserRepo.all
    val media = MediaRepo.all
    Ok(views.html.admin_dashboard(users, media, currentUser))
  }

  // GET /admin/users - Lista de usuarios
  def userList = AdminAction { implicit req =>
    val users = UserRepo.all
    Ok(views.html.admin_users(users, currentUser))
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
    
    Ok(views.html.admin_media(media, currentUser, query, typeFilter))
  }

  // GET /admin/stats - Estadísticas avanzadas
  def statistics = AdminAction { implicit req =>
    Ok(views.html.admin_statistics(currentUser))
  }

  // GET /admin/media/new - Formulario para crear producto
  def newMediaForm = AdminAction { implicit req =>
    Ok(views.html.admin_media_form(None, currentUser))
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
      case Some(media) => Ok(views.html.admin_media_form(Some(media), currentUser))
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
  
  // POST /admin/users/:id/add-balance - Agregar saldo a usuario
  def addBalance(id: Long) = Action { implicit req =>
    // Verificar que sea admin
    if (!isAdmin) {
      Redirect(routes.HomeController.index)
        .flashing("error" -> "Acceso denegado. Solo administradores.")
    } else {
      req.body.asFormUrlEncoded match {
        case Some(formData) =>
          formData.get("amount").flatMap(_.headOption) match {
            case Some(amountStr) =>
              try {
                val amount = BigDecimal(amountStr)
                if (amount > 0) {
                  UserRepo.addBalance(id, amount) match {
                    case Some(user) =>
                      Redirect(routes.AdminController.userList)
                        .flashing("success" -> s"$$${amount} agregados a ${user.name}. Nuevo saldo: $$${user.balance}")
                    case None =>
                      Redirect(routes.AdminController.userList)
                        .flashing("error" -> "Usuario no encontrado")
                  }
                } else {
                  Redirect(routes.AdminController.userList)
                    .flashing("error" -> "El monto debe ser mayor a 0")
                }
              } catch {
                case _: NumberFormatException =>
                  Redirect(routes.AdminController.userList)
                    .flashing("error" -> "Monto inválido")
              }
            case None =>
              Redirect(routes.AdminController.userList)
                .flashing("error" -> "Debes especificar un monto")
          }
        case None =>
          Redirect(routes.AdminController.userList)
            .flashing("error" -> "Datos de formulario inválidos")
      }
    }
  }
  
  // GET /admin/balance-requests - Ver solicitudes de saldo pendientes
  def balanceRequests = AdminAction { implicit req =>
    val pendingRequests = BalanceRequestRepo.findPending
    val requestsWithUsers = pendingRequests.flatMap { request =>
      UserRepo.findById(request.userId).map(user => (request, user))
    }
    Ok(views.html.admin_balance_requests(requestsWithUsers, currentUser))
  }
  
  // POST /admin/balance-requests/:id/approve - Aprobar solicitud
  def approveBalanceRequest(id: Long) = Action { implicit req =>
    if (!isAdmin) {
      Redirect(routes.HomeController.index)
        .flashing("error" -> "Acceso denegado. Solo administradores.")
    } else {
      currentUser(req).id match {
        case adminId =>
          BalanceRequestRepo.approve(id, adminId) match {
            case Some(request) =>
              UserRepo.findById(request.userId) match {
                case Some(user) =>
                  Redirect(routes.AdminController.balanceRequests)
                    .flashing("success" -> s"Solicitud aprobada. $$${request.amount} agregados a ${user.name}")
                case None =>
                  Redirect(routes.AdminController.balanceRequests)
                    .flashing("error" -> "Usuario no encontrado")
              }
            case None =>
              Redirect(routes.AdminController.balanceRequests)
                .flashing("error" -> "Solicitud no encontrada o ya procesada")
          }
      }
    }
  }
  
  // POST /admin/balance-requests/:id/reject - Rechazar solicitud
  def rejectBalanceRequest(id: Long) = Action { implicit req =>
    if (!isAdmin) {
      Redirect(routes.HomeController.index)
        .flashing("error" -> "Acceso denegado. Solo administradores.")
    } else {
      currentUser(req).id match {
        case adminId =>
          BalanceRequestRepo.reject(id, adminId) match {
            case Some(request) =>
              UserRepo.findById(request.userId) match {
                case Some(user) =>
                  Redirect(routes.AdminController.balanceRequests)
                    .flashing("success" -> s"Solicitud de ${user.name} rechazada")
                case None =>
                  Redirect(routes.AdminController.balanceRequests)
                    .flashing("error" -> "Usuario no encontrado")
              }
            case None =>
              Redirect(routes.AdminController.balanceRequests)
                .flashing("error" -> "Solicitud no encontrada o ya procesada")
          }
      }
    }
  }
}
