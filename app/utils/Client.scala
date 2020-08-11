package utils

import models.{Request, Team}
import play.api.libs.json._
import scalaj.http.Http

object Client {
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"))

  def getTeams: Seq[Team] = {
    val res = Http(s"${Conf.serverUrl}/getTeams/${Conf.pk}").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    Json.parse(res.body).as[List[JsValue]].map(team => {
      val memberId = (team \\ "memberId").head.as[Long]
      val pending = (team \\ "pendingNum").head.as[Int]
      Team((team \\ "team").head, memberId, pending)
    })
  }

  def getProposals(teamId: Long): (Team, Seq[Request]) = {
    val res = Http(s"${Conf.serverUrl}/getProposals/$teamId/${Conf.pk}").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    val memberId = (js \\ "memberId").head.as[Long]
    val team = Team((js \\ "team").head, memberId)
    val proposals = (js \\ "proposals").head.as[List[JsValue]].map(req => Request(req))
    (team, proposals)
  }

  def approveProposal(reqId: Long, memberId: Long, a: String): Boolean = {
    val res = Http(s"${Conf.serverUrl}/request/$reqId/newCommitment").postData(
      s"""{
        |  "a": "$a",
        |  "memberId": $memberId
        |}""".stripMargin).headers(defaultHeader).asString
    if (res.isError) false
    else true
  }

  def getMemberId(teamId: Long): Long = {
    2L
  }
}
