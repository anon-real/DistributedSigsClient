package services

import javax.inject.Inject
import models.Team
import play.api.Logger
import utils.{Node, Server}

class TransactionHandler @Inject() (node: Node, server: Server) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * handles unsigned transaction generation for proposals
   */
  def handleTxGeneration(teams: Seq[Team]): Unit = {
    try {
      logger.info("handling tx generation...")
      teams.foreach(team => {
        val proposals = server.getApprovedProposals(team.id)
        proposals.foreach(prop => {
          val (created, _) = server.getUnsignedTx(prop.id)
          if (!created) {
            logger.info(s"we create unsigned tx for proposal: ${prop.id}")
            var ergAmount = 1000000L // default nano ergs for token requests
            if (team.tokenId.isEmpty) ergAmount = (prop.amount * 1e9).toLong
            val (ok, tx) = node.generateUnsignedTx(team.address, ergAmount, prop.address, team.tokenId, prop.amount.toLong)
            if (!ok) logger.error(s"could not generate unsigned tx for proposal: ${prop.id}")
            else server.setTx(prop.id, tx)
          }
        })
      })
      logger.info("handling tx generation done")
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }

}
