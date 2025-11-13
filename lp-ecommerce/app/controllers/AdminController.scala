package controllers

import http.{HttpRequest, HttpResponse}
import models._
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
      case Right(_) => serveHtml("admin_dashboard", request)
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
             |  "id": ${user.id},
             |  "email": "${escapeJson(user.email)}",
             |  "username": "${escapeJson(user.name)}",
             |  "isAdmin": ${user.isAdmin},
             |  "isActive": ${user.isActive},
             |  "balance": ${user.balance},
             |  "totalSpent": ${user.totalSpent}
             |}""".stripMargin
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
    
    val allMedia = Try(MediaRepo.all).getOrElse(Seq.empty)
    val allPromotions = Try(PromotionRepo.all).getOrElse(Seq.empty)
    
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
         |  "id": ${media.id},
         |  "title": "${escapeJson(media.title)}",
         |  "description": "${escapeJson(media.description)}",
         |  "mediaType": "${media.mtype.asString}",
         |  "categoryId": ${media.categoryId.getOrElse("null")},
         |  "categoryPath": "$categoryPath",
         |  "assetPath": "${escapeJson(media.assetPath)}",
         |  "price": ${media.price},
         |  "rating": ${media.rating},
         |  "downloadCount": ${media.downloads},
         |  "stock": ${media.stock},
         |  "coverImage": "${media.getCoverImageUrl}",
         |  "hasPromotion": $hasPromotion,
         |  "discountPercent": $discountPercent,
         |  "discountedPrice": $discountedPrice
         |}""".stripMargin
    }.mkString(",\n")
    
    val json = s"""{"products": [\n$mediaJsonArray\n]}"""
    
    HttpResponse(
      status = 200,
      statusText = "OK",
      headers = Map("Content-Type" -> "application/json; charset=UTF-8"),
      body = json
    )
  }
  
  /** Escapa JSON para prevenir errores de sintaxis */
  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\")
     .replace("\"", "\\\"")
     .replace("\n", "\\n")
     .replace("\r", "\\r")
     .replace("\t", "\\t")

  /** GET /admin/media/new */
  def newMediaForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) => serveHtml("admin_media_form", request)
      case Left(res) => res
    }
  }

  /** POST /admin/media */
  def createMedia(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        
        // 🔍 DEBUG: Ver qué datos llegan del formulario
        println("🔍 [CREATE MEDIA] FormData completo:")
        data.foreach { case (key, value) => println(s"  $key = '$value'") }
        
        val title = data.getOrElse("title", "")
        val desc = data.getOrElse("description", "")
        val price = data.get("price").flatMap(_.toDoubleOption).getOrElse(0.0)
        val catId = data.get("categoryId").flatMap(_.toLongOption)
        val mtype = MediaType.from(data.getOrElse("mediaType", "video"))
        val url = data.getOrElse("url", "")
        val stock = data.get("stock").flatMap(_.toIntOption).getOrElse(0)
        val promotionId = data.get("promotionId").flatMap(_.toLongOption) // Opcional
        
        println(s"🔍 [CREATE MEDIA] Valores parseados:")
        println(s"  title = '$title'")
        println(s"  price = $price")
        println(s"  mediaType = ${data.get("mediaType")} → ${mtype.asString}")
        println(s"  stock = $stock")
        println(s"  url = '$url'")
        println(s"  promotionId = $promotionId")

        // MediaRepo.add(title, description, mtype, price, categoryId, assetPath, coverImage, stock, promotionId)
        MediaRepo.add(title, desc, mtype, BigDecimal(price), catId, url, None, stock, promotionId)
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
  
  /** Escapa HTML para prevenir XSS */
  private def escapeHtml(s: String): String =
    s.replace("&", "&amp;")
     .replace("<", "&lt;")
     .replace(">", "&gt;")
     .replace("\"", "&quot;")
     .replace("'", "&#x27;")

  /** POST /admin/media/:id */
  def updateMedia(id: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        val title = data.getOrElse("title", "")
        val desc = data.getOrElse("description", "")
        val price = data.get("price").flatMap(_.toDoubleOption).getOrElse(0.0)
        val catId = data.get("categoryId").flatMap(_.toLongOption)
        val mtype = MediaType.from(data.getOrElse("mediaType", "video"))
        val url = data.getOrElse("url", "")
        val stock = data.get("stock").flatMap(_.toIntOption).getOrElse(0)
        val promotionId = data.get("promotionId").flatMap(_.toLongOption) // Opcional

        // MediaRepo.update(id, title, description, mtype, price, categoryId, assetPath, coverImage, stock, promotionId)
        MediaRepo.update(id, title, desc, mtype, BigDecimal(price), catId, url, None, stock, promotionId)
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
      case Right(_) => serveHtml("admin_categories", request)
      case Left(res) => res
    }
  }

  /** GET /api/categories - API JSON para obtener todas las categorías */
  def categoriesJson(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val allCategories = Try(CategoryRepo.all).getOrElse(Seq.empty)
        
        // Generar JSON con breadcrumb (ruta completa) para cada categoría
        val categoriesJsonArray = allCategories.map { cat =>
          val breadcrumb = CategoryRepo.getBreadcrumb(cat.id)
          val path = breadcrumb.map(_.name).mkString(" > ")
          val level = breadcrumb.length - 1
          
          s"""{
             |  "id": ${cat.id},
             |  "name": "${escapeJson(cat.name)}",
             |  "parentId": ${cat.parentId.map(_.toString).getOrElse("null")},
             |  "description": "${escapeJson(cat.description)}",
             |  "path": "${escapeJson(path)}",
             |  "level": $level
             |}""".stripMargin
        }.mkString(",\n")
        
        val json = s"""{"categories": [\n$categoriesJsonArray\n]}"""
        
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
        val parent = data.get("parentId").flatMap(_.toLongOption)
        CategoryRepo.add(name, parent, desc)
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
        
        val html = s"""<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>LP Studios | Gestión de Promociones</title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.11.3/font/bootstrap-icons.min.css">
  <link rel="stylesheet" href="/assets/stylesheets/fearless.css">
</head>
<body class="bg-dark text-light">
  <nav class="navbar navbar-dark bg-dark border-bottom shadow-sm">
    <div class="container">
      <a class="navbar-brand fw-semibold" href="/">LP Studios</a>
      <div class="ms-auto d-flex gap-2">
        <a class="btn btn-outline-danger btn-sm" href="/admin">👨‍💼 Admin</a>
        <a class="btn btn-outline-light btn-sm" href="/logout"><i class="bi bi-box-arrow-right me-1"></i> Salir</a>
      </div>
    </div>
  </nav>
  <main class="container py-5">
    <div class="row mb-4 align-items-center">
      <div class="col">
        <h1 class="fw-bold">🔥 Gestión de Promociones</h1>
        <nav aria-label="breadcrumb">
          <ol class="breadcrumb">
            <li class="breadcrumb-item"><a href="/admin" class="text-white text-decoration-none">Dashboard</a></li>
            <li class="breadcrumb-item active text-light" aria-current="page">Promociones</li>
          </ol>
        </nav>
      </div>
      <div class="col-auto">
        <a href="/admin/promotions/new" class="btn btn-success">➕ Nueva Promoción</a>
      </div>
    </div>
    <div class="row mb-4">
      <div class="col-md-4">
        <div class="card bg-success text-white shadow-sm">
          <div class="card-body">
            <h6 class="text-uppercase mb-2">✅ Activas</h6>
            <h2 class="mb-0 fw-bold">${activePromotions.size}</h2>
            <small>En curso ahora mismo</small>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card bg-info text-white shadow-sm">
          <div class="card-body">
            <h6 class="text-uppercase mb-2">⏰ Próximas</h6>
            <h2 class="mb-0 fw-bold">${upcomingPromotions.size}</h2>
            <small>Aún no han comenzado</small>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <div class="card bg-secondary text-white shadow-sm">
          <div class="card-body">
            <h6 class="text-uppercase mb-2">❌ Inactivas</h6>
            <h2 class="mb-0 fw-bold">${inactivePromotions.size}</h2>
            <small>Ya finalizaron</small>
          </div>
        </div>
      </div>
    </div>
    <div class="card shadow-sm border-0">
      <div class="card-header bg-danger text-white d-flex justify-content-between align-items-center">
        <h5 class="mb-0">📢 Crear y administrar descuentos temporales</h5>
      </div>
      <div class="card-body p-0">
        <div class="table-responsive">
          <table class="table table-dark table-hover align-middle mb-0">
            <thead class="table-secondary text-dark">
              <tr>
                <th>Producto</th>
                <th>Descuento</th>
                <th>Inicio</th>
                <th>Fin</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              $promotionsRows
            </tbody>
          </table>
        </div>
      </div>
    </div>
    <div class="mt-4">
      <a href="/admin" class="btn btn-outline-light"><i class="bi bi-arrow-left me-2"></i> Volver al Dashboard</a>
    </div>
  </main>
  <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>"""
        
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
            s"""<option value="${media.id}">${escapeHtml(media.title)} ($$${media.price})</option>"""
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
      case Right(_) => serveHtml("admin_statistics", request)
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
}
