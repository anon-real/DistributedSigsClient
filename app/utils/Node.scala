package utils

import models.{Request, Secret, Team}
import play.api.Logger
import play.api.libs.json._
import scalaj.http.Http

object Node {
  private val logger: Logger = Logger(this.getClass)
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"), ("api_key", Conf.nodeApi))

  /**
   * gets a new commitment from node!
   * @return (a, r)
   */
  def produceCommitment(): (String, String) = {
    val res = Http(s"${Conf.nodeUrl}/script/generateCommitment").postData(
      s"""{
        |  "op": -51,
        |  "h": "${Conf.pk}"
        |}""".stripMargin).headers(defaultHeader).asString
    val js = Json.parse(res.body)
    ((js \\ "a").head.as[String], (js \\ "r").head.as[String])
  }

  def getBoxRaw(boxId: String): String = {
    val res = Http(s"${Conf.nodeUrl}/utxo/byIdBinary/$boxId").headers(defaultHeader).asString
    (Json.parse(res.body) \ "bytes").as[String]
  }

  def generateUnsignedTx(sourceAddr: String, amount: Long, destAddr: String): (Boolean, String) = {
    val fee = 2000000
    var need = amount + fee
    val boxes = Explorer.getUnspentBoxes(sourceAddr).takeWhile(box => {
      need -= box.value
      need + box.value > 0
    })
    if (need > 0) {
      logger.error(s"not enough ergs to satisfy proposal $amount nano ergs!")
      return (false, "")
    }
    val inputsRaw = boxes.map(box => getBoxRaw(box.id))
    var endBoxes: Seq[String] = Nil

    endBoxes = endBoxes :+
      s"""{
        |  "address": "$destAddr",
        |  "value": $amount
        |}""".stripMargin
    val sm = boxes.map(_.value).sum
    if (sm > amount + fee) {
      endBoxes = endBoxes :+
        s"""{
           |  "address": "$sourceAddr",
           |  "value": ${sm - amount - fee},
           |  "registers": ${boxes.head.registers}
           |}""".stripMargin
    }
    val request =
      s"""{
        |  "requests": [${endBoxes.mkString(",")}],
        |  "fee": $fee,
        |  "inputsRaw": [${inputsRaw.map(in => s""""$in"""").mkString(",")}]
        |}""".stripMargin

    val res = Http(s"${Conf.nodeUrl}/wallet/transaction/generateUnsigned").postData(request).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"could not generate unsinged tx: ${res.body}")
      (false, "")
    } else {
      logger.info("successfully generated unsigned tx.")
      (true, res.body)
    }
  }

  def signTx(tx: String, secret: Secret, proofs: Seq[String]): (Boolean, String) = {
    val mySec =
      s"""
        |{
        |      "hint": "cmtWithSecret",
        |      "type": "dlog",
        |      "pubkey": {
        |         "op": -51,
        |         "h": "${Conf.pk}"
        |      },
        |      "a": "${secret.a}" ,
        |      "secret": "${secret.r}"
        |     }
        |""".stripMargin
    val allProofs = (proofs :+ mySec).reverse
    val request =
      s"""{
        |  "tx": $tx,
        |  "secrets": {
        |    "dlog": [${Conf.secretSeq.map(sec => s""""$sec"""").mkString(",")}]
        |  },
        |  "hints": [${allProofs.mkString(",")}]
        |}""".stripMargin

    val res = Http(s"${Conf.nodeUrl}/wallet/transaction/sign").postData(request).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"could not sign tx: ${res.body}")
      (false, "")
    } else {
      logger.info("successfully signed tx.")
      (true, res.body)
    }
  }

  def extractHints(tx: String, real: Seq[String], simulated: Seq[String]): (Boolean, String) = {
    val realR = real.map(r =>
      s"""{
        |  "op": -51,
        |  "h": "$r"
        |}""".stripMargin)
    val simulatedR = simulated.map(r =>
      s"""{
         |  "op": -51,
         |  "h": "$r"
         |}""".stripMargin)
    val request =
      s"""{
         |  "transaction": $tx,
         |  "real": [${realR.mkString(",")}],
         |  "simulated": [${simulatedR.mkString(",")}]
         |}""".stripMargin

    val res = Http(s"${Conf.nodeUrl}/script/extractHints").postData(request).headers(defaultHeader).asString
    if (res.isError) {
      logger.error(s"could extract hints: ${res.body}")
      (false, "")
    } else {
      logger.info("successfully extracted hints")
      (true, res.body)
    }
  }
}
