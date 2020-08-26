package services

import akka.actor.{ActorSystem, Props}
import dao.{SecretDAO, TransactionDAO}
import javax.inject._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.inject.ApplicationLifecycle
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class StartupService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, appLifecycle: ApplicationLifecycle,
                               system: ActorSystem, secrets: SecretDAO, transactions: TransactionDAO)
                              (implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  private val logger: Logger = Logger(this.getClass)

  logger.info("App started!")

  val jobs = system.actorOf(Props(new Jobs(secrets, transactions)), "scheduler")
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
