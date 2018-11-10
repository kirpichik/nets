package org.polushin.rest_chat

import java.util.UUID

trait ChatProvider {

  def broadcastMessage(msg: String): Unit

  def getUsers: List[User]

  def getUser(nick: String): Option[User]

  def getUser(uuid: UUID): Option[User]

  def kick(user: User): Boolean

  def shutdown(): Unit

}
