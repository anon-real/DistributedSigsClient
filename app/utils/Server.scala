package utils

import models.{Commitment, Member, Proof, Request, Team}
import play.api.Logger
import play.api.libs.json._
import scalaj.http.Http

object Server {
  private val logger: Logger = Logger(this.getClass)
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"))

  /**
   * gets list of teams the client is a member of
   */
  def getTeams: Seq[Team] = {
    val res = Http(s"${Conf.serverUrl}/team/${Conf.pk}").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    Json.parse(res.body).as[List[JsValue]].map(team => {
      val memberId = (team \\ "memberId").head.as[Long]
      val pending = (team \\ "pendingNum").head.as[Int]
      Team((team \\ "team").head, memberId, pending)
    })
  }

  /**
   * gets list of proposals relating a team
   */
  def getProposals(teamId: Long): (Team, Seq[Request]) = {
    val res = Http(s"${Conf.serverUrl}/proposal/$teamId/${Conf.pk}").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    val memberId = (js \\ "memberId").head.as[Long]
    val team = Team((js \\ "team").head, memberId)
    val proposals = (js \\ "proposals").head.as[List[JsValue]].map(req => Request(req))
    (team, proposals)
  }

  /**
   * gets list of approved proposals relating a team
   */
  def getApprovedProposals(teamId: Long): Seq[Request] = {
    val res = Http(s"${Conf.serverUrl}/proposal/approved/$teamId").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[List[JsValue]].map(req => Request(req))
  }

  def getUnsignedTx(reqId: Long): (Boolean, String) = {
    val res = Http(s"${Conf.serverUrl}/proposal/tx/unsigned/$reqId").headers(defaultHeader).asString
    if (res.code == 404) return (false, "")
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    (true, (js \ "tx").get.toString())
  }

  def getProofs(reqId: Long): Seq[Proof] = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/proofs").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(proof => Proof(proof))
  }

  def getMembers(teamId: Long): Seq[Member] = {
    val res = Http(s"${Conf.serverUrl}/team/$teamId/members").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(member => Member(member))
  }

  def getCommitments(reqId: Long): Seq[Commitment] = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/commitments").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(cmnt => Commitment(cmnt))
  }

  /**
   * sends approval request to the server for a proposal
   * @return a boolean specifying whether the operation was successful
   */
  def approveProposal(reqId: Long, memberId: Long, a: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/commitment").postData(
      s"""{
        |  "a": "$a",
        |  "memberId": $memberId
        |}""".stripMargin).headers(defaultHeader).asString
    !res.isError
  }

  def proposalDecision(id: Long, decision: Boolean): (Boolean, String) = {
    val res = Http(s"${Conf.serverUrl}/proposal/$id/decide").postData(
      s"""{
         |  "decision": $decision
         |}""".stripMargin).headers(defaultHeader).asString
    val js = Json.parse(res.body)
    if (res.isError) (false, (js \ "message").as[String])
    else (true, "")
  }

  def setTx(reqId: Long, isUnsigned: Boolean, tx: String): Boolean = {
    logger.debug(s"setting tx. isUnsigned: $isUnsigned, body: $tx")
    val res = Http(s"${Conf.serverUrl}/proposal/tx/$reqId").postData(
      s"""{
         |  "isUnsigned": $isUnsigned,
         |  "tx": $tx
         |}""".stripMargin).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"setting tx failed ${res.body}")
      false
    } else true
  }

  def setProof(reqId: Long, isSimulated: Boolean, memberId: Long, proof: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/proof").postData(
      s"""{
         |  "simulated": $isSimulated,
         |  "memberId": $memberId,
         |  "proof": $proof
         |}""".stripMargin).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"setting proof failed ${res.body}")
      false
    } else true
  }

  def setProposalPaid(reqId: Long, txId: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/paid").postData(
      s"""{
         |  "txId": "$txId"
         |}""".stripMargin).headers(defaultHeader).asString
    res.isSuccess
  }
}
