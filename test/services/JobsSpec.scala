package services

import java.io.File

import akka.actor.Props
import dao.{SecretDAO, TransactionDAO}
import models.{Request, RequestStatus, Team, Transaction}
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.Application
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import utils.{Explorer, Node, Server}

import scala.io.Source.fromFile
import org.mockito._

class JobsSpec extends PlaySpec with BeforeAndAfterEach with BeforeAndAfterAll with MockitoSugar {
  private lazy val appBuilder = new GuiceApplicationBuilder()
  private implicit lazy val app = appBuilder.build()
  private lazy val injector: Injector = appBuilder.injector()
  private lazy val databaseApi: DBApi = injector.instanceOf[DBApi]
  private val explorer = mock[Explorer]
  private val server = mock[Server]
  private val node = mock[Node](withSettings().useConstructor(explorer))

  private val teams = Seq(Team("", "", "", "", "", 0, 1, 1), Team("", "", "", "", "", 0, 2, 2))
  val proposals = Seq(Request("", 1e6, "", "someAddr", 1, RequestStatus.approved, Seq(), 1))

  def transactionDAO(implicit app: Application): TransactionDAO = Application.instanceCache[TransactionDAO].apply(app)

  def secretDAO(implicit app: Application): SecretDAO = Application.instanceCache[SecretDAO].apply(app)

  private val transactionHandler = new TransactionHandler(node, server)
  private val proofHandler = new ProofHandler(node, explorer, server, secretDAO, transactionDAO)

  override def beforeAll(): Unit = {
    Evolutions.applyEvolutions(databaseApi.database("default"))
  }

  override def afterAll(): Unit = {
    new File("db/test.mv.db").delete()
    new File("db/test.trace.db").delete()

  }

  override def beforeEach(): Unit = {
    when(server.getTeams).thenReturn(teams)
    when(server.getApprovedProposals(1)).thenReturn(Seq())
    when(server.getApprovedProposals(2)).thenReturn(proposals)

    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
  }

  "handleProof" must {
    "do nothing if there is no tx" in {
      when(server.getUnsignedTx(1)).thenReturn((false, ""))

      proofHandler.handleProof(teams)

      verify(server, times(0)).setTx(any(), any())
      verify(server, times(0)).getCommitments(any())
      verify(server, times(0)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(0)).setProof(any(), any(), any(), any())
      verify(node, times(0)).signTx(any(), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(0)).extractHints(any(), any(), any())
      verify(explorer, times(0)).getTxConfirmationNum(any())
      transactionDAO.exists(1) mustBe false
    }

  }

  "handleTxGeneration" must {
    "do nothing if tx is already generated" in {
      when(server.getUnsignedTx(1)).thenReturn((true, ""))
      transactionHandler.handleTxGeneration(teams)
      verify(node, times(0)).generateUnsignedTx(any(), any(), any(), any(), any())
    }

    "generate tx when not exists" in {
      val proposals = Seq(Request("", 1, "", "someAddr", 1, RequestStatus.approved, Seq(), 1))
      when(server.getApprovedProposals(2)).thenReturn(proposals)
      when(server.getUnsignedTx(1)).thenReturn((false, ""))
      when(node.generateUnsignedTx(any(), any(), any(), any(), any())).thenReturn((true, "{}"))

      transactionHandler.handleTxGeneration(teams)

      verify(node, times(1)).generateUnsignedTx("", 1e9.toLong, "someAddr", "", 1)
      verify(server, times(1)).setTx(1, "{}")
    }
  }

}
