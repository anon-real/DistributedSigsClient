package services

import dao.{SecretDAO, TransactionDAO}
import javax.inject.Inject
import models.{Team, Transaction}
import play.api.Logger
import play.api.libs.json.{JsValue, Json}
import utils.{Conf, Explorer, Node, Server}

class ProofHandler @Inject() (node: Node, explorer: Explorer, server: Server, secrets: SecretDAO, transactions: TransactionDAO) {
  private val logger: Logger = Logger(this.getClass)

  /**
   * handles proof generation for proposals
   */
  def handleProof(teams: Seq[Team]): Unit = {
    try {
      logger.info("handling proof generation...")
      teams.foreach(team => {
        val proposals = server.getApprovedProposals(team.id)
        proposals.filter(prop => secrets.exists(prop.id)).foreach(prop => {
          val (created, tx) = server.getUnsignedTx(prop.id)
          if (!created) logger.info(s"unsigned tx for proposal ${prop.id} is not created yet.")
          else {
            val secret = secrets.byRequestId(prop.id)
            val cmnts = server.getCommitments(prop.id).filter(_.a.nonEmpty).filterNot(_.a.equals(secret.a))
            val proofs = server.getProofs(prop.id)
            if (proofs.size == cmnts.size + 1) {

              logger.info(s"all proofs have been gathered for proposal ${prop.id}, we will assemble the tx")
              val fProofs: Seq[String] = proofs.map(proof => {
                Json.parse(proof.proof).as[Seq[JsValue]].map(_.toString()).mkString(",")
              })

              val (ok, signed) = {
                if (transactions.exists(prop.id)) (true, transactions.byId(prop.id).toString)
                else {
                  var (ok, signed) = node.signTx(tx, secret, fProofs)
                  if (ok && node.isTxOk(signed)) {
                    transactions.insert(Transaction(prop.id, signed.getBytes("utf-16")))
                  } else {
                    ok = false
                    logger.error(s"final assembled transaction is not valid for proposal ${prop.id}")
                  }
                  (ok, signed)
                }
              }
              if (ok) {
                val id = (Json.parse(signed) \ "id").as[String]
                val numConf = explorer.getTxConfirmationNum(id)
                if (numConf >= 3) {
                  logger.info("transaction is confirmed, will mark proposal as paid.")
                  server.setProposalPaid(prop.id, id)
                } else if (numConf == -1) {
                  logger.info(s"will broadcast transaction $id to be mined.")
                  node.broadcastTx(signed)
                } else {
                  logger.info(s"transaction $id is already mined, waiting for enough confirmations to inform server.")
                }
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
                val (ok, signed) = node.signTx(tx, secret, fProofs)
                if (!ok) {
                  logger.error(s"could not sign tx for proposal ${prop.id}")
                } else {
                  if (proofs.isEmpty) {
                    logger.info(s"we will simulate for proposal ${prop.id}")
                    val simulated = server.getMembers(team.id).map(_.pk).filterNot(mem => cmnts.map(_.member.pk).contains(mem))
                      .filterNot(_ == Conf.pk)
                    val (ok, hints) = node.extractHints(signed, Seq(Conf.pk), simulated)
                    if (ok) {
                      if (server.setProof(prop.id, isSimulated = true, team.memberId, hints))
                        logger.info("successfully simulated and sent to server")
                      else logger.error("could not sent simulated proofs to server")
                    }
                  } else {
                    logger.info(s"proposal ${prop.id} is already simulated, we will generate our part of proof now")
                    val (ok, hints) = node.extractHints(signed, Seq(Conf.pk), Seq())
                    if (ok) {
                      if (server.setProof(prop.id, isSimulated = false, team.memberId, hints))
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
        logger.error(e.getMessage)
    }

  }

}
