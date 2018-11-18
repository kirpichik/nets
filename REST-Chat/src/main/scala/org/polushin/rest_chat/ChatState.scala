package org.polushin.rest_chat

import java.io.IOException
import java.net.InetAddress

class ChatState {

  private var provider: Option[ChatProvider] = None

  @throws[IOException]("If it's impossible to start server on this port")
  def bindServer(port: Int): Boolean = {
    shutdown()
    try {
      provider = Option(new ServerChatProvider(port))
      true
    } catch {
      case e: Exception =>
        sendMessageToOwner("Cannot host server: " + e.getMessage)
        false
    }
  }

  @throws[IOException]("If it's impossible to connect to required server")
  def connectServer(nick: String, address: InetAddress, port: Int): Boolean = {
    shutdown()
    try {
      provider = Option(new ClientChatProvider(nick, address, port))
      true
    } catch {
      case e: Exception =>
        sendMessageToOwner("Cannot connect to server: " + e.getMessage)
        false
    }
  }

  def isInitialized: Boolean = provider.nonEmpty

  def shutdown(): Unit = provider match {
    case Some(p) =>
      p.shutdown()
      provider = None
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
  def getUsers: Set[String] = provider match {
    case Some(p) => p.getUsers
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUser(nick: String): Option[UserData] = provider match {
    case Some(p) => p.getUser(nick)
    case None => throw new IllegalStateException("No connection")
  }
}
