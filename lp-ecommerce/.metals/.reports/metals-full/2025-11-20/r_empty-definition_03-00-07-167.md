error id: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AdminController.scala:`<none>`.
file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AdminController.scala
empty definition using pc, found symbol in pc: `<none>`.
empty definition using semanticdb
empty definition using fallback
non-local guesses:
	 -models/media/price.
	 -models/media/price#
	 -models/media/price().
	 -media/price.
	 -media/price#
	 -media/price().
	 -scala/Predef.media.price.
	 -scala/Predef.media.price#
	 -scala/Predef.media.price().
offset: 22440
uri: file:///C:/Users/PC/Proyecto-Final-del-Curso-Lenguajes-de-Programacion/lp-ecommerce/app/controllers/AdminController.scala
text:
```scala
package controllers

import http.{HttpRequest, HttpResponse}
import models._
import services.AnalyticsService
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}
import scala.io.Source

/**
 * Controlador del Panel de Administración
 */
object AdminController {

  /** 
   * Sirve una página HTML desde /app/views/
   */
  private def serveHtml(filename: String, request: HttpRequest = null): HttpResponse = {
    val projectDir = System.getProperty("user.dir")
    val path = Paths.get(projectDir, "app", "views", s"$filename.html")
    Try(Files.readString(path)) match {
      case Success(html) => 
        val response = HttpResponse.ok(html)
        // Preservar la cookie de sesión si existe
        if (request != null && request.cookies.contains("sessionId")) {
          response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
        } else {
          response
        }
      case Failure(e) =>
        HttpResponse.notFound(s"No se pudo cargar la página: ${e.getMessage}<br>Ruta intentada: $path")
    }
  }

  /** GET /admin */
  def dashboard(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val projectDir = System.getProperty("user.dir")
        val path = Paths.get(projectDir, "app", "views", "admin_dashboard.html")
        Try(Files.readString(path)) match {
          case Success(template) =>
            val users = UserRepo.all
            val totalUsers = users.size
            val totalAdmins = users.count(_.isAdmin)
            val totalProducts = MediaRepo.all.size
            val promotions = Try(PromotionRepo.all).getOrElse(Seq.empty)
            import java.time.LocalDateTime
            val now = LocalDateTime.now()
            val activePromos = promotions.count(p => !p.startDate.isAfter(now) && !p.endDate.isBefore(now))
            val upcomingPromos = promotions.count(_.startDate.isAfter(now))

        val topRows = buildTopPurchasesRows()

        val replacements = Map(
          "__TOTAL_USERS__" -> totalUsers.toString,
          "__TOTAL_PRODUCTS__" -> totalProducts.toString,
          "__TOTAL_ADMINS__" -> totalAdmins.toString,
          "__PROMO_ACTIVE__" -> activePromos.toString,
          "__PROMO_UPCOMING__" -> upcomingPromos.toString,
          "<!-- TOP_PURCHASES_ROWS -->" -> topRows,
          "\"products\": [" -> s""""products": [""",
          "\"isAdmin\": false" -> "\"isAdmin\": true"
        )

            val filled = replacePlaceholders(template, replacements)
            val response = HttpResponse.ok(filled)
            if (request.cookies.contains("sessionId"))
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            else response
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar el dashboard: ${e.getMessage}")
        }
      case Left(res) => res
    }
  }

  /** GET /admin/users */
  def users(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        serveHtml("admin_users", request)
      case Left(res) => res
    }
  }

  /** GET /api/users - API JSON para obtener todos los usuarios */
  def usersJson(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val allUsers = Try(UserRepo.all).getOrElse(Seq.empty)
        
        // Generar JSON manualmente
        val usersJsonArray = allUsers.map { user =>
          s"""{
  "id": ${user.id},
  "email": "${escapeJson(user.email)}",
  "username": "${escapeJson(user.name)}",
  "isAdmin": ${user.isAdmin},
  "isActive": ${user.isActive},
  "balance": ${user.balance},
  "totalSpent": ${user.totalSpent}
}""".stripMargin
        }.mkString(",\n")
        
        val json = s"""{"users": [\n$usersJsonArray\n]}"""
        
        HttpResponse(
          status = 200,
          statusText = "OK",
          headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
          body = json
        )
      case Left(res) => res
    }
  }

  /** POST /admin/users/:id/toggle */
  def toggleUserActive(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        UserRepo.toggleActive(id)
        HttpResponse.redirect("/admin/users?success=Usuario+actualizado")
      case Left(res) => res
    }
  }

  /** GET /admin/media */
  def media(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) => serveHtml("admin_media", request)
      case Left(res) => res
    }
  }

  /** GET /api/media - API JSON para obtener todos los productos (PÚBLICO) */
  def mediaJson(request: HttpRequest): HttpResponse = {
    // NOTA: Este endpoint es público para que la tienda pueda mostrar productos
    import java.time.LocalDateTime
    val now = LocalDateTime.now()
    
    try {
      val allMedia = MediaRepo.all
      val allPromotions = Try(PromotionRepo.all).getOrElse(Seq.empty)
      
      println(s"📦 [API] Cargando ${allMedia.size} productos para JSON")
      
      // Generar JSON manualmente (sin librerías externas)
      val mediaJsonArray = allMedia.map { media =>
        // Buscar promoción activa para este producto (por producto O por categoría)
        val activePromotion = allPromotions.find { promo =>
          val isActive = !promo.startDate.isAfter(now) && !promo.endDate.isBefore(now)
          if (!isActive) false
          else {
            promo.targetType match {
              case PromotionTarget.Product => promo.targetIds.contains(media.id)
              case PromotionTarget.Category => 
                media.categoryId.exists(catId => promo.targetIds.contains(catId))
              case _ => false
            }
          }
        }
        
        val (hasPromotion, discountPercent, discountedPrice) = activePromotion match {
          case Some(promo) =>
            val discount = promo.discountPercent
            val newPrice = media.price * (100 - discount) / 100
            (true, discount, newPrice)
          case None =>
            (false, 0, media.price)
        }
        
        // Obtener ruta completa de la categoría (breadcrumb)
        val categoryPath = media.categoryId match {
          case Some(catId) =>
            val breadcrumb = CategoryRepo.getBreadcrumb(catId)
            escapeJson(breadcrumb.map(_.name).mkString(" > "))
          case None => "Sin categoría"
        }
        
        s"""{
  "id": ${media.id},
  "title": "${escapeJson(media.title)}",
  "description": "${escapeJson(media.description)}",
  "productType": "${media.productType.asString}",
  "categoryId": ${media.categoryId.getOrElse("null")},
  "categoryPath": "$categoryPath",
  "assetPath": "${escapeJson(media.assetPath)}",
  "price": ${media.price},
  "rating": ${media.rating},
  "downloadCount": ${media.downloads},
  "stock": ${media.stock},
  "coverImage": "${escapeJson(media.getCoverImageUrl)}",
  "hasPromotion": $hasPromotion,
  "discountPercent": $discountPercent,
  "discountedPrice": $discountedPrice
}""".stripMargin
      }.mkString(",\n")
      
      val isAdminFlag = AuthController.getCurrentUser(request).flatMap(UserRepo.findByEmail).exists(_.isAdmin)
      val json = s"""{"products": [\n$mediaJsonArray\n], "isAdmin": $isAdminFlag}"""
      
      HttpResponse(
        status = 200,
        statusText = "OK",
        headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
        body = json
      )
    } catch {
      case e: Exception =>
        println(s"❌ Error generando JSON de productos: ${e.getMessage}")
        e.printStackTrace()
        HttpResponse(
          status = 500,
          statusText = "Internal Server Error",
          headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
          body = s"""{"error": "Error cargando productos: ${escapeJson(e.getMessage)}"}"""
        )
    }
  }
  
  /** Escapa JSON para prevenir errores de sintaxis */
  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\")
     .replace("\"", "\\\"")
     .replace("\n", "\\n")
     .replace("\r", "\\r")
     .replace("\t", "\\t")

  private def escapeHtml(s: String): String =
    s.replace("&", "&amp;")
     .replace("<", "&lt;")
     .replace(">", "&gt;")
     .replace("\"", "&quot;")
     .replace("'", "&#x27;")

  private def formatMoney(amount: BigDecimal): String =
    f"$$${amount}%.2f"

  private def replacePlaceholders(template: String, replacements: Map[String, String]): String =
    replacements.foldLeft(template) { case (acc, (key, value)) => acc.replace(key, value) }

  private def buildTopPurchasesRows(): String = {
    val topProducts = AnalyticsService.topPurchasedMedia(5)
    if (topProducts.isEmpty) {
      """<tr><td colspan="5" class="text-center py-3" style="color:#1976d2;">Aún no hay compras registradas</td></tr>"""
    } else {
      topProducts.map { case (media, quantity, revenue) =>
        s"""
<tr>
  <td>${escapeHtml(media.title)}</td>
  <td>${media.productType.asString.capitalize}</td>
  <td>${formatMoney(media.price)}</td>
  <td>${media.rating}</td>
  <td><strong>$quantity</strong></td>
</tr>
         """.stripMargin
      }.mkString
    }
  }

  /** GET /admin/media/new */
  def newMediaForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) => serveHtml("addContent", request)
      case Left(res) => res
    }
  }

  /** POST /admin/media */
  def createMedia(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        // 🔍 DEBUG: Ver headers y body crudo
        println("🔍 [CREATE MEDIA] Headers recibidos:")
        request.headers.foreach { case (key, value) => println(s"  $key: $value") }
        println("🔍 [CREATE MEDIA] Body crudo:")
        println(s"  ${request.body.getOrElse("(sin body)")}")
        
        val data = request.formData
        
        // 🔍 DEBUG: Ver qué datos llegan del formulario
        println("🔍 [CREATE MEDIA] FormData completo:")
        data.foreach { case (key, value) => println(s"  $key = '$value'") }
        
        val title = data.getOrElse("title", "")
        val desc = data.getOrElse("description", "")
        val price = data.get("price").flatMap(_.toDoubleOption).getOrElse(0.0)
        val catId = data.get("categoryId").flatMap(_.toLongOption)
        val productType = ProductType.from(data.getOrElse("productType", "digital"))
        val url = data.getOrElse("url", "")
        val stock = data.get("stock").flatMap(_.toIntOption).getOrElse(0)
        val promotionId = data.get("promotionId").flatMap(_.toLongOption) // Opcional
        
        println(s"🔍 [CREATE MEDIA] Valores parseados:")
        println(s"  title = '$title'")
        println(s"  price = $price")
        println(s"  productType = ${data.get("productType")} → ${productType.asString}")
        println(s"  stock = $stock")
        println(s"  url = '$url'")
        println(s"  categoryId = ${data.get("categoryId")} → $catId")
        println(s"  promotionId = $promotionId")

        // MediaRepo.add(title, description, productType, price, categoryId, assetPath, stock, promotionId)
        MediaRepo.add(title, desc, productType, BigDecimal(price), catId, url, stock, promotionId)
        HttpResponse.redirect("/admin/media?success=Medio+creado+exitosamente")
      case Left(res) => res
    }
  }

  /** GET /admin/media/:id/edit */
  def editMediaForm(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        MediaRepo.find(id) match {
          case Some(media) =>
            // Cargar el nuevo formulario con selector en cascada
            val projectDir = System.getProperty("user.dir")
            val path = s"$projectDir/app/views/item_info_edit.html"
            
            val html = Try(scala.io.Source.fromFile(path, "UTF-8").mkString) match {
              case Success(content) => content
              case Failure(_) =>
                s"""<html><body><h1>Error: No se pudo cargar el formulario</h1></body></html>"""
            }
            HttpResponse.ok(html)
          case None =>
            HttpResponse.redirect("/admin/media?error=Medio+no+encontrado")
        }
      case Left(res) => res
    }
  }
  
  /** POST /admin/media/:id */
  def updateMedia(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        val title = data.getOrElse("title", "")
        val desc = data.getOrElse("description", "")
        val price = data.get("price").flatMap(_.toDoubleOption).getOrElse(0.0)
        val catId = data.get("categoryId").flatMap(_.toLongOption)
        val productType = ProductType.from(data.getOrElse("productType", "digital"))
        val url = data.getOrElse("url", "")
        val stock = data.get("stock").flatMap(_.toIntOption).getOrElse(0)
        val promotionId = data.get("promotionId").flatMap(_.toLongOption)

        println(s"🔍 [UPDATE] Valores parseados:")
        println(s"  id = $id")
        println(s"  title = '$title'")
        println(s"  price = $price")
        println(s"  productType = ${data.get("productType")} → ${productType.asString}")
        println(s"  stock = $stock")
        println(s"  url = '$url'")
        println(s"  categoryId = ${data.get("categoryId")} → $catId")
        println(s"  promotionId = $promotionId")

        // MediaRepo.update(id, title, description, productType, price, categoryId, assetPath, stock, promotionId)
        MediaRepo.update(id, title, desc, productType, BigDecimal(price), catId, url, stock, promotionId)
        HttpResponse.redirect("/admin/media?success=Medio+actualizado")
      case Left(res) => res
    }
  }

  /** POST /admin/media/:id/delete */
  def deleteMedia(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        MediaRepo.delete(id)
        HttpResponse.redirect("/admin/media?success=Medio+eliminado")
      case Left(res) => res
    }
  }

  /** GET /admin/categories */
  def categories(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        serveHtml("admin_categories", request)
      case Left(res) => res
    }
  }

  /** GET /api/categories - API JSON para obtener todas las categorías */
  def categoriesJson(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val allCategories = Try(CategoryRepo.all).getOrElse(Seq.empty)
        println(s"[API] /api/categories - total: ${allCategories.size}")
        // Generar JSON con breadcrumb (ruta completa) para cada categoría
        val categoriesJsonArray = allCategories.map { cat =>
          val breadcrumb = CategoryRepo.getBreadcrumb(cat.id)
          val path = breadcrumb.map(_.name).mkString(" > ")
          val level = breadcrumb.length - 1
          
          s"""{
  \"id\": ${cat.id},
  \"name\": \"${escapeJson(cat.name)}\",
  \"parentId\": ${if (cat.parentId.isEmpty || cat.parentId.contains(0L)) "null" else cat.parentId.get},
  \"description\": \"${escapeJson(cat.description)}\",
  \"productType\": \"${escapeJson(cat.productType)}\",
  \"path\": \"${escapeJson(path)}\",
  \"level\": $level
}""".stripMargin
        }.mkString(",\n")
        val json = s"""{"categories": [
$categoriesJsonArray
]}"""
        println(s"[API] /api/categories JSON: $json")
        
        HttpResponse(
          status = 200,
          statusText = "OK",
          headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
          body = json
        )
      case Left(res) => res
    }
  }

  /** POST /admin/categories */
  def createCategory(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        val name = data.getOrElse("name", "")
        val desc = data.getOrElse("description", "")
        val parent = data.get("parent_id").flatMap(_.toLongOption)
        val productType = parent.flatMap(CategoryRepo.find).map(_.productType).getOrElse("digital")
        CategoryRepo.add(name, parent, desc, productType)
        HttpResponse.redirect("/admin/categories?success=Categoria+creada")
      case Left(res) => res
    }
  }

  /** POST /admin/categories/:id/delete */
  def deleteCategory(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        CategoryRepo.delete(id)
        HttpResponse.redirect("/admin/categories?success=Categoria+eliminada")
      case Left(res) => res
    }
  }

  /** GET /admin/promotions */
  def promotions(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val allPromotions = Try(PromotionRepo.all) match {
          case Success(promos) => promos
          case Failure(ex) =>
            println(s"❌ ERROR al leer promociones de MongoDB:")
            ex.printStackTrace()
            Seq.empty
        }
        
        println(s"🔍 DEBUG: Encontradas ${allPromotions.size} promociones en MongoDB")
        allPromotions.foreach(p => println(s"  - ${p.name} (${p.discountPercent}% OFF) - targetIds: ${p.targetIds}"))
        
        // Calcular promociones por estado temporal
        import java.time.LocalDateTime
        val now = LocalDateTime.now()
        
        val activePromotions = allPromotions.filter { p =>
          !p.startDate.isAfter(now) && !p.endDate.isBefore(now)
        }
        val upcomingPromotions = allPromotions.filter { p =>
          p.startDate.isAfter(now)
        }
        val inactivePromotions = allPromotions.filter { p =>
          p.endDate.isBefore(now)
        }
        
        println(s"📊 Activas: ${activePromotions.size} | Próximas: ${upcomingPromotions.size} | Inactivas: ${inactivePromotions.size}")
        
        // Generar HTML completo desde cero
        val promotionsRows = if (allPromotions.isEmpty) {
          """<tr>
                <td colspan="6" class="text-center text-muted py-4">
                  No hay promociones activas 😴
                </td>
              </tr>"""
        } else {
          allPromotions.map { promo =>
            val mediaNames = promo.targetIds.flatMap(id => MediaRepo.find(id)).map(_.title).mkString(", ")
            val targetDisplay = if (mediaNames.isEmpty) "Todos los productos" else mediaNames
            
            // Determinar estado de la promoción
            val (statusBadge, statusClass) = if (promo.startDate.isAfter(now)) {
              ("⏰ Próxima", "bg-info")
            } else if (promo.endDate.isBefore(now)) {
              ("❌ Inactiva", "bg-secondary")
            } else {
              ("✅ Activa", "bg-success")
            }
            
            s"""<tr>
              <td>${escapeHtml(targetDisplay)}</td>
              <td><span class="badge bg-warning text-dark">${promo.discountPercent}% OFF</span></td>
              <td>${promo.startDate.toLocalDate()}</td>
              <td>${promo.endDate.toLocalDate()}</td>
              <td><span class="badge $statusClass">$statusBadge</span></td>
              <td>
                <form method="POST" action="/admin/promotions/${promo.id}/delete" class="d-inline">
                  <button type="submit" class="btn btn-sm btn-danger" onclick="return confirm('¿Eliminar promoción?')">
                    🗑️
                  </button>
                </form>
              </td>
            </tr>"""
          }.mkString("\n")
        }
        
        val template = serveHtml("admin_promotions", request).body
        val replacements = Map(
          "__ACTIVE_COUNT__" -> activePromotions.size.toString,
          "__UPCOMING_COUNT__" -> upcomingPromotions.size.toString,
          "__INACTIVE_COUNT__" -> inactivePromotions.size.toString,
          "<!-- PROMOTIONS_ROWS -->" -> promotionsRows
        )
        val html = replacePlaceholders(template, replacements)
        
        HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), html)
      case Left(res) => res
    }
  }

  /** GET /admin/promotions/new */
  def newPromotionForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val allMedia = Try(MediaRepo.all).getOrElse(Seq.empty)
        val allPromotions = Try(PromotionRepo.all).getOrElse(Seq.empty)
        
        // Filtrar productos que YA tienen promoción
        val mediaWithPromo = allPromotions
          .filter(_.targetType == PromotionTarget.Product)
          .flatMap(_.targetIds)
          .toSet
        
        val availableMedia = allMedia.filterNot(m => mediaWithPromo.contains(m.id))
        
        val html = serveHtml("admin_promotion_form", request).body
        
        val mediaOptions = if (availableMedia.isEmpty) {
          """<option value="">No hay productos sin promoción</option>"""
        } else {
          availableMedia.map { media =>
            s"""<option value="${media.id}">${escapeHtml(media.title)} ($$${media.price@@})</option>"""
          }.mkString("\n")
        }
        
        val updatedHtml = html.replace(
          """<option value="">Selecciona un producto</option>
              <!-- Aquí se insertarán dinámicamente los productos -->""",
          s"""<option value="">Selecciona un producto</option>
              $mediaOptions"""
        )
        
        HttpResponse(200, "OK", Map("Content-Type" -> "text/html; charset=UTF-8"), updatedHtml)
      case Left(res) => res
    }
  }

  /** POST /admin/promotions */
  def createPromotion(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        
        // Obtener media_id del formulario
        val mediaId = data.get("media_id").flatMap(_.toLongOption).getOrElse(0L)
        val discount = data.get("discount").flatMap(_.toIntOption).getOrElse(10)
        
        // Verificar si ya existe una promoción para este producto
        val existingPromo = PromotionRepo.all.find { p =>
          p.targetType == PromotionTarget.Product && p.targetIds.contains(mediaId)
        }
        
        if (existingPromo.isDefined) {
          return HttpResponse.redirect(s"/admin/promotions?error=Ya+existe+una+promoción+para+este+producto")
        }
        
        // Obtener fechas del formulario
        val startStr = data.getOrElse("start_date", "")
        val endStr = data.getOrElse("end_date", "")
        
        val start = Try(java.time.LocalDateTime.parse(startStr)).getOrElse(java.time.LocalDateTime.now())
        val end = Try(java.time.LocalDateTime.parse(endStr)).getOrElse(start.plusMonths(1))
        
        // Crear nombre basado en el producto
        val name = MediaRepo.find(mediaId).map(m => s"Descuento ${discount}% en ${m.title}").getOrElse("Promoción")
        
        PromotionRepo.create(
          name,
          s"${discount}% de descuento",
          discount,
          start,
          end,
          PromotionTarget.Product,
          Vector(mediaId)
        )
        HttpResponse.redirect("/admin/promotions?success=Promocion+creada")
      case Left(res) => res
    }
  }

  /** POST /admin/promotions/:id/delete */
  def deletePromotion(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        PromotionRepo.delete(id)
        HttpResponse.redirect("/admin/promotions?success=Promocion+eliminada")
      case Left(res) => res
    }
  }

  /** GET /admin/balance/requests */
  def balanceRequests(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val projectDir = System.getProperty("user.dir")
        val path = s"$projectDir/app/views/admin_balance_requests.html"
        
        Try(Source.fromFile(path, "UTF-8").mkString) match {
          case Success(html) =>
            val pending = BalanceRequestRepo.findPending
            val pendingCount = pending.size
            
            // Generar filas HTML dinámicamente
            val requestsHtml = if (pending.isEmpty) {
              """
              <div class="p-5 text-center">
                <i class="bi bi-check-circle display-1 text-success"></i>
                <h4 class="mt-3 fw-semibold">No hay solicitudes pendientes</h4>
                <p class="text-muted mb-4">Todas las solicitudes han sido procesadas correctamente.</p>
                <a href="/admin" class="btn btn-primary">
                  <i class="bi bi-arrow-left me-2"></i> Volver al Dashboard
                </a>
              </div>
              """
            } else {
              val rows = pending.map { req =>
                val user = UserRepo.findById(req.userId).getOrElse(User(0, "Desconocido", "", "", ""))
                s"""
                <tr>
                  <td>#${req.id}</td>
                  <td>${user.name}<br><small class="text-muted">${user.email}</small></td>
                  <td class="fw-bold text-success">$$${req.amount}</td>
                  <td>${req.paymentMethod}</td>
                  <td>${req.requestDate.toString.substring(0, 16)}</td>
                  <td>
                    <form method="POST" action="/admin/balance/requests/${req.id}/approve" style="display:inline;">
                      <button type="submit" class="btn btn-sm btn-success">
                        <i class="bi bi-check-circle me-1"></i> Aprobar
                      </button>
                    </form>
                    <form method="POST" action="/admin/balance/requests/${req.id}/reject" style="display:inline;">
                      <button type="submit" class="btn btn-sm btn-danger">
                        <i class="bi bi-x-circle me-1"></i> Rechazar
                      </button>
                    </form>
                  </td>
                </tr>
                """
              }.mkString("\n")
              
              s"""
              <div class="table-responsive">
                <table class="table table-dark table-hover align-middle mb-0">
                  <thead class="table-secondary">
                    <tr>
                      <th>ID</th>
                      <th>Usuario</th>
                      <th>Monto</th>
                      <th>Método</th>
                      <th>Fecha</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    $rows
                  </tbody>
                </table>
              </div>
              """
            }
            
            // Reemplazar placeholders
            val updatedHtml = html
              .replaceAll("<strong>0</strong>", s"<strong>$pendingCount</strong>")
              .replaceAll("""<span class="position-absolute[^>]*>\s*0\s*</span>""", 
                java.util.regex.Matcher.quoteReplacement(s"""<span class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger">$pendingCount</span>"""))
              .replaceAll("(?s)<!-- REQUESTS_TABLE_PLACEHOLDER -->.*?<!-- END_REQUESTS_TABLE_PLACEHOLDER -->", 
                java.util.regex.Matcher.quoteReplacement(requestsHtml))
            
            val response = HttpResponse.ok(updatedHtml)
            if (request.cookies.contains("sessionId")) {
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            } else {
              response
            }
          case Failure(e) =>
            HttpResponse.notFound(s"Error cargando solicitudes: ${e.getMessage}")
        }
      case Left(res) => res
    }
  }

  /** POST /admin/balance/requests/:id/approve */
  def approveBalanceRequest(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(admin) =>
        BalanceRequestRepo.approve(id, admin.id)
        HttpResponse.redirect("/admin/balance/requests?success=Solicitud+aprobada")
      case Left(res) => res
    }
  }

  /** POST /admin/balance/requests/:id/reject */
  def rejectBalanceRequest(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(admin) =>
        BalanceRequestRepo.reject(id, admin.id)
        HttpResponse.redirect("/admin/balance/requests?success=Solicitud+rechazada")
      case Left(res) => res
    }
  }

  /** GET /admin/statistics */
  def statistics(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val projectDir = System.getProperty("user.dir")
        val path = Paths.get(projectDir, "app", "views", "admin_statistics.html")
        Try(Files.readString(path)) match {
          case Success(template) =>
            val replacements = Map(
              "__STAT_TOTAL_SALES__" -> AnalyticsService.totalUnitsSold.toString,
              "__STAT_TOTAL_REVENUE__" -> formatMoney(AnalyticsService.totalRevenue),
              "__STAT_TOTAL_USERS__" -> UserRepo.all.size.toString,
              "__STAT_TOTAL_PRODUCTS__" -> MediaRepo.all.size.toString
            )
            val filled = replacePlaceholders(template, replacements)
            val response = HttpResponse.ok(filled)
            if (request.cookies.contains("sessionId"))
              response.withCookie("sessionId", request.cookies("sessionId"), maxAge = Some(86400))
            else response
          case Failure(e) =>
            HttpResponse.notFound(s"No se pudo cargar estadísticas: ${e.getMessage}")
        }
      case Left(res) => res
    }
  }

  /** GET /api/promotions/stats - API JSON para estadísticas de promociones */
  def promotionsStats(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        import java.time.LocalDateTime
        val now = LocalDateTime.now()
        
        val allPromotions = Try(PromotionRepo.all).getOrElse(Seq.empty)
        
        val active = allPromotions.count { p =>
          !p.startDate.isAfter(now) && !p.endDate.isBefore(now)
        }
        val upcoming = allPromotions.count { p =>
          p.startDate.isAfter(now)
        }
        val inactive = allPromotions.count { p =>
          p.endDate.isBefore(now)
        }
        
        val json = s"""{
          "active": $active,
          "upcoming": $upcoming,
          "inactive": $inactive,
          "total": ${allPromotions.size}
        }"""
        
        HttpResponse(
          status = 200,
          statusText = "OK",
          headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
          body = json
        )
      case Left(res) => res
    }
  }

  /** GET /api/files/list - API JSON para listar archivos en public/images/ */
  def listFiles(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val imagesDir = new java.io.File("public/images")
        
        def listFilesRecursively(dir: java.io.File, basePath: String = ""): Seq[Map[String, String]] = {
          if (!dir.exists() || !dir.isDirectory) return Seq.empty
          
          val files = dir.listFiles()
          if (files == null) return Seq.empty
          
          files.flatMap { file =>
            val relativePath = if (basePath.isEmpty) file.getName else s"$basePath/${file.getName}"
            
            if (file.isDirectory) {
              // Agregar la carpeta
              Map("name" -> file.getName, "type" -> "folder", "path" -> relativePath) +: 
              listFilesRecursively(file, relativePath)
            } else {
              // Solo archivos de imagen
              val ext = file.getName.toLowerCase
              if (ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png") || 
                  ext.endsWith(".gif") || ext.endsWith(".webp")) {
                Seq(Map("name" -> file.getName, "type" -> "file", "path" -> relativePath))
              } else {
                Seq.empty
              }
            }
          }
        }
        
        val fileList = listFilesRecursively(imagesDir)
        val json = s"""{"files": ${fileList.map { f =>
          s"""{"name": "${escapeJson(f("name"))}", "type": "${f("type")}", "path": "${escapeJson(f("path"))}"}"""
        }.mkString("[", ",", "]")}}"""
        
        HttpResponse(
          status = 200,
          statusText = "OK",
          headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
          body = json
        )
      case Left(res) => res
    }
  }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: `<none>`.