package controllers

import http.{HttpRequest, HttpResponse}
import models.{RatingRepo, DownloadRepo, MediaRepo}
import scala.util.Try

object RatingController {

  private def parseScore(data: Map[String, Any]): Option[Int] =
    data.get("score").orElse(data.get("nota")) match {
      case Some(value: String) => value.toIntOption
      case Some(value: Int)    => Some(value)
      case Some(value)         => Try(value.toString.toInt).toOption
      case None                => None
    }

  private def parseMediaId(data: Map[String, Any], fallback: Option[Long] = None): Option[Long] =
    data.get("id").orElse(data.get("mediaId")).flatMap {
      case s: String => s.toLongOption
      case n: Int    => Some(n.toLong)
      case other     => Try(other.toString.toLong).toOption
    }.orElse(fallback)

  def rate(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val payload = request.jsonBody.getOrElse(Map.empty)
        val mediaId = parseMediaId(payload).orElse(request.formData.get("mediaId").flatMap(_.toLongOption))
        val score = parseScore(payload).orElse(request.formData.get("score").flatMap(_.toIntOption))

        (mediaId, score) match {
          case (Some(mid), Some(value)) =>
            if (!DownloadRepo.userHasDownloaded(user.id, mid)) {
              return HttpResponse.json(403, Map("error" -> "Solo puedes calificar contenidos que ya descargaste"))
            }
            try {
              RatingRepo.save(user.id, mid, value)
              HttpResponse.json(200, Map("success" -> true))
            } catch {
              case e: IllegalArgumentException =>
                HttpResponse.json(400, Map("success" -> false, "error" -> e.getMessage))
            }
          case _ =>
            HttpResponse.json(400, Map("success" -> false, "error" -> "Parametros incompletos"))
        }
      case Left(resp) => resp
    }
  }

  def mediaStats(mediaId: Long, request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(_) =>
        val ratings = RatingRepo.findByMedia(mediaId)
        val average = if (ratings.nonEmpty) ratings.map(_.score).sum.toDouble / ratings.size else 0.0
        HttpResponse.json(200, Map(
          "mediaId" -> mediaId,
          "average" -> average,
          "count" -> ratings.size
        ))
      case Left(resp) => resp
    }
  }

  def userRatings(request: HttpRequest): HttpResponse = {
    AuthController.requireAuth(request) match {
      case Right(user) =>
        val ratings = RatingRepo.findByUser(user.id)
        val rows = if (ratings.isEmpty) {
          "<p class='text-muted'>Aún no calificaste contenidos.</p>"
        } else {
          ratings.map { rating =>
            val mediaTitle = MediaRepo.find(rating.mediaId).map(_.title).getOrElse(s"ID ${rating.mediaId}")
            s"""
              |<div class="list-group-item bg-dark text-light border-secondary">
              |  <div class="d-flex justify-content-between">
              |    <strong>%s</strong>
              |    <span>Nota: %d</span>
              |  </div>
              |  <small class="text-muted">Actualizado: %s</small>
              |</div>
            """.stripMargin.format(mediaTitle, rating.score, rating.updatedAt)
          }.mkString("\n")
        }

        val html = s"""
          |<!DOCTYPE html>
          |<html lang="es">
          |<head>
          |  <meta charset="UTF-8">
          |  <title>Mis calificaciones</title>
          |  <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css">
          |</head>
          |<body class="bg-dark text-light">
          |  <div class="container py-4">
          |    <h1 class="mb-4">⭐ Mis calificaciones</h1>
          |    <div class="list-group">
          |      %s
          |    </div>
          |    <a href="/user/account" class="btn btn-outline-light mt-4">Volver a mi cuenta</a>
          |  </div>
          |</body>
          |</html>
        """.stripMargin.format(rows)

        HttpResponse.ok(html)
      case Left(resp) => resp
    }
  }
}
