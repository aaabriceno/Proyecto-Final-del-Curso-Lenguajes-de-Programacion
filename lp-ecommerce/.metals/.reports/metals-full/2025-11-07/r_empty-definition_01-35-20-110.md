error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/Router.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/Router.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -controllers/HomeController.about.
	 -controllers/HomeController.about#
	 -controllers/HomeController.about().
	 -HomeController.about.
	 -HomeController.about#
	 -HomeController.about().
	 -scala/Predef.HomeController.about.
	 -scala/Predef.HomeController.about#
	 -scala/Predef.HomeController.about().
offset: 699
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/http/Router.scala
text:
```scala
package http

import controllers._

object Router {
  def route(request: HttpRequest): HttpResponse = {
    val path = request.path
    val method = request.method
    
    (method, path) match {
      case ("GET", "/login") => AuthController.loginForm(request)
      case ("POST", "/login") => AuthController.login(request)
      case ("GET", "/register") => AuthController.registerForm(request)
      case ("POST", "/register") => AuthController.register(request)
      case ("GET", "/logout") => AuthController.logout(request)
      case ("POST", "/logout") => AuthController.logout(request)
      case ("GET", "/") => HomeController.index(request)
      case ("GET", "/about") => HomeController.@@about(request)
      case ("GET", "/shop") => ShopController.shop(request)
      case ("GET", path) if path.matches("/shop/(\\d+)") => ShopController.detail(path.split("/")(2).toLong)(request)
      case ("GET", "/cart") => ShopController.viewCart(request)
      case ("POST", path) if path.matches("/cart/add/(\\d+)") => ShopController.addToCart(path.split("/")(3).toLong)(request)
      case ("POST", path) if path.matches("/cart/remove/(\\d+)") => ShopController.removeFromCart(path.split("/")(3).toLong)(request)
      case ("POST", path) if path.matches("/cart/update/(\\d+)") => ShopController.updateCartQuantity(path.split("/")(3).toLong)(request)
      case ("POST", "/cart/clear") => ShopController.clearCart(request)
      case ("GET", "/purchase") => ShopController.purchasePage(request)
      case ("POST", "/purchase") => ShopController.processPurchase(request)
      case ("GET", "/user/account") => UserController.account(request)
      case ("GET", "/user/info") => UserController.info(request)
      case ("POST", "/user/info") => UserController.updateInfo(request)
      case ("GET", "/user/downloads") => UserController.downloads(request)
      case ("GET", "/user/transactions") => UserController.transactions(request)
      case ("GET", "/user/balance/request") => UserController.balanceRequestForm(request)
      case ("POST", "/user/balance/request") => UserController.createBalanceRequest(request)
      case ("GET", "/admin") => AdminController.dashboard(request)
      case ("GET", "/admin/users") => AdminController.users(request)
      case ("POST", path) if path.matches("/admin/users/(\\d+)/toggle") => AdminController.toggleUserActive(path.split("/")(3).toLong)(request)
      case ("GET", "/admin/media") => AdminController.media(request)
      case ("GET", "/admin/media/new") => AdminController.newMediaForm(request)
      case ("POST", "/admin/media") => AdminController.createMedia(request)
      case ("GET", path) if path.matches("/admin/media/(\\d+)/edit") => AdminController.editMediaForm(path.split("/")(3).toLong)(request)
      case ("POST", path) if path.matches("/admin/media/(\\d+)") => AdminController.updateMedia(path.split("/")(3).toLong)(request)
      case ("POST", path) if path.matches("/admin/media/(\\d+)/delete") => AdminController.deleteMedia(path.split("/")(3).toLong)(request)
      case ("GET", "/admin/categories") => AdminController.categories(request)
      case ("POST", "/admin/categories") => AdminController.createCategory(request)
      case ("POST", path) if path.matches("/admin/categories/(\\d+)/delete") => AdminController.deleteCategory(path.split("/")(3).toLong)(request)
      case ("GET", "/admin/promotions") => AdminController.promotions(request)
      case ("GET", "/admin/promotions/new") => AdminController.newPromotionForm(request)
      case ("POST", "/admin/promotions") => AdminController.createPromotion(request)
      case ("POST", path) if path.matches("/admin/promotions/(\\d+)/delete") => AdminController.deletePromotion(path.split("/")(3).toLong)(request)
      case ("GET", "/admin/balance/requests") => AdminController.balanceRequests(request)
      case ("POST", path) if path.matches("/admin/balance/requests/(\\d+)/approve") => AdminController.approveBalanceRequest(path.split("/")(4).toLong)(request)
      case ("POST", path) if path.matches("/admin/balance/requests/(\\d+)/reject") => AdminController.rejectBalanceRequest(path.split("/")(4).toLong)(request)
      case ("GET", "/admin/statistics") => AdminController.statistics(request)
      case ("GET", p) if p.startsWith("/assets/") => HttpResponse.serveStaticFile(path.drop(8))
      case _ => HttpResponse.notFound("Page not found")
    }
  }
}
```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.