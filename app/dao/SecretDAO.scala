package dao

import javax.inject.{Inject, Singleton}
import models.Secret
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait SecretComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class SecretTable(tag: Tag) extends Table[Secret](tag, "SECRET") {
    def a = column[String]("A")
    def r = column[String]("R")
    def requestId = column[Long]("REQUEST_ID")
    def * = (a, r, requestId) <> (Secret.tupled, Secret.unapply)
  }
}

@Singleton()
class SecretDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends SecretComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val secrets = TableQuery[SecretTable]

  def insert(secret: Secret): Future[Unit] = {
    db.run(secrets += secret).map(_ => ())
  }

  def exists(reqId: Long): Boolean = {
    val res = db.run(secrets.filter(_.requestId === reqId).exists.result)
    Await.result(res, 5.second)
  }

  def byA(a: String): Secret = {
    val res = db.run(secrets.filter(_.a === a).result.head)
    Await.result(res, 5.second)
  }

  def byRequestId(reqId: Long): Secret = {
    val res = db.run(secrets.filter(_.requestId === reqId).result.head)
    Await.result(res, 5.second)
  }
}