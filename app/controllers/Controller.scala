package controllers

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

  def proposals(teamId: Long) = Action { implicit request =>
    val res = Client.getProposals(teamId)
    Ok(views.html.request_list(res._2, res._1, Conf.pk))
  }
}
