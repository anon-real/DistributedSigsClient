package models

import java.nio.charset.StandardCharsets

import play.api.libs.json.JsValue
import utils.Conf
import utils.Util._

object RequestStatus {
  val pendingApproval = "Pending Approval"
  val rejected = "Rejected"
  val approved = "Approved"
  val paid = "Fund Paid"
}

case class Team(name: String, description: String, address: String, pendingNum: Int, memberId: Long, id: Long)

object Team {
  def apply(team: JsValue, memberId: Long = 0L, pending: Int = 0): Team = {
    val name = (team \ "name").as[String]
    val desc = (team \ "description").as[String]
    val address = (team \ "address").as[String]
    val id = (team \ "id").as[Long]
    Team(name, desc, address, pending, memberId, id)
  }
}

case class Member(pk: String, teamId: Long, nickName: String, id: Long)

object Member {
  def apply(member: JsValue): Member = {
    val nickName = (member \ "nickName").as[String]
    val pk = (member \ "pk").as[String]
    val id = (member \ "id").as[Long]
    val teamId = (member \ "teamId").as[Long]
    new Member(pk, teamId, nickName, id)
  }
}

case class Request(title: String, amount: Double, description: String, address: String, teamId: Long, status: String, commitments: Seq[Commitment], id: Long) {
  def isRejected: Boolean = status == RequestStatus.rejected

  def isApproved: Boolean = status == RequestStatus.approved

  def isPending: Boolean = status == RequestStatus.pendingApproval

  def iRejected: Boolean = commitments.exists(cmt => cmt.isMine && cmt.isRejected)

  def iApproved: Boolean = commitments.exists(cmt => cmt.isMine && cmt.isApproved)

  def pendingMe: Boolean = !iRejected && !iApproved

  def numRejected: Int = commitments.count(_.isRejected)

  def numApproved: Int = commitments.count(_.isApproved)

  def sortedCmts: Seq[Commitment] = {
    commitments.sortBy(cmt => boolAsInt(cmt.isApproved) + boolAsInt(cmt.isMine)).reverse
  }
}

object Request {
  def apply(proposal: JsValue): Request = {
    val title = (proposal \ "title").as[String]
    val description = (proposal \ "description").as[String]
    val address = (proposal \ "address").as[String]
    val status = (proposal \ "status").as[String]
    val amount = (proposal \ "amount").as[Double]
    val id = (proposal \ "id").as[Long]
    val teamId = (proposal \ "teamId").as[Long]
    val commitments = (proposal \ "commitments").as[Seq[JsValue]].map(cmt => Commitment(cmt))
    Request(title, amount, description, address, teamId, status, commitments, id)
  }
}

case class Commitment(member: Member, a: String, reqId: Long, memId: Long) {
  def isMine: Boolean = member.pk == Conf.pk

  def isRejected: Boolean = a.isEmpty

  def isApproved: Boolean = !isRejected

  override def toString: String = {
    var postfix = "rejected"
    if (isApproved) postfix = "approved"
    if (isMine) s"${member.nickName} (YOU) $postfix"
    else s"${member.nickName} (${member.pk.slice(0, 15)}...) $postfix"
  }
}

object Commitment {
  def apply(cmt: JsValue): Commitment = {
    val member = Member((cmt \ "member").as[JsValue])
    val a = (cmt \ "a").as[String]
    val reqId = (cmt \ "requestId").as[Long]
    val memId = (cmt \ "memberId").as[Long]
    Commitment(member, a, reqId, memId)
  }
}

case class Transaction(reqId: Long, isPartial: Boolean, bytes: Array[Byte], isValid: Boolean, isConfirmed: Boolean, pk: String) {
    override def toString: String = new String(bytes, StandardCharsets.UTF_16)
}

case class Secret(a: String, r: String, requestId: Long)

case class Box(id: String, value: Long, registers: String)

case class Proof(memberId: Long, reqId: Long, proof: String, simulated: Boolean)

object Proof {
  def apply(proof: JsValue): Proof = {
    val memberId = (proof \ "memberId").as[Long]
    val reqId = (proof \ "requestId").as[Long]
    val p = (proof \ "proof").get.toString()
    val simulated = (proof \ "simulated").as[Boolean]
    Proof(memberId, reqId, p, simulated)
  }
}

