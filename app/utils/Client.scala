package utils

import models.{Request, Team}
import play.api.libs.json._
import scalaj.http.Http

object Client {
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

  /**
   * gets a new commitment from node!
   * @return (a, r)
   */
  def produceCommitment(): (String, String) = {
    val res = Http(s"${Conf.nodeUrl}/script/generateCommitment").postData(
      s"""{
        |  "op": -51,
        |  "h": "${Conf.pk}"
        |}""".stripMargin).headers(defaultHeader).asString
    val js = Json.parse(res.body)
    ((js \\ "a").head.as[String], (js \\ "r").head.as[String])
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
}
