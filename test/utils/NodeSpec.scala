package utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{JsValue, Json}

import scala.io.Source.fromFile

class NodeSpec extends PlaySpec with BeforeAndAfterEach {
  private val mockPort = 9090
  private val mockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(mockPort))

  override def beforeEach(): Unit = {
    super.beforeEach()
    mockServer.start()
    mockServer.stubFor(
      get(urlPathEqualTo(s"/api/v0/transactions/boxes/byAddress/unspent/someAddr"))
        .willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(fromFile("test/resources/unspent_boxes.json").mkString)
          .withStatus(200)))
    mockServer.stubFor(
      get(urlPathMatching(s"/utxo/byIdBinary/(.*)"))
        .willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """{
              |  "bytes": "bytes"
              |}""".stripMargin)
          .withStatus(200)))
    mockServer.stubFor(
      post(urlPathEqualTo("/wallet/transaction/generateUnsigned"))
        .willReturn(aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("{}".stripMargin)
          .withStatus(200)))

  }

  override def afterEach(): Unit = {
    super.afterEach()
    mockServer.stop()
  }


  "generate tx" must {
    "return false because of not enough ergs" in {
      val res = Node.generateUnsignedTx("someAddr", 1e8.toLong, "destAddr")
      res._1 mustBe false
    }

    "return false because not enough token available" in {
      val tokenId = "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
      val res = Node.generateUnsignedTx("someAddr", 1e6.toLong, "destAddr", tokenId, 70)
      res._1 mustBe false
    }

    "return false because generating tx will result in token loss" in {
      val tokenId = "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
      val res = Node.generateUnsignedTx("someAddr", 8996000, "destAddr", tokenId, 50)
      res._1 mustBe false
    }

    "generate tx with proper change for erg and tokens" in {
      val tokenId = "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
      val res = Node.generateUnsignedTx("someAddr", 1e6.toLong, "destAddr", tokenId, 50)
      res._1 mustBe true
      val requests = mockServer.findAll(postRequestedFor(urlPathEqualTo("/wallet/transaction/generateUnsigned")));
      requests.size() mustBe 1
      val req = Json.parse(requests.get(0).getBodyAsString)
      (req \ "inputsRaw").as[Seq[String]].length mustBe 2
      val outs = (req \ "requests").as[Seq[JsValue]]
      val to = outs.head
      val change = outs(1)
      (to \ "address").as[String] mustBe "destAddr"
      (to \ "value").as[Long] mustBe 1e6.toLong
      (to \ "assets").as[Seq[JsValue]].length mustBe 1
      val toToken = (to \ "assets").as[Seq[JsValue]].head
      (toToken \ "tokenId").as[String] mustBe "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98"
      (toToken \ "amount").as[Long] mustBe 50
      (change \ "address").as[String] mustBe "someAddr"
      (change \ "value").as[Long] mustBe 8000000
      val changeTokens = (change \ "assets").as[JsValue]
      val expected = Json.parse(
        """[{
          |  "tokenId": "991594920258238d11d7713af0e9a1ebcf49a765bc5e5066bfde95bcf720a585",
          |  "amount": 2000
          | }, {
          |  "tokenId": "1a6a8c16e4b1cc9d73d03183565cfb8e79dd84198cb66beeed7d3463e0da2b98",
          |  "amount": 10
          | }]""".stripMargin)
      changeTokens mustBe expected.as[JsValue]
    }
  }

}
