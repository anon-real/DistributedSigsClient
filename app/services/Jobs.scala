package services

import akka.actor.{Actor, ActorLogging}
import dao.SecretDAO
import javax.inject.Inject
import models.Team
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import utils.{Conf, Explorer, Node, Server}

import scala.util.Try

object JobsUtil {
  val handleApproved = "handle approved"
}

class Jobs(secrets: SecretDAO) extends Actor with ActorLogging {
  private val logger: Logger = Logger(this.getClass)
  private var teams: Seq[Team] = Nil

  def receive = {
    case JobsUtil.handleApproved =>
      teams = Server.getTeams
      logger.info("Handling approved proposals...")
      Try(handleTxGeneration)
      Try(handleProof)
  }

  def handleProof: Unit = {
    try {
      logger.info("handling proof generation...")
      teams.foreach(team => {
        val proposals = Server.getApprovedProposals(team.id)
        proposals.filter(prop => secrets.exists(prop.id)).foreach(prop => {
          val (created, tx) = Server.getUnsignedTx(prop.id)
          if (!created) logger.info(s"unsigned tx for proposal ${prop.id} is not created yet.")
          else {
            val secret = secrets.byRequestId(prop.id)
            val cmnts = Server.getCommitments(prop.id).filter(_.a.nonEmpty).filterNot(_.a.equals(secret.a))
            val proofs = Server.getProofs(prop.id)
            if (proofs.size == cmnts.size + 1) {
              logger.info(s"all proofs have been gathered for proposal ${prop.id}, we will assemble the tx")
              val fProofs: Seq[String] = proofs.map(proof => {
                Json.parse(proof.proof).as[Seq[JsValue]].map(_.toString()).mkString(",")
              })
              val (ok, signed) = Node.signTx(tx, secret, fProofs)
              if (ok) {
                if (prop.id != 1 && prop.id != 33 && prop.id != 34) logger.error(signed)
              } else logger.error(s"could not assemble tx for proposal ${prop.id}")

            } else {
              if (proofs.exists(proof => proof.proof.contains(Conf.pk))) {
                logger.info(s"we have already generated our proof for proposal ${prop.id}, waiting for others...")
              } else {
                val fProofs: Seq[String] = proofs.filter(_.simulated).map(proof => {
                  Json.parse(proof.proof).as[Seq[JsValue]].map(_.toString()).mkString(",")
                }) ++ cmnts.map(cmt => {
                  s"""{
                     |  "hint": "cmtReal",
                     |  "type": "dlog",
                     |  "pubkey":{
                     |     "op": -51,
                     |     "h": "${cmt.member.pk}"
                     |  },
                     |  "a": "${cmt.a}"
                     |}""".stripMargin
                })
                val (ok, signed) = Node.signTx(tx, secret, fProofs)
                if (!ok) {
                  logger.error(s"could not sign tx for proposal ${prop.id}")
                } else {
                  if (proofs.isEmpty) {
                    logger.info(s"we will simulate for proposal ${prop.id}")
                    val simulated = Server.getMembers(team.id).map(_.pk).filterNot(mem => cmnts.map(_.member.pk).contains(mem))
                        .filterNot(_ == Conf.pk)
                    val (ok, hints) = Node.extractHints(signed, Seq(Conf.pk), simulated)
                    if (ok) {
                      if (Server.setProof(prop.id, isSimulated = true, team.memberId, hints))
                        logger.info("successfully simulated and sent to server")
                      else logger.error("could not sent simulated proofs to server")
                    }
                  } else {
                    logger.info(s"proposal ${prop.id} is already simulated, we will generate our part of proof now")
                    val (ok, hints) = Node.extractHints(signed, Seq(Conf.pk), Seq())
                    if (ok) {
                      if (Server.setProof(prop.id, isSimulated = false, team.memberId, hints))
                        logger.info("successfully sent proof to server")
                      else logger.error("could not sent proof to server")
                    }
                  }
                }
              }
            }
          }
        })
      })
      logger.info("handling proof generation done")
    } catch {
      case e: Throwable => e.printStackTrace()
    }

  }

  def handleTxGeneration: Unit = {
    try {
      logger.info("handling tx generation...")
      teams.foreach(team => {
        val proposals = Server.getApprovedProposals(team.id)
        proposals.foreach(prop => {
          val (created, _) = Server.getUnsignedTx(prop.id)
          if (!created) {
            logger.info(s"we create unsigned tx for proposal: ${prop.id}")
            val (ok, tx) = Node.generateUnsignedTx(team.address, (prop.amount * 1e9).toLong, prop.address)
            if (!ok) logger.error(s"could not generate unsigned tx for proposal: ${prop.id}")
            else Server.setTx(prop.id, isUnsigned = true, tx)
          }
        })
      })
      logger.info("handling tx generation done")
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }
}
