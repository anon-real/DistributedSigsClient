package services

import akka.actor.{ActorRef, ActorSystem, Props}
import dao.{SecretDAO, TransactionDAO}
import javax.inject._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.inject.ApplicationLifecycle
import slick.jdbc.JdbcProfile
import utils.{Explorer, Node, Server}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartupService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, appLifecycle: ApplicationLifecycle,
                               system: ActorSystem, server: Server, transactionHandler: TransactionHandler, proofHandler: ProofHandler)
                              (implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  private val logger: Logger = Logger(this.getClass)

  logger.info("App started!")

  val jobs: ActorRef = system.actorOf(Props(new Jobs(transactionHandler, proofHandler, server)), "scheduler")
  system.scheduler.scheduleAtFixedRate(
    initialDelay = 5.seconds,
    interval = 30.seconds,
    receiver = jobs,
    message = JobsUtil.handleApproved
  )

  appLifecycle.addStopHook { () =>
    logger.info("App stopped!")
    Future.successful(())
  }
}
