package org.polushin.rest_chat

import java.util.UUID

import scala.concurrent.duration._

class User(name: String, id: UUID) {

  def this(name: String) = {
    this(name, UUID.randomUUID())
    updateActivity()
  }

  val username: String = name
  val uuid: UUID = id

  private var lastActivity: Long = -1

  def getLastActivity: Long = lastActivity

  def updateActivity(): Unit = lastActivity = System.currentTimeMillis()

  def toUserData: UserData = UserData(name, User.ACTIVITY_TIMEOUT < System.currentTimeMillis() - lastActivity)

  override def toString: String = username
}

object User {
  val ACTIVITY_TIMEOUT: Long = (5 minutes)._1

  def fromUserAccess(user: UserAccess): User = new User(user.nickname, UUID.fromString(user.token))
}
