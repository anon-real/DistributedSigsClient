package utils

import javax.inject.Singleton
import models.{Commitment, Member, Proof, Request, Team}
import play.api.Logger
import play.api.libs.json._
import scalaj.http.Http

@Singleton
class Server {
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
   * @param teamId team id
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
   * @param teamId team id
   * gets list of approved proposals relating a team
   */
  def getApprovedProposals(teamId: Long): Seq[Request] = {
    val res = Http(s"${Conf.serverUrl}/proposal/approved/$teamId").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[List[JsValue]].map(req => Request(req))
  }

  /**
   * gets unsinged tx related to a proposal from server if already generated
   * @param reqId proposal id
   * @return (success, tx) in case that tx is not generated yet, success is false
   */
  def getUnsignedTx(reqId: Long): (Boolean, String) = {
    val res = Http(s"${Conf.serverUrl}/proposal/tx/unsigned/$reqId").headers(defaultHeader).asString
    if (res.code == 404) return (false, "")
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    (true, (js \ "tx").get.toString())
  }

  /**
   * gets partial proofs related to a proposal
   * @param reqId proposal id
   * @return proofs
   */
  def getProofs(reqId: Long): Seq[Proof] = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/proofs").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(proof => Proof(proof))
  }

  /**
   * gets members of a team
   * @param teamId team id
   * @return list of members
   */
  def getMembers(teamId: Long): Seq[Member] = {
    val res = Http(s"${Conf.serverUrl}/team/$teamId/members").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(member => Member(member))
  }

  /**
   * gets commitments associated with the proposal
   * @param reqId proposal id
   * @return list of commitments
   */
  def getCommitments(reqId: Long): Seq[Commitment] = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/commitments").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(cmnt => Commitment(cmnt))
  }


  /**
   * sends approval request to the server for a proposal
   * @param reqId proposal id
   * @param memberId member id of us in this team
   * @param a 'a' in the commitment, empty in case of rejection
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

  /**
   * sets final decision about a proposal
   * @param id proposal id
   * @param decision approve or reject
   * @return whether operation was successful or not with the server's message
   */
  def proposalDecision(id: Long, decision: Boolean): (Boolean, String) = {
    val res = Http(s"${Conf.serverUrl}/proposal/$id/decide").postData(
      s"""{
         |  "decision": $decision
         |}""".stripMargin).headers(defaultHeader).asString
    val js = Json.parse(res.body)
    if (res.isError) (false, (js \ "message").as[String])
    else (true, "")
  }

  /**
   * sets unsigned tx for a proposal
   * @param reqId proposal id
   * @param tx unsigned tx
   * @return
   */
  def setTx(reqId: Long, tx: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/proposal/tx/$reqId").postData(
      s"""{
         |  "tx": $tx
         |}""".stripMargin).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"setting tx failed ${res.body}")
      false
    } else true
  }

  /**
   * sets out partial proof for a proposal (or simulations)
   * @param reqId proposal id
   * @param isSimulated whether contains simulations too
   * @param memberId our member id in this team
   * @param proof proof
   * @return result
   */
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

  /**
   * sets proposal status as paid and also sets tx id for the proposal which must be confirmed
   * @param reqId proposal id
   * @param txId tx id
   * @return result
   */
  def setProposalPaid(reqId: Long, txId: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/proposal/$reqId/paid").postData(
      s"""{
         |  "txId": "$txId"
         |}""".stripMargin).headers(defaultHeader).asString
    res.isSuccess
  }
}
