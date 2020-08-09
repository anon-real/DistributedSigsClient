package models

import java.nio.charset.StandardCharsets

import play.api.libs.json.JsValue

object RequestStatus {
  val pendingApproval = "Pending Approval"
  val rejected = "Rejected"
  val approved = "Approved"
  val paid = "Fund Paid"
}

case class Team(name: String, description: String, address: String, pendingNum: Int, id: Long)

object Team {
  def apply(team: JsValue): Team = {
    val name = (team \\ "name").head.as[String]
    val desc = (team \\ "description").head.as[String]
    val address = (team \\ "address").head.as[String]
    val id = (team \\ "id").head.as[Long]
    val pendingNum = (team \ "pendingNum").asOpt[Int].getOrElse(0)
    Team(name, desc, address, pendingNum, id)
  }
}

case class Member(pk: String, teamId: Long)

case class Request(title: String, amount: Long, description: String, address: String, teamId: Long, status: String, id: Long)

object Request {
  def apply(proposal: JsValue): Request = {
    val title = (proposal \\ "title").head.as[String]
    val description = (proposal \\ "description").head.as[String]
    val address = (proposal \\ "address").head.as[String]
    val status = (proposal \\ "status").head.as[String]
    val amount = (proposal \\ "amount").head.as[Long]
    val id = (proposal \\ "id").head.as[Long]
    val teamId = (proposal \\ "teamId").head.as[Long]
    Request(title, amount, description, address, teamId, status, id)
  }
}


case class Commitment(pk: String, a: String, reqId: Long)

case class Transaction(reqId: Long, isPartial: Boolean, bytes: Array[Byte], isValid: Boolean, isConfirmed: Boolean, pk: String) {
    override def toString: String = new String(bytes, StandardCharsets.UTF_16)
}
