package utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlPathEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec

import scala.io.Source.fromFile

class ServerSpec extends PlaySpec with BeforeAndAfterEach {
  private val mockPort = 9090
  private val mockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(mockPort))

  override def beforeEach(): Unit = {
    super.beforeEach()
    mockServer.start()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    mockServer.stop()
  }

  "explorer" must {
    "get teams of the member" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/team/${Conf.pk}"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/member_teams.json").mkString)
            .withStatus(200)))
      val res = Server.getTeams
      res.length mustBe 2
    }

    "get team's proposals" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/1/${Conf.pk}"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/team_proposals.json").mkString)
            .withStatus(200)))
      val res = Server.getProposals(1)
      res._1.id mustBe 1
      res._2.length mustBe 2
    }

    "get team's approved proposals" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/approved/1"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/team_approved_proposals.json").mkString)
            .withStatus(200)))
      val res = Server.getApprovedProposals(1)
      res.length mustBe 1
    }

    "get proposal's unsigned tx" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/tx/unsigned/1"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/proposal_unsigned_tx.json").mkString)
            .withStatus(200)))
      val res = Server.getUnsignedTx(1)
      res._1 mustBe true
      res._2.length must be > 1000
    }

    "return false for not existing tx" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/tx/unsigned/1"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(404)))
      val res = Server.getUnsignedTx(1)
      res._1 mustBe false
    }

    "get proposal's proofs" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/4/proofs"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/proposal_proofs.json").mkString)
            .withStatus(200)))
      val res = Server.getProofs(4)
      res.length mustBe 3
    }

    "get team's members" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/team/3/members"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/team_members.json").mkString)
            .withStatus(200)))
      val res = Server.getMembers(3)
      res.length mustBe 5
      res.count(_.pk == Conf.pk) mustBe 1
    }

    "get proposals's commitments" in {
      mockServer.stubFor(
        get(urlPathEqualTo(s"/proposal/4/commitments"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/proposal_commitments.json").mkString)
            .withStatus(200)))
      val res = Server.getCommitments(4)
      res.length mustBe 4
      res.count(_.isRejected) mustBe 1
    }
  }
}
