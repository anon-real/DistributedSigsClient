package controllers

import akka.actor.ActorSystem
import dao.SecretDAO
import javax.inject._
import models.Secret
import play.api.Logger
import play.api.mvc._
import utils.Util._
import utils.{Conf, Node, Server}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Controller @Inject()(secrets: SecretDAO, cc: ControllerComponents, actorSystem: ActorSystem,
                           node: Node, server: Server)(implicit exec: ExecutionContext) extends AbstractController(cc) {

  private val logger: Logger = Logger(this.getClass)

  /**
   * Home! shows list of teams which user participate in!
   */
  def home = Action { implicit request =>
    Ok(views.html.team_list(server.getTeams, Conf.pk))
  }

  /**
   * endpoint to reject a proposal.
   */
  def rejectProposal(reqId: Long) = Action(parse.json) { implicit request =>
    logger.info(s"rejecting proposal with id $reqId")

    val memberId = (request.body \\ "memberId").head.as[Long]
    val serverRes = server.approveProposal(reqId, memberId, "") // empty commitment means rejection!
    if (serverRes) {
      Ok(
        s"""{
           |  "reload": true
           |}""".stripMargin).as("application/json")
    } else {
      BadRequest(
        s"""{
           |  "message": "server returned error when trying to post commitment!"
           |}""".stripMargin).as("application/json")
    }
  }

  /**
   * endpoint to approve a proposal
   */
  def approveProposal(reqId: Long) = Action(parse.json).async { implicit request =>
    logger.info(s"approving proposal with id $reqId")
    try {
      val (a, r) = node.produceCommitment()
      logger.info(s"generated commitment $a for the proposal")
      val memberId = (request.body \\ "memberId").head.as[Long]
      val serverRes = server.approveProposal(reqId, memberId, a)
      if (serverRes) {
        secrets.insert(Secret(a, r, reqId)).map(_ => {
          logger.info("commitment sent to server successfully, also saved in local db with the secret.")
          Ok(
            s"""{
               |  "reload": true
               |}""".stripMargin).as("application/json")

        }).recover {
          case e: Exception => BadRequest(
            s"""{
               |  "message": "local db error: ${e.getMessage}"
               |}""".stripMargin).as("application/json")
        }
      } else {
        logger.info("server returned error response for the commitment!")
        Future {
          BadRequest(
            s"""{
               |  "message": "server returned error when trying to post commitment!"
               |}""".stripMargin).as("application/json")
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s"error while generating commitment! ${e.getMessage}")
        Future {
          BadRequest(
            s"""{
               |  "message": "node error: ${e.getMessage}"
               |}""".stripMargin).as("application/json")
        }
    }
  }

  /**
   * endpoint to make the final decision about the proposal
   */
  def proposalDecision(reqId: Long) = Action(parse.json) { implicit request =>
    val approved = (request.body \\ "decision").head.as[Boolean]
    val (res, msg) = server.proposalDecision(reqId, approved)
    if (res) {
      Ok(
        s"""{
           |  "reload": true
           |}""".stripMargin
      ).as("application/json")
    } else {
      BadRequest(
        s"""{
           |  "message": "$msg!"
           |}""".stripMargin
      ).as("application/json")
    }
  }

  /**
   * list of proposal related to a specific team
   */
  def proposals(teamId: Long) = Action { implicit request =>
    val res = server.getProposals(teamId)
    val props = res._2.sortBy(p => boolAsInt(p.isPending) + boolAsInt(p.pendingMe)).reverse
    Ok(views.html.request_list(props, res._1, Conf.pk))
  }
}
