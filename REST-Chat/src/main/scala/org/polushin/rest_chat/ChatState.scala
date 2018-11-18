package org.polushin.rest_chat

import java.io.IOException
import java.net.InetAddress

class ChatState {

  private var provider: Option[ChatProvider] = None

  @throws[IOException]("If it's impossible to start server on this port")
  def bindServer(port: Int): Unit = {
    shutdown()
    provider = Option(new ServerChatProvider(port))
  }

  @throws[IOException]("If it's impossible to connect to required server")
  def connectServer(address: InetAddress, port: Int): Unit = {
    shutdown()
    provider = Option(new ClientChatProvider(address, port))
  }

  def isInitialized: Boolean = provider.nonEmpty

  def shutdown(): Unit = provider match {
    case Some(p) => p.shutdown()
    case _ =>
  }

  def sendMessageToOwner(msg: String): Unit = provider match {
    case Some(p) => p.sendMessageToOwner(msg)
    case _ => println(s"[$SystemUser]: $msg")
  }

  @throws[IllegalStateException]("If connection is not established")
  def sendMessageToChat(msg: String): Unit = provider match {
    case Some(p) => p.sendMessageToChat(msg)
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUsers: Set[User] = provider match {
    case Some(p) => p.getUsers
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUser(nick: String): Option[User] = provider match {
    case Some(p) => p.getUser(nick)
    case None => throw new IllegalStateException("No connection")
  }
}
