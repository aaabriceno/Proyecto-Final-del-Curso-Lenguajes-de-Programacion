package controllers

import http.{HttpRequest, HttpResponse}
import models.{UserRepo, MediaRepo}
import services.{RankingService, RankingRow}

object RankingController {

  private def toJsonArray(rows: Vector[Map[String, Any]]): String =
    rows.map { item =>
      val body = item.map { case (k, v) =>
        val value = v match {
          case s: String => s"\"${s.replace("\"", "\\\"")}\""
          case b: Boolean => b.toString
          case n: Int => n.toString
          case n: Long => n.toString
          case n: Double => f"$n%.2f"
          case other => s"\"${other.toString.replace("\"", "\\\"")}\""
        }
        s"\"$k\":$value"
      }.mkString(",")
      s"{$body}"
    }.mkString("[", ",", "]")

  def generateSnapshots(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        RankingService.generateSnapshots()
        HttpResponse.json(200, Map("success" -> true))
      case Left(resp) => resp
    }
  }

  def topUsers(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val rankingRows: Vector[RankingRow] = RankingService.usersRankingRows()
        val rows: Vector[Map[String, Any]] = rankingRows.map { row =>
          val user = UserRepo.findById(row.referenceId)
          Map(
            "id" -> row.referenceId,
            "username" -> user.map(_.name).getOrElse(s"Usuario ${row.referenceId}"),
            "estado_cuenta" -> (if (user.exists(_.isActive)) "activo" else "inactivo"),
            "total_descargas" -> row.value.toInt,
            "posicion" -> row.position,
            "posicion_anterior" -> row.previousPosition.getOrElse(0)
          )
        }
        HttpResponse.json(toJsonArray(rows))
      case Left(resp) => resp
    }
  }

  def topProducts(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val rankingRows: Vector[RankingRow] = RankingService.productsRankingRows()
        val rows: Vector[Map[String, Any]] = rankingRows.map { row =>
          val media = MediaRepo.find(row.referenceId)
          Map(
            "id" -> row.referenceId,
            "title" -> media.map(_.title).getOrElse(s"Producto ${row.referenceId}"),
            "downloads" -> row.value.toInt,
            "posicion" -> row.position,
            "posicion_anterior" -> row.previousPosition.getOrElse(0)
          )
        }
        HttpResponse.json(toJsonArray(rows))
      case Left(resp) => resp
    }
  }

  def topRated(request: HttpRequest): HttpResponse = {
    AuthController.requireAdmin(request) match {
      case Right(_) =>
        val rankingRows: Vector[RankingRow] = RankingService.ratedRankingRows()
        val rows: Vector[Map[String, Any]] = rankingRows.map { row =>
          val media = MediaRepo.find(row.referenceId)
          Map(
            "id" -> row.referenceId,
            "title" -> media.map(_.title).getOrElse(s"Producto ${row.referenceId}"),
            "rating" -> row.value,
            "posicion" -> row.position,
            "posicion_anterior" -> row.previousPosition.getOrElse(0)
          )
        }
        HttpResponse.json(toJsonArray(rows))
      case Left(resp) => resp
    }
  }
}
