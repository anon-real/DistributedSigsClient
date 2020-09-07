package services

import java.io.File

import akka.actor.Props
import dao.{SecretDAO, TransactionDAO}
import models.{Commitment, Member, Proof, Request, RequestStatus, Secret, Team, Transaction}
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentMatchers.eq
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
import utils.{Conf, Explorer, Node, Server}

import scala.io.Source.fromFile
import org.mockito._

class JobsSpec extends PlaySpec with BeforeAndAfterEach with BeforeAndAfterAll with MockitoSugar {
  private lazy val appBuilder = new GuiceApplicationBuilder()
  private implicit lazy val app = appBuilder.build()
  private lazy val injector: Injector = appBuilder.injector()
  private lazy val databaseApi: DBApi = injector.instanceOf[DBApi]

  private val teams = Seq(Team("", "", "", "", "", 0, 1, 1), Team("", "", "", "", "", 0, 2, 2))
  private val proposals = Seq(Request("", 1e6, "", "someAddr", 1, RequestStatus.approved, Seq(), 1))
  private val members = Seq(Member(Conf.pk, 1, "", 1), Member("2", 2, "", 2), Member("", 3, "", 3))

  def transactionDAO(implicit app: Application): TransactionDAO = Application.instanceCache[TransactionDAO].apply(app)

  def secretDAO(implicit app: Application): SecretDAO = Application.instanceCache[SecretDAO].apply(app)

  private val explorer = mock[Explorer]
  private val server = mock[Server]
  private val node = mock[Node](withSettings().useConstructor(explorer))

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
    Mockito.ignoreStubs(explorer)
    Mockito.ignoreStubs(node)
    Mockito.ignoreStubs(server)
    Mockito.reset(explorer)
    Mockito.reset(node)
    Mockito.reset(server)

    when(server.getTeams).thenReturn(teams)
    when(server.getApprovedProposals(2)).thenReturn(Seq())
    when(server.getApprovedProposals(1)).thenReturn(proposals)
    secretDAO.deleteAll()
    transactionDAO.deleteAll()

    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
  }

  "proof generation method" must {
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

    "simulate when there are no proofs" in {
      when(server.getUnsignedTx(1)).thenReturn((true, "{}"))
      when(server.getProofs(1)).thenReturn(Seq())
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      when(node.extractHints("{partial}", Seq(Conf.pk), Seq(""))).thenReturn((true, "hints"))
      val ourSecret = Secret(Conf.pk, "r", 1)
      when(node.signTx(ArgumentMatchers.eq("{}"), ArgumentMatchers.eq(ourSecret), any())).thenReturn((true, "{partial}"))
      secretDAO.insert(ourSecret)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(any())
      verify(server, times(1)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(1)).setProof(any(), ArgumentMatchers.eq(true), any(), any())
      verify(node, times(1)).signTx(ArgumentMatchers.eq("{}"), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(1)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(0)).getTxConfirmationNum(any())

    }

    "generate our part of proof is already simulated" in {
      when(server.getUnsignedTx(1)).thenReturn((true, "{}"))
      when(server.getProofs(1)).thenReturn(Seq(Proof(2, 1, "[{}, {}]", simulated = true)))
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      when(node.extractHints("{partial}", Seq(Conf.pk), Seq())).thenReturn((true, "hints"))
      val ourSecret = Secret(Conf.pk, "r", 1)
      when(node.signTx(ArgumentMatchers.eq("{}"), ArgumentMatchers.eq(ourSecret), any())).thenReturn((true, "{partial}"))
      secretDAO.insert(ourSecret)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(1)
      verify(server, times(1)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(1)).setProof(any(), ArgumentMatchers.eq(false), any(), any())
      verify(node, times(1)).signTx(ArgumentMatchers.eq("{}"), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(1)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(0)).getTxConfirmationNum(any())

    }

    "do nothing if already has generated its proof" in {
      when(server.getUnsignedTx(1)).thenReturn((true, "{}"))
      when(server.getProofs(1)).thenReturn(Seq(Proof(2, 1, s"""{}, {"${Conf.pk}"}""", simulated = true)))
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      val ourSecret = Secret(Conf.pk, "r", 1)
      secretDAO.insert(ourSecret)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(1)
      verify(server, times(1)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(0)).setProof(any(), ArgumentMatchers.eq(false), any(), any())
      verify(node, times(0)).signTx(ArgumentMatchers.eq("{}"), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(0)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(0)).getTxConfirmationNum(any())
    }

    "assemble tx if all proofs are gathered" in {
      when(server.getUnsignedTx(1)).thenReturn((true, """{"id": "someId"}"""))
      when(server.getProofs(1)).thenReturn(Seq(Proof(2, 1, s"""[{}, {}]""", simulated = true), Proof(1, 1, s"""[{"proof": "${Conf.pk}"}]""", simulated = false)))
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      val ourSecret = Secret(Conf.pk, "r", 1)
      secretDAO.insert(ourSecret)
      when(node.signTx(ArgumentMatchers.eq("""{"id": "someId"}"""), ArgumentMatchers.eq(ourSecret), any())).thenReturn((true, """{"id": "someId"}"""))
      when(node.isTxOk(any())).thenReturn(true)
      when(explorer.getTxConfirmationNum("someId")).thenReturn(-1)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(1)
      verify(server, times(1)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(0)).setProof(any(), ArgumentMatchers.eq(false), any(), any())
      verify(node, times(1)).signTx(ArgumentMatchers.eq("""{"id": "someId"}"""), any(), any())
      verify(node, times(1)).isTxOk(any())
      verify(node, times(1)).broadcastTx(any())
      verify(node, times(0)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(1)).getTxConfirmationNum(any())
      transactionDAO.exists(1) mustBe true

    }

    "wait for enough confirmation for tx" in {
      when(server.getUnsignedTx(1)).thenReturn((true, """{"id": "someId"}"""))
      when(server.getProofs(1)).thenReturn(Seq(Proof(2, 1, s"""[{}, {}]""", simulated = true), Proof(1, 1, s"""[{"proof": "${Conf.pk}"}]""", simulated = false)))
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      val ourSecret = Secret(Conf.pk, "r", 1)
      secretDAO.insert(ourSecret)
      transactionDAO.insert(Transaction(1, """{"id": "someId"}""".getBytes("utf-16")))
      when(explorer.getTxConfirmationNum("someId")).thenReturn(1)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(1)
      verify(server, times(1)).getProofs(any())
      verify(server, times(0)).setProposalPaid(any(), any())
      verify(server, times(0)).setProof(any(), ArgumentMatchers.eq(false), any(), any())
      verify(node, times(0)).signTx(ArgumentMatchers.eq("""{"id": "someId"}"""), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(0)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(1)).getTxConfirmationNum(any())
      transactionDAO.exists(1) mustBe true

    }

    "inform server when tx is mined" in {
      when(server.getUnsignedTx(1)).thenReturn((true, """{"id": "someId"}"""))
      when(server.getProofs(1)).thenReturn(Seq(Proof(2, 1, s"""[{}, {}]""", simulated = true), Proof(1, 1, s"""[{"proof": "${Conf.pk}"}]""", simulated = false)))
      when(server.getCommitments(1)).thenReturn(members.map(mem => Commitment(mem, mem.pk, 1, mem.id)))
      when(server.getMembers(1)).thenReturn(members)
      val ourSecret = Secret(Conf.pk, "r", 1)
      secretDAO.insert(ourSecret)
      transactionDAO.insert(Transaction(1, """{"id": "someId"}""".getBytes("utf-16")))
      when(explorer.getTxConfirmationNum("someId")).thenReturn(5)

      proofHandler.handleProof(teams)

      verify(server, times(1)).getCommitments(1)
      verify(server, times(1)).getProofs(any())
      verify(server, times(1)).setProposalPaid(1, "someId")
      verify(server, times(0)).setProof(any(), ArgumentMatchers.eq(false), any(), any())
      verify(node, times(0)).signTx(ArgumentMatchers.eq("""{"id": "someId"}"""), any(), any())
      verify(node, times(0)).isTxOk(any())
      verify(node, times(0)).broadcastTx(any())
      verify(node, times(0)).extractHints(ArgumentMatchers.eq("{partial}"), any(), any())
      verify(explorer, times(1)).getTxConfirmationNum(any())
      transactionDAO.exists(1) mustBe true

    }
  }

  "tx generation method" must {
    "do nothing if tx is already generated" in {
      transactionHandler.handleTxGeneration(teams)
      verify(node, times(0)).generateUnsignedTx(any(), any(), any(), any(), any())
    }

    "generate tx when not exists" in {
      val proposals = Seq(Request("", 1, "", "someAddr", 1, RequestStatus.approved, Seq(), 1))
      when(server.getApprovedProposals(1)).thenReturn(proposals)
      when(server.getUnsignedTx(1)).thenReturn((false, ""))
      when(node.generateUnsignedTx(any(), any(), any(), any(), any())).thenReturn((true, "{}"))

      transactionHandler.handleTxGeneration(teams)

      verify(node, times(1)).generateUnsignedTx("", 1e9.toLong, "someAddr", "", 1)
      verify(server, times(1)).setTx(1, "{}")
    }
  }
}
