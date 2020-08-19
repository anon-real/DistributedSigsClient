package utils

import models.Box
import play.api.libs.json._
import scalaj.http.Http

object Explorer {
  private val defaultHeader: Seq[(String, String)] = Seq[(String, String)](("Content-Type", "application/json"))

  /**
   * gets list of unspent boxes of an address
   */
  def getUnspentBoxes(address: String): Seq[Box] = {
    val res = Http(s"${Conf.explorerUrl}/api/v0/transactions/boxes/byAddress/unspent/$address").headers(defaultHeader).asString
    val js = Json.parse(res.body)
    js.as[Seq[JsValue]].map(box => Box((box \ "id").as[String] , (box \ "value").as[Long], (box \ "additionalRegisters").get.toString))
  }
}
