package models

import java.nio.charset.StandardCharsets

import play.api.libs.json.JsValue
import utils.Conf

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

case class Member(pk: String, teamId: Long, nickName: String, id: Long)

object Member {
  def apply(member: JsValue): Member = {
    val nickName = (member \\ "nickName").head.as[String]
    val pk = (member \\ "pk").head.as[String]
    val id = (member \\ "id").head.as[Long]
    val teamId = (member \\ "teamId").head.as[Long]
    new Member(pk, teamId, nickName, id)
  }
}

case class Request(title: String, amount: Long, description: String, address: String, teamId: Long, status: String, commitments: Seq[Commitment], id: Long) {
  def isRejected: Boolean = status == RequestStatus.rejected

  def isApproved: Boolean = status == RequestStatus.approved

  def isPending: Boolean = status == RequestStatus.pendingApproval

  def iRejected: Boolean = commitments.exists(cmt => cmt.isMine && cmt.isRejected)

  def iApproved: Boolean = commitments.exists(cmt => cmt.isMine && cmt.isApproved)

  def pendingMe: Boolean = !iRejected && !iApproved

  def numRejected: Int = commitments.count(_.isRejected)

  def numApproved: Int = commitments.count(_.isApproved)
}

object Request {
  def apply(proposal: JsValue): Request = {
    val title = (proposal \\ "title").head.as[String]
    val description = (proposal \\ "description").head.as[String]
    val address = (proposal \\ "address").head.as[String]
    val status = (proposal \\ "status").head.as[String]
    val amount = (proposal \\ "amount").head.as[Long]
    val id = (proposal \\ "id").head.as[Long]
    val teamId = (proposal \\ "teamId").head.as[Long]
    val commitments = (proposal \\ "commitments").head.as[Seq[JsValue]].map(cmt => Commitment(cmt))
    Request(title, amount, description, address, teamId, status, commitments, id)
  }
}


case class Commitment(member: Member, a: String, reqId: Long, memId: Long) {
  def isMine: Boolean = member.pk == Conf.pk

  def isRejected: Boolean = a.isEmpty

  def isApproved: Boolean = !isRejected

  override def toString: String = member.nickName + s" (${member.pk.slice(0, 15)}...)"
}

object Commitment {
  def apply(cmt: JsValue): Commitment = {
    val member = Member((cmt \\ "member").head.as[JsValue])
    val a = (cmt \\ "a").head.as[String]
    val reqId = (cmt \\ "requestId").head.as[Long]
    val memId = (cmt \\ "memberId").head.as[Long]
    Commitment(member, a, reqId, memId)
  }
}

case class Transaction(reqId: Long, isPartial: Boolean, bytes: Array[Byte], isValid: Boolean, isConfirmed: Boolean, pk: String) {
    override def toString: String = new String(bytes, StandardCharsets.UTF_16)
}
