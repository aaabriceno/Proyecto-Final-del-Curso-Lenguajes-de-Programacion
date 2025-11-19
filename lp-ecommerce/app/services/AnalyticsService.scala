package services

import models._

object AnalyticsService {

  private def purchaseTransactions: Vector[Transaction] =
    TransactionRepo.all.filter(_.transactionType == TransactionType.Purchase)

  def totalUnitsSold: Int =
    purchaseTransactions.map(_.quantity).sum

  def totalRevenue: BigDecimal =
    purchaseTransactions.map(_.netAmount).sum

  def totalOrdersCount: Int = {
    val persisted = OrderRepo.countAll
    val legacy = purchaseTransactions.count(_.orderId.isEmpty)
    persisted + legacy
  }

  def topPurchasedMedia(limit: Int = 5): Vector[(Media, Int, BigDecimal)] = {
    val aggregated = purchaseTransactions
      .groupBy(_.mediaId)
      .collect {
        case (Some(mediaId), txs) =>
          val quantity = txs.map(_.quantity).sum
          val revenue = txs.map(_.netAmount).sum
          (mediaId, quantity, revenue)
      }
      .toVector
      .sortBy(-_._2)
      .take(limit)

    aggregated.flatMap { case (mediaId, qty, revenue) =>
      MediaRepo.find(mediaId).map(media => (media, qty, revenue))
    }
  }
}
