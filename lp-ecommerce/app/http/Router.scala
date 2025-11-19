package http

import controllers._
import scala.util.Try

/**
 * Router HTTP simple que redirige rutas hacia los controladores.
 * Inspirado en Play Framework, pero totalmente funcional sin él.
 */
object Router {

  def route(request: HttpRequest): HttpResponse = {
    val path   = request.path
    val method = request.method.toUpperCase

    try {
      (method, path) match {

        // ---------- Página principal ----------
        case ("GET", "/") =>
          HomeController.index(request)

        // ---------- Autenticación ----------
        case ("GET", "/login")     => AuthController.loginForm(request)
        case ("POST", "/login")    => AuthController.login(request)
        case ("GET", "/register")  => AuthController.registerForm(request)
        case ("POST", "/register") => AuthController.register(request)
        case ("GET", "/logout") | ("POST", "/logout") =>
          AuthController.logout(request)

        // ---------- Tienda ----------
        case ("GET", "/shop")           => ShopController.shop(request)
        case ("GET", p) if p.startsWith("/shop/") && !p.endsWith("/purchase") =>
          val id = Try(p.stripPrefix("/shop/").toLong).getOrElse(0L)
          ShopController.detail(id, request)
        case ("POST", p) if p.startsWith("/shop/") && p.endsWith("/purchase") =>
          val id = Try(p.stripPrefix("/shop/").stripSuffix("/purchase").toLong).getOrElse(0L)
          ShopController.purchaseItem(id, request)

        // ---------- Carrito ----------
        case ("GET", "/cart")           => ShopController.viewCart(request)
        case ("POST", "/cart/clear")    => ShopController.clearCart(request)
        case ("POST", "/cart/add") =>
          val mediaId = request.formData.getOrElse("mediaId", "0").toLong
          ShopController.addToCart(mediaId, request)
        case ("POST", p) if p.startsWith("/cart/add/") =>
          val id = Try(p.stripPrefix("/cart/add/").toLong).getOrElse(0L)
          ShopController.addToCart(id, request)
        case ("POST", p) if p.startsWith("/cart/remove/") =>
          val id = Try(p.stripPrefix("/cart/remove/").toLong).getOrElse(0L)
          ShopController.removeFromCart(id, request)
        case ("POST", p) if p.startsWith("/cart/update/") =>
          val id = Try(p.stripPrefix("/cart/update/").toLong).getOrElse(0L)
          ShopController.updateCartQuantity(id, request)

        // ---------- Compras ----------
        case ("GET", "/purchase")  => ShopController.purchasePage(request)
        case ("POST", "/purchase") => ShopController.processPurchase(request)

        // ---------- Usuarios ----------
        case ("GET", "/user/account")     => UserController.account(request)
        case ("GET", "/user/info")        => UserController.info(request)
        case ("POST", "/user/info")       => UserController.updateInfo(request)
        case ("GET", "/user/downloads")   => UserController.downloads(request)
        case ("GET", "/user/orders")      => UserController.orders(request)
        case ("GET", "/user/transactions")=> UserController.transactions(request)
        case ("GET", "/user/balance/request")  => UserController.balanceRequestForm(request)
        case ("POST", "/user/balance/request") => UserController.createBalanceRequest(request)
        case ("GET", "/user/ratings") => RatingController.userRatings(request)
        case ("GET", "/user/gifts")   => GiftController.list(request)
        case ("POST", "/rate_content") => RatingController.rate(request)
        case ("GET", p) if p.startsWith("/media/") && p.endsWith("/ratings") =>
          val id = Try(p.stripPrefix("/media/").stripSuffix("/ratings").toLong).getOrElse(0L)
          RatingController.mediaStats(id, request)
        case ("POST", "/gift_content") => GiftController.send(request)
        case ("POST", p) if p.startsWith("/gifts/") && p.endsWith("/claim") =>
          val id = Try(p.split("/")(2).toLong).getOrElse(0L)
          GiftController.claim(id, request)

        // ---------- Administración ----------
        case ("GET", "/admin")              => AdminController.dashboard(request)
        case ("GET", "/admin/users")        => AdminController.users(request)
        case ("GET", "/api/users")          => AdminController.usersJson(request)
        case ("POST", p) if p.startsWith("/admin/users/") && p.endsWith("/toggle") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.toggleUserActive(id, request)

        case ("GET", "/admin/media")        => AdminController.media(request)
        case ("GET", "/admin/media/new")    => AdminController.newMediaForm(request)
        case ("POST", "/admin/media")       => AdminController.createMedia(request)
        case ("GET", "/api/media")          => AdminController.mediaJson(request)
        case ("GET", "/api/promotions/stats") => AdminController.promotionsStats(request)
        case ("GET", "/api/files/list")       => AdminController.listFiles(request)
        case ("GET", p) if p.startsWith("/admin/media/") && p.endsWith("/edit") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.editMediaForm(id, request)
        case ("POST", p) if p.startsWith("/admin/media/") && !p.endsWith("/delete") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.updateMedia(id, request)
        case ("POST", p) if p.endsWith("/delete") && p.startsWith("/admin/media/") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.deleteMedia(id, request)

        // ---------- Categorías ----------
        case ("GET", "/admin/categories")     => AdminController.categories(request)
        case ("POST", "/admin/categories")    => AdminController.createCategory(request)
        case ("GET", "/api/categories")       => AdminController.categoriesJson(request)
        case ("POST", p) if p.endsWith("/delete") && p.startsWith("/admin/categories/") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.deleteCategory(id, request)

        // ---------- Promociones ----------
        case ("GET", "/admin/promotions")      => AdminController.promotions(request)
        case ("GET", "/admin/promotions/new")  => AdminController.newPromotionForm(request)
        case ("POST", "/admin/promotions")     => AdminController.createPromotion(request)
        case ("POST", p) if p.endsWith("/delete") && p.startsWith("/admin/promotions/") =>
          val id = Try(p.split("/")(3).toLong).getOrElse(0L)
          AdminController.deletePromotion(id, request)

        // ---------- Solicitudes de saldo ----------
        case ("GET", "/admin/balance/requests") => AdminController.balanceRequests(request)
        case ("POST", p) if p.startsWith("/admin/balance/requests/") && p.endsWith("/approve") =>
          val parts = p.split("/")
          val id = Try(parts(4).toLong).getOrElse(0L)
          AdminController.approveBalanceRequest(id, request)
        case ("POST", p) if p.startsWith("/admin/balance/requests/") && p.endsWith("/reject") =>
          val parts = p.split("/")
          val id = Try(parts(4).toLong).getOrElse(0L)
          AdminController.rejectBalanceRequest(id, request)

        // ---------- Estadísticas ----------
        case ("GET", "/admin/statistics") => AdminController.statistics(request)
        case ("POST", "/admin/rankings/generate") => RankingController.generateSnapshots(request)
        case ("GET", "/api/rankings/top-products") => RankingController.topProducts(request)
        case ("GET", "/api/rankings/top-rated") => RankingController.topRated(request)
        case ("GET", "/get_downloads_ranking") => RankingController.topUsers(request)

        // ---------- Archivos estáticos ----------
        case ("GET", p) if p.startsWith("/assets/") =>
          val relPath = p.stripPrefix("/assets/")
          HttpResponse.serveStaticFile(relPath)
        case ("GET", p) if p.startsWith("/styles/") =>
          val relPath = p.stripPrefix("/styles/")
          HttpResponse.serveStaticFile(relPath)
        case ("GET", p) if p.startsWith("/scripts/") =>
          val relPath = p.stripPrefix("/scripts/")
          HttpResponse.serveStaticFile(relPath)

        // ---------- Rutas no encontradas ----------
        case _ =>
          HttpResponse.notFound(s"Ruta no encontrada: $method $path")
      }
    } catch {
      case e: Exception =>
        e.printStackTrace()
        HttpResponse.internalError(s"Error en Router: ${e.getMessage}")
    }
  }
}
