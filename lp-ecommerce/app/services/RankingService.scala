package services

import models._
import java.time.LocalDateTime

case class RankingRow(referenceId: Long, value: BigDecimal, position: Int, previousPosition: Option[Int])

object RankingService {

  private def cutoffMonths(months: Long): LocalDateTime = LocalDateTime.now().minusMonths(months)

  def topUsers(limit: Int = 10): Vector[(Long, Int)] = {
    val downloads = DownloadRepo.all.filter(_.downloadDate.isAfter(cutoffMonths(6)))
    downloads
      .groupBy(_.userId)
      .view
      .mapValues(_.map(_.quantity).sum)
      .toVector
      .sortBy(-_._2)
      .take(limit)
  }

  def topProducts(limit: Int = 10): Vector[(Long, Int)] = {
    val downloads = DownloadRepo.all
    downloads
      .groupBy(_.mediaId)
      .view
      .mapValues(_.map(_.quantity).sum)
      .toVector
      .sortBy(-_._2)
      .take(limit)
  }

  def topRated(limit: Int = 10): Vector[(Long, Double)] = {
    val media = MediaRepo.all
    val ratings = media.map(m => (m.id, m.rating)).filter(_._2 > 0)
    ratings.sortBy(-_._2).take(limit)
  }

  private def buildRows[T](rankingType: RankingType, current: Vector[(Long, T)])(implicit numeric: Numeric[T]): Vector[RankingRow] = {
    val previous = RankingRepo.latestSnapshot(rankingType)
    val previousPositions = previous.map(_.entries.map(e => e.referenceId -> e.position).toMap).getOrElse(Map.empty[Long, Int])

    current.zipWithIndex.map { case ((referenceId, value), idx) =>
      RankingRow(
        referenceId = referenceId,
        value = BigDecimal(numeric.toDouble(value)),
        position = idx + 1,
        previousPosition = previousPositions.get(referenceId)
      )
    }
  }

  def generateSnapshots(): Unit = {
    RankingRepo.saveSnapshot(RankingType.TopDownloads, topUsers().zipWithIndex.map { case ((userId, _), idx) => RankingEntry(userId, idx + 1) })
    RankingRepo.saveSnapshot(RankingType.TopProducts, topProducts().zipWithIndex.map { case ((mediaId, _), idx) => RankingEntry(mediaId, idx + 1) })
    RankingRepo.saveSnapshot(RankingType.TopRated, topRated().zipWithIndex.map { case ((mediaId, _), idx) => RankingEntry(mediaId, idx + 1) })
  }

  def usersRankingRows(limit: Int = 10): Vector[RankingRow] =
    buildRows(RankingType.TopDownloads, topUsers(limit))(Numeric.IntIsIntegral)

  def productsRankingRows(limit: Int = 10): Vector[RankingRow] =
    buildRows(RankingType.TopProducts, topProducts(limit))(Numeric.IntIsIntegral)

  def ratedRankingRows(limit: Int = 10): Vector[RankingRow] =
    buildRows(RankingType.TopRated, topRated(limit))(Numeric.DoubleIsFractional)
}
