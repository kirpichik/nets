package org.polushin.rest_chat

import java.util.UUID

class ServerChatProvider(port: Int) extends ChatProvider {

  override def broadcastMessage(msg: String): Unit = ???

  override def getUsers: List[User] = ???

  override def getUser(nick: String): Option[User] = ???

  override def getUser(uuid: UUID): Option[User] = ???

  override def kick(user: User): Boolean = ???

  override def shutdown(): Unit = ???
}
