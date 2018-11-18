package org.polushin.rest_chat

import java.net.InetAddress

class ClientChatProvider(address: InetAddress, port: Int) extends ChatProvider {

  override def sendMessageToChat(msg: String): Unit = ???

  override def getUsers: Set[User] = ???

  override def getUser(nickname: String): Option[User] = ???

  override def shutdown(): Unit = ???
}
