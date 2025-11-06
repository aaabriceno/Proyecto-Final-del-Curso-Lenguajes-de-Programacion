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
    val stock = data.get("stock").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
    val categoryId = data.get("category_id").flatMap(_.headOption).flatMap(c => 
      if (c.isEmpty) None else scala.util.Try(c.toLong).toOption
    )

    MediaRepo.add(title, description, MediaType.from(mtype), price, categoryId, assetPath, stock)
    
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
    val stock = data.get("stock").flatMap(_.headOption).flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0)
    val categoryId = data.get("category_id").flatMap(_.headOption).flatMap(c => 
      if (c.isEmpty) None else scala.util.Try(c.toLong).toOption
    )

    MediaRepo.update(id, title, description, MediaType.from(mtype), price, categoryId, assetPath, stock)
    
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
  
  // ============ GESTIÓN DE CATEGORÍAS ============
  
  // GET /admin/categories - Lista de categorías
  def listCategories = AdminAction { implicit request =>
    val admin = currentUser
    val categories = CategoryRepo.getTree
    Ok(views.html.admin_categories(categories, admin))
  }
  
  // POST /admin/categories/create - Crear categoría
  def createCategory = AdminAction { implicit request =>
    request.body.asFormUrlEncoded match {
      case Some(form) =>
        val name = form.get("name").flatMap(_.headOption).getOrElse("")
        val description = form.get("description").flatMap(_.headOption).getOrElse("")
        val parentId = form.get("parent_id").flatMap(_.headOption).flatMap(id => 
          if (id.isEmpty) None else Some(id.toLong)
        )
        
        if (name.trim.isEmpty) {
          Redirect(routes.AdminController.listCategories)
            .flashing("error" -> "El nombre de la categoría no puede estar vacío")
        } else {
          try {
            val category = CategoryRepo.add(name.trim, parentId, description.trim)
            Redirect(routes.AdminController.listCategories)
              .flashing("success" -> s"Categoría '${category.name}' creada exitosamente")
          } catch {
            case e: IllegalArgumentException =>
              Redirect(routes.AdminController.listCategories)
                .flashing("error" -> e.getMessage)
          }
        }
      case None =>
        Redirect(routes.AdminController.listCategories)
          .flashing("error" -> "Datos del formulario inválidos")
    }
  }
  
  // POST /admin/categories/:id/update - Actualizar categoría
  def updateCategory(id: Long) = AdminAction { implicit request =>
    request.body.asFormUrlEncoded match {
      case Some(form) =>
        val name = form.get("name").flatMap(_.headOption).getOrElse("")
        val description = form.get("description").flatMap(_.headOption).getOrElse("")
        val parentId = form.get("parent_id").flatMap(_.headOption).flatMap(pid => 
          if (pid.isEmpty) None else Some(pid.toLong)
        )
        
        if (name.trim.isEmpty) {
          Redirect(routes.AdminController.listCategories)
            .flashing("error" -> "El nombre de la categoría no puede estar vacío")
        } else {
          try {
            CategoryRepo.update(id, name.trim, parentId, description.trim) match {
              case Some(category) =>
                Redirect(routes.AdminController.listCategories)
                  .flashing("success" -> s"Categoría '${category.name}' actualizada")
              case None =>
                Redirect(routes.AdminController.listCategories)
                  .flashing("error" -> "Categoría no encontrada")
            }
          } catch {
            case e: IllegalArgumentException =>
              Redirect(routes.AdminController.listCategories)
                .flashing("error" -> e.getMessage)
          }
        }
      case None =>
        Redirect(routes.AdminController.listCategories)
          .flashing("error" -> "Datos del formulario inválidos")
    }
  }
  
  // POST /admin/categories/:id/delete - Eliminar categoría
  def deleteCategory(id: Long) = AdminAction { implicit request =>
    try {
      CategoryRepo.find(id) match {
        case Some(category) =>
          if (CategoryRepo.delete(id)) {
            Redirect(routes.AdminController.listCategories)
              .flashing("success" -> s"Categoría '${category.name}' eliminada")
          } else {
            Redirect(routes.AdminController.listCategories)
              .flashing("error" -> "No se pudo eliminar la categoría")
          }
        case None =>
          Redirect(routes.AdminController.listCategories)
            .flashing("error" -> "Categoría no encontrada")
      }
    } catch {
      case e: IllegalArgumentException =>
        Redirect(routes.AdminController.listCategories)
          .flashing("error" -> e.getMessage)
    }
  }

  // ========== GESTIÓN DE PROMOCIONES ==========

  // GET /admin/promotions - Lista de promociones
  def listPromotions = AdminAction { implicit request =>
    val promotions = PromotionRepo.all
    Ok(views.html.admin_promotions(promotions))
  }

  // GET /admin/promotions/new - Formulario nueva promoción
  def newPromotionForm = AdminAction { implicit request =>
    val categories = CategoryRepo.all
    val mediaList = MediaRepo.all
    Ok(views.html.admin_promotion_form(None, categories, mediaList))
  }

  // POST /admin/promotions/create - Crear promoción
  def createPromotion = AdminAction { implicit request =>
    val data = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val name = data.get("name").flatMap(_.headOption).getOrElse("")
    val description = data.get("description").flatMap(_.headOption).getOrElse("")
    val discountPercent = data.get("discountPercent").flatMap(_.headOption).flatMap(d => scala.util.Try(d.toInt).toOption).getOrElse(0)
    
    val startDate = data.get("startDate").flatMap(_.headOption).flatMap { d =>
      scala.util.Try(java.time.LocalDateTime.parse(d)).toOption
    }.getOrElse(java.time.LocalDateTime.now())
    
    val endDate = data.get("endDate").flatMap(_.headOption).flatMap { d =>
      scala.util.Try(java.time.LocalDateTime.parse(d)).toOption
    }.getOrElse(java.time.LocalDateTime.now().plusDays(7))
    
    val targetType = data.get("targetType").flatMap(_.headOption).map(PromotionTarget.from).getOrElse(PromotionTarget.All)
    
    val targetIds = data.get("targetIds").flatMap(_.headOption).map { ids =>
      ids.split(",").flatMap(id => scala.util.Try(id.trim.toLong).toOption).toVector
    }.getOrElse(Vector.empty)

    PromotionRepo.create(name, description, discountPercent, startDate, endDate, targetType, targetIds)
    
    Redirect(routes.AdminController.listPromotions)
      .flashing("success" -> s"Promoción '$name' creada exitosamente")
  }

  // GET /admin/promotions/:id/edit - Formulario editar promoción
  def editPromotionForm(id: Long) = AdminAction { implicit request =>
    PromotionRepo.find(id) match {
      case Some(promo) =>
        val categories = CategoryRepo.all
        val mediaList = MediaRepo.all
        Ok(views.html.admin_promotion_form(Some(promo), categories, mediaList))
      case None => Redirect(routes.AdminController.listPromotions)
        .flashing("error" -> "Promoción no encontrada")
    }
  }

  // POST /admin/promotions/:id/edit - Actualizar promoción
  def updatePromotion(id: Long) = AdminAction { implicit request =>
    val data = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val name = data.get("name").flatMap(_.headOption).getOrElse("")
    val description = data.get("description").flatMap(_.headOption).getOrElse("")
    val discountPercent = data.get("discountPercent").flatMap(_.headOption).flatMap(d => scala.util.Try(d.toInt).toOption).getOrElse(0)
    
    val startDate = data.get("startDate").flatMap(_.headOption).flatMap { d =>
      scala.util.Try(java.time.LocalDateTime.parse(d)).toOption
    }.getOrElse(java.time.LocalDateTime.now())
    
    val endDate = data.get("endDate").flatMap(_.headOption).flatMap { d =>
      scala.util.Try(java.time.LocalDateTime.parse(d)).toOption
    }.getOrElse(java.time.LocalDateTime.now().plusDays(7))
    
    val targetType = data.get("targetType").flatMap(_.headOption).map(PromotionTarget.from).getOrElse(PromotionTarget.All)
    
    val targetIds = data.get("targetIds").flatMap(_.headOption).map { ids =>
      ids.split(",").flatMap(id => scala.util.Try(id.trim.toLong).toOption).toVector
    }.getOrElse(Vector.empty)
    
    val isActive = data.get("isActive").flatMap(_.headOption).contains("true")

    PromotionRepo.update(id, name, description, discountPercent, startDate, endDate, targetType, targetIds, isActive)
    
    Redirect(routes.AdminController.listPromotions)
      .flashing("success" -> "Promoción actualizada exitosamente")
  }

  // POST /admin/promotions/:id/delete - Eliminar promoción
  def deletePromotion(id: Long) = AdminAction { implicit request =>
    PromotionRepo.find(id) match {
      case Some(promo) =>
        PromotionRepo.delete(id)
        Redirect(routes.AdminController.listPromotions)
          .flashing("success" -> s"Promoción '${promo.name}' eliminada")
      case None =>
        Redirect(routes.AdminController.listPromotions)
          .flashing("error" -> "Promoción no encontrada")
    }
  }

  // POST /admin/promotions/:id/toggle - Pausar/reanudar promoción
  def togglePromotion(id: Long) = AdminAction { implicit request =>
    PromotionRepo.toggleActive(id) match {
      case Some(promo) =>
        val status = if (promo.isActive) "activada" else "pausada"
        Redirect(routes.AdminController.listPromotions)
          .flashing("success" -> s"Promoción '${promo.name}' $status")
      case None =>
        Redirect(routes.AdminController.listPromotions)
          .flashing("error" -> "Promoción no encontrada")
    }
  }
}

