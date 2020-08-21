package dao

import javax.inject.{Inject, Singleton}
import models.Transaction
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import scala.concurrent.duration._

import scala.concurrent.{Await, ExecutionContext, Future}

trait TransactionComponent { self: HasDatabaseConfigProvider[JdbcProfile] =>
  import profile.api._

  class TransactionTable(tag: Tag) extends Table[Transaction](tag, "TX") {
    def requestId = column[Long]("REQUEST_ID")
    def txBytes = column[Array[Byte]]("TX_BYTES")
    def * = (requestId, txBytes) <> (Transaction.tupled, Transaction.unapply)
  }
}

@Singleton()
class TransactionDAO @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends TransactionComponent
    with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  val transactions = TableQuery[TransactionTable]

  /**
   * inserts a tx into db
   * @param transaction transaction
   */
  def insert(transaction: Transaction): Future[Unit] = db.run(transactions += transaction).map(_ => ())

  /**
   * @param reqId proposal id
   * @return transaction associated with the proposal id
   */
  def byId(reqId: Long): Transaction = {
    val res = db.run(transactions.filter(tx => tx.requestId === reqId).result.head)
    Await.result(res, 5.second)
  }

  /**
   * @param reqId proposal id
   * @return whether tx exists for a specific proposal or not
   */
  def exists(reqId: Long): Boolean = {
    val res = db.run(transactions.filter(_.requestId === reqId).exists.result)
    Await.result(res, 5.second)
  }
}