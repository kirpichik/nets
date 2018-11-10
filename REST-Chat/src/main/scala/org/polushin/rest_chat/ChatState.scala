package org.polushin.rest_chat

import java.io.IOException
import java.net.InetAddress
import java.util.UUID

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
  }

  def sendMessageToOwner(msg: String, sender: User = SystemUser): Unit = {
    println(s"[$sender]: $msg")
  }

  @throws[IllegalStateException]("If connection is not established")
  def broadcastMessage(msg: String): Unit = provider match {
    case Some(p) => p.broadcastMessage(msg)
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUsers: List[User] = provider match {
    case Some(p) => p.getUsers
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUser(nick: String): Option[User] = provider match {
    case Some(p) => p.getUser(nick)
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def getUser(uuid: UUID): Option[User] = provider match {
    case Some(p) => p.getUser(uuid)
    case None => throw new IllegalStateException("No connection")
  }

  @throws[IllegalStateException]("If connection is not established")
  def kick(user: User): Boolean = provider match {
    case Some(p) => p.kick(user)
    case None => throw new IllegalStateException("No connection")
  }
}
