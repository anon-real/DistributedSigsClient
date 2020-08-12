package dao

import javax.inject.{Inject, Singleton}
import models.{Commitment, Secret}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait SecretComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class SecretTable(tag: Tag) extends Table[Secret](tag, "SECRET") {
    def a = column[String]("A")
    def r = column[String]("R")
    def * = (a, r) <> (Secret.tupled, Secret.unapply)
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

  def byA(a: String): Future[Secret] = db.run(secrets.filter(_.a === a).result.head)
}