package services

import javax.inject.Inject
import models.Team
import play.api.Logger
import utils.{Node, Server}

class TransactionHandler @Inject()(node: Node, server: Server) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * handles unsigned transaction generation for proposals
   */
  def handleTxGeneration(reqId: Long): (Boolean, String) = {
    logger.info("handling tx generation...")
    val (team, prop) = server.getProposalById(reqId)
    val (created, prev) = server.getUnsignedTx(prop.id)
    if (!created) {
      logger.info(s"we create unsigned tx for proposal: ${prop.id}")
      var ergAmount = 1000000L // default nano ergs for token requests
      if (team.tokenId.isEmpty) ergAmount = (prop.amount * 1e9).toLong
      val (ok, tx) = node.generateUnsignedTx(team.address, ergAmount, prop.address, team.tokenId, prop.amount.toLong)
      if (!ok) {
        logger.error(s"could not generate unsigned tx for proposal: ${prop.id}")
        return (false, "")
      }
      else {
        return (server.setTx(prop.id, tx), tx)
      }
    }
    logger.info("handling tx generation done")
    (true, prev)
  }

}
