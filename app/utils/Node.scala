package utils

import models.{Request, Secret, Team}
import play.api.Logger
import play.api.libs.json._
import scalaj.http.Http

import scala.collection.mutable

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

  /**
   * gets box as raw
   * @param boxId box id
   * @return string representing the raw box
   */
  def getBoxRaw(boxId: String): String = {
    val res = Http(s"${Conf.nodeUrl}/utxo/byIdBinary/$boxId").headers(defaultHeader).asString
    (Json.parse(res.body) \ "bytes").as[String]
  }

  /**
   * generates an unsigned tx
   * @param sourceAddr source address for input boxes
   * @param ergAmount amount
   * @param destAddr address to send amount to
   * @return (success, tx), i.e. was generation successful and the tx itself
   */
  def generateUnsignedTx(sourceAddr: String, ergAmount: Long, destAddr: String, tokenId: String = "", tokenAmount: Long = 0L): (Boolean, String) = {
    val fee = 2000000
    val needErg = ergAmount + fee
    val boxes = Explorer.getUnspentBoxes(sourceAddr)

    val sm = boxes.map(_.value).sum
    val changeTokens: mutable.Map[String, Long] = mutable.Map.empty
    boxes.foreach(box => box.tokens.foreach(token => {
      changeTokens(token._1) = changeTokens.getOrElse(token._1, 0L) + token._2
    }))

    if (sm < needErg) {
      logger.error(s"not enough ergs to satisfy proposal $ergAmount nano ergs!")
      return (false, "")
    }
    if (tokenId.nonEmpty) {
      if (changeTokens(tokenId) < tokenAmount) {
        logger.error(s"not enough token $tokenId to satisfy proposal $tokenAmount token!")
        return (false, "")
      }

      changeTokens(tokenId) -= tokenAmount
      if (changeTokens(tokenId) == 0) changeTokens.remove(tokenId)

      if (changeTokens.nonEmpty && sm - needErg < 100000) {
        logger.error(s"Although there is enough erg for the proposal we won't assemble tx! because we will lose tokens, need some more ergs for change box!")
        return (false, "")
      }
    }
    val inputsRaw = boxes.map(box => getBoxRaw(box.id))
    var endBoxes: Seq[String] = Nil

    val changeAsset = changeTokens.map(token =>
      s"""{
        |  "tokenId": "${token._1}",
        |  "amount": ${token._2}
        |}""".stripMargin).mkString(",")
    var requestAsset = ""
    if (tokenId.nonEmpty) requestAsset =
      s"""{
         |  "tokenId": "$tokenId",
         |  "amount": $tokenAmount
         |}""".stripMargin


    endBoxes = endBoxes :+
      s"""{
        |  "address": "$destAddr",
        |  "value": $ergAmount,
        |  "assets": [${requestAsset}]
        |}""".stripMargin
    if (sm > ergAmount + fee) {
      endBoxes = endBoxes :+
        s"""{
           |  "address": "$sourceAddr",
           |  "value": ${sm - ergAmount - fee},
           |  "registers": ${boxes.head.registers},
           |  "assets": [$changeAsset]
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

  /**
   * generates an unsigned tx
   * @param tx transaction to sign
   * @param secret secret associated with the proposal (commitment)
   * @param proofs other partial proofs needed
   * @return (success, tx) signs the tx, used for simulation, partial proof generation and signing the assembled tx
   */
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

  /**
   * extracts hints from a signed tx
   * @param tx tx
   * @param real real signers
   * @param simulated simulated
   * @return (success, hints)
   */
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

  /**
   * checks to see if tx is ok
   * @param tx transaction
   * @return whether tx is ok or not
   */
  def isTxOk(tx: String): Boolean = {
    val res = Http(s"${Conf.nodeUrl}/transactions/check").postData(tx).headers(defaultHeader).asString
    res.isSuccess
  }

  /**
   * broadcasts tx
   * @param tx transaction
   * @return whether it was successful or not
   */
  def broadcastTx(tx: String): Boolean = {
    val res = Http(s"${Conf.nodeUrl}/transactions").postData(tx).headers(defaultHeader).asString
    res.isSuccess
  }
}
