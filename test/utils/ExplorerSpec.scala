package utils

import java.io.File

import akka.serialization.Serialization
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import com.github.tomakehurst.wiremock.client.WireMock._
import scala.io.Source.fromFile
import play.api.test.Helpers._



class ExplorerSpec extends PlaySpec with BeforeAndAfterEach {
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
    "parse unspent boxes" in {
      val address = "someAddress"
      mockServer.stubFor(
        get(urlPathEqualTo(s"/api/v0/transactions/boxes/byAddress/unspent/$address"))
          .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(fromFile("test/resources/boxes.json").mkString)
            .withStatus(200)))
      val res = Explorer.getUnspentBoxes(address)
      res.length mustBe 2
      res.head.tokens mustBe Seq(("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 63))
      res(1).tokens mustBe Seq(("1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98", 38))
      res.foreach(box => {
        (4 to 7).foreach(num => box.registers must include (s"R$num"))
      })
    }
  }

}
