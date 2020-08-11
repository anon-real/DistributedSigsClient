package utils

import models.{Request, Team}
import play.api.libs.json._
import scalaj.http.Http

object Client {
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"))

  def getTeams: Seq[Team] = {
    val res = Http(s"${Conf.serverUrl}/getTeams/${Conf.pk}").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    Json.parse(res.body).as[List[JsValue]].map(team => Team(team))
  }

  def getProposals(teamId: Long): (Team, Seq[Request]) = {
    val res = Http(s"${Conf.serverUrl}/getProposals/$teamId").headers(defaultHeader).asString
    if (res.isError) throw new Throwable(s"Error getting info from server! $res")
    val js = Json.parse(res.body)
    val team = Team((js \\ "team").head)
    val proposals = (js \\ "proposals").head.as[List[JsValue]].map(req => Request(req))
    (team, proposals)
  }
}
