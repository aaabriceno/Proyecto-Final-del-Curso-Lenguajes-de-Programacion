package http

import scala.util.matching.Regex

/**
 * Sistema de routing manual con pattern matching
 * SIN frameworks - rutas definidas manualmente
 */
object Router {
  
  type Handler = HttpRequest => HttpResponse
  
  /**
   * Rutear request a handler correspondiente
   */
  def route(request: HttpRequest): HttpResponse = {
    // Logging
    println(s"  → Routing: ${request.method} ${request.path}")
    
    // Pattern matching por método y path
    (request.method, request.path) match {
      
      // ========== RUTAS PÚBLICAS ==========
      
      case ("GET", "/") => 
        // TODO: HomeController.index
        HttpResponse.ok("<h1>Página de inicio</h1><p>Bienvenido al e-commerce</p>")
      
      case ("GET", "/login") =>
        // TODO: AuthController.loginForm
        HttpResponse.ok("<h1>Login</h1><p>Formulario de inicio de sesión</p>")
      
      case ("POST", "/login") =>
        // TODO: AuthController.login
        HttpResponse.ok("<h1>Login POST</h1><p>Procesando inicio de sesión...</p>")
      
      case ("GET", "/register") =>
        // TODO: AuthController.registerForm
        HttpResponse.ok("<h1>Registro</h1><p>Formulario de registro</p>")
      
      case ("POST", "/register") =>
        // TODO: AuthController.register
        HttpResponse.ok("<h1>Registro POST</h1><p>Procesando registro...</p>")
      
      case ("GET", "/logout") =>
        // TODO: AuthController.logout
        HttpResponse.redirect("/")
      
      // ========== RUTAS DE TIENDA ==========
      
      case ("GET", "/shop") =>
        // TODO: ShopController.list
        HttpResponse.ok("<h1>Tienda</h1><p>Lista de productos</p>")
      
      case ("GET", path) if path.startsWith("/shop/") =>
        // Extraer ID del producto
        extractId(path, "/shop/") match {
          case Some(id) =>
            // TODO: ShopController.detail(id)
            HttpResponse.ok(s"<h1>Producto $id</h1><p>Detalles del producto</p>")
          case None =>
            HttpResponse.notFound("Producto no encontrado")
        }
      
      case ("GET", "/cart") =>
        // TODO: CartController.view
        HttpResponse.ok("<h1>Carrito</h1><p>Tu carrito de compras</p>")
      
      case ("POST", "/cart/add") =>
        // TODO: CartController.add
        HttpResponse.redirect("/cart")
      
      case ("POST", "/cart/remove") =>
        // TODO: CartController.remove
        HttpResponse.redirect("/cart")
      
      case ("POST", "/checkout") =>
        // TODO: CartController.checkout
        HttpResponse.redirect("/user/purchases")
      
      // ========== RUTAS DE USUARIO ==========
      
      case ("GET", "/user/account") =>
        // TODO: UserController.account
        HttpResponse.ok("<h1>Mi Cuenta</h1><p>Información de usuario</p>")
      
      case ("GET", "/user/purchases") =>
        // TODO: UserController.purchases
        HttpResponse.ok("<h1>Mis Compras</h1><p>Historial de compras</p>")
      
      case ("POST", "/user/request-balance") =>
        // TODO: UserController.requestBalance
        HttpResponse.redirect("/user/account")
      
      // ========== RUTAS DE ADMIN ==========
      
      case ("GET", "/admin") =>
        // TODO: AdminController.dashboard
        HttpResponse.ok("<h1>Admin Panel</h1><p>Panel de administración</p>")
      
      case ("GET", "/admin/users") =>
        // TODO: AdminController.userList
        HttpResponse.ok("<h1>Usuarios</h1><p>Lista de usuarios</p>")
      
      case ("GET", "/admin/media") =>
        // TODO: AdminController.mediaList
        HttpResponse.ok("<h1>Productos</h1><p>Lista de productos</p>")
      
      case ("GET", "/admin/categories") =>
        // TODO: AdminController.listCategories
        HttpResponse.ok("<h1>Categorías</h1><p>Gestión de categorías</p>")
      
      case ("GET", "/admin/promotions") =>
        // TODO: AdminController.listPromotions
        HttpResponse.ok("<h1>Promociones</h1><p>Gestión de promociones</p>")
      
      case ("GET", "/admin/balance-requests") =>
        // TODO: AdminController.balanceRequests
        HttpResponse.ok("<h1>Solicitudes de Saldo</h1><p>Gestión de solicitudes</p>")
      
      // ========== ARCHIVOS ESTÁTICOS ==========
      
      case ("GET", path) if path.startsWith("/assets/") =>
        // TODO: AssetController.serve
        serveStaticFile(path.stripPrefix("/assets/"))
      
      // ========== 404 NOT FOUND ==========
      
      case _ =>
        HttpResponse.notFound(s"Ruta no encontrada: ${request.method} ${request.path}")
    }
  }
  
  /**
   * Extraer ID numérico de un path
   */
  private def extractId(path: String, prefix: String): Option[Long] = {
    val id = path.stripPrefix(prefix).takeWhile(_.isDigit)
    if (id.nonEmpty) scala.util.Try(id.toLong).toOption else None
  }
  
  /**
   * Servir archivo estático desde public/
   */
  private def serveStaticFile(path: String): HttpResponse = {
    try {
      val file = new java.io.File(s"public/$path")
      if (file.exists() && file.isFile) {
        val content = scala.io.Source.fromFile(file, "UTF-8").mkString
        val mimeType = getMimeType(path)
        
        HttpResponse(
          status = 200,
          statusText = "OK",
          headers = Map("Content-Type" -> mimeType),
          body = content
        )
      } else {
        HttpResponse.notFound(s"Archivo no encontrado: $path")
      }
    } catch {
      case e: Exception =>
        HttpResponse.internalError(s"Error al leer archivo: ${e.getMessage}")
    }
  }
  
  /**
   * Determinar MIME type por extensión
   */
  private def getMimeType(path: String): String = {
    path.split("\\.").lastOption match {
      case Some("css") => "text/css; charset=UTF-8"
      case Some("js") => "application/javascript; charset=UTF-8"
      case Some("html") => "text/html; charset=UTF-8"
      case Some("json") => "application/json; charset=UTF-8"
      case Some("png") => "image/png"
      case Some("jpg" | "jpeg") => "image/jpeg"
      case Some("gif") => "image/gif"
      case Some("svg") => "image/svg+xml"
      case Some("ico") => "image/x-icon"
      case Some("woff") => "font/woff"
      case Some("woff2") => "font/woff2"
      case Some("ttf") => "font/ttf"
      case Some("eot") => "application/vnd.ms-fontobject"
      case _ => "application/octet-stream"
    }
  }
}
