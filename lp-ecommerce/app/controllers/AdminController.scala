package controllers

import http.{HttpRequest, HttpResponse}
import models._
import scala.util.{Try, Success, Failure}
import java.nio.file.{Files, Paths}

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
    val allMedia = Try(MediaRepo.all).getOrElse(Seq.empty)
    
    // Generar JSON manualmente (sin librerías externas)
    val mediaJsonArray = allMedia.map { media =>
      s"""{
         |  "id": ${media.id},
         |  "title": "${escapeJson(media.title)}",
         |  "description": "${escapeJson(media.description)}",
         |  "mediaType": "${media.mtype.asString}",
         |  "price": ${media.price},
         |  "rating": ${media.rating},
         |  "downloadCount": ${media.downloads},
         |  "stock": ${media.stock}
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
        
        println(s"🔍 [CREATE MEDIA] Valores parseados:")
        println(s"  title = '$title'")
        println(s"  price = $price")
        println(s"  mediaType = ${data.get("mediaType")} → ${mtype.asString}")
        println(s"  stock = $stock")
        println(s"  url = '$url'")

        MediaRepo.add(title, desc, mtype, BigDecimal(price), catId, url, stock)
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
            // Cargar el HTML del formulario
            val projectDir = System.getProperty("user.dir")
            val path = s"$projectDir/app/views/admin_media_form.html"
            
            val html = Try(scala.io.Source.fromFile(path, "UTF-8").mkString) match {
              case Success(content) =>
                // Reemplazar los valores del formulario con los datos del producto
                content
                  .replace("➕ Nuevo Producto", "✏️ Editar Producto")
                  .replace("action=\"/admin/media\"", s"action=\"/admin/media/$id\"")
                  .replace("placeholder=\"Ej: Melodía Ambiental\"", s"value=\"${escapeHtml(media.title)}\" placeholder=\"Ej: Melodía Ambiental\"")
                  .replace("placeholder=\"Ej: 3.50\"", s"value=\"${media.price}\" placeholder=\"Ej: 3.50\"")
                  .replace("placeholder=\"Describe brevemente tu producto...\"", s">${escapeHtml(media.description)}</textarea>")
                  .replace("placeholder=\"Ej: /media/audio/song.mp3\"", s"value=\"${escapeHtml(media.assetPath)}\" placeholder=\"Ej: /media/audio/song.mp3\"")
                  .replace(s"""<option value="${media.mtype.asString}">""", s"""<option value="${media.mtype.asString}" selected>""")
                  .replace(s"""<option value="${media.categoryId.getOrElse("")}">""", s"""<option value="${media.categoryId.getOrElse("")}" selected>""")
                  .replace("value=\"100\"", s"value=\"${media.stock}\"")
                  .replace("🛒 Información del Producto", "✏️ Editar Información")
                  .replace("Guardar Producto", "Actualizar Producto")
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

        MediaRepo.update(id, title, desc, mtype, BigDecimal(price), catId, url, stock)
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
        
        // Generar JSON manualmente
        val categoriesJsonArray = allCategories.map { cat =>
          s"""{
             |  "id": ${cat.id},
             |  "name": "${escapeJson(cat.name)}",
             |  "parentId": ${cat.parentId.map(_.toString).getOrElse("null")},
             |  "description": "${escapeJson(cat.description)}"
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
      case Right(_) => serveHtml("admin_promotions", request)
      case Left(res) => res
    }
  }

  /** GET /admin/promotions/new */
  def newPromotionForm(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) => serveHtml("admin_promotion_form", request)
      case Left(res) => res
    }
  }

  /** POST /admin/promotions */
  def createPromotion(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val data = request.formData
        val name = data.getOrElse("name", "Promoción")
        val desc = data.getOrElse("description", "")
        val discount = data.get("discountPercent").flatMap(_.toIntOption).getOrElse(10)
        val target = PromotionTarget.from(data.getOrElse("targetType", "all"))
        val start = java.time.LocalDateTime.now()
        val end = start.plusMonths(1)

        PromotionRepo.create(name, desc, discount, start, end, target, Vector.empty)
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
      case Right(_) => serveHtml("admin_balance_requests", request)
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
}
