package controllers

import java.util.UUID

import akka.actor.ActorSystem
import javax.inject._
import play.api.mvc._
import utils.{Client, Conf, Util}

import scala.concurrent.ExecutionContext

@Singleton
class Controller @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  /**
   * Home! shows list of teams which user participate in!
   */
  def home = Action { implicit request =>
    Ok(views.html.team_list(Client.getTeams, Conf.pk))
  }

  def rejectProposal(reqId: Long) = Action(parse.json) { implicit request =>
    // TODO insert into db
    val memberId = (request.body \\ "memberId").head.as[Long]
    val serverRes = Client.approveProposal(reqId, memberId, "")
    if (serverRes) {
      Ok(
        s"""{
           |  "reload": true
           |}""".stripMargin).as("application/json")
    } else {
      BadRequest(
        s"""{
           |  "message": "Server returned error when trying to post commitment!"
           |}""".stripMargin).as("application/json")
    }
  }

  def approveProposal(reqId: Long) = Action(parse.json) { implicit request =>
    // TODO insert into db
    val memberId = (request.body \\ "memberId").head.as[Long]
    val a = UUID.randomUUID().toString; // TODO get from node
    val serverRes = Client.approveProposal(reqId, memberId, a)
    if (serverRes) {
      Ok(
        s"""{
           |  "reload": true
           |}""".stripMargin).as("application/json")
    } else {
      BadRequest(
        s"""{
           |  "message": "Server returned error when trying to post commitment!"
           |}""".stripMargin).as("application/json")
    }
  }

  def proposals(teamId: Long) = Action { implicit request =>
    val res = Client.getProposals(teamId)
    Ok(views.html.request_list(res._2, res._1, Conf.pk))
  }
}
