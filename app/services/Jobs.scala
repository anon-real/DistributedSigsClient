package services

import akka.actor.{Actor, ActorLogging}
import dao.{SecretDAO, TransactionDAO}
import models.{Team, Transaction}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import utils.{Conf, Explorer, Node, Server}

import scala.util.Try

object JobsUtil {
  val handleApproved = "handle approved"
}

class Jobs(transactionHandler: TransactionHandler, proofHandler: ProofHandler, server: Server) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)
  private var teams: Seq[Team] = Nil

  def updateTeams(): Unit = {
    teams = server.getTeams
  }

  /**
   * periodically handles proof and tx generation for approved proposals
   */
  def receive = {
    case JobsUtil.handleApproved =>
      updateTeams()
      logger.info("Handling approved proposals...")
      Try(transactionHandler.handleTxGeneration(teams))
      Try(proofHandler.handleProof(teams))
  }


}

