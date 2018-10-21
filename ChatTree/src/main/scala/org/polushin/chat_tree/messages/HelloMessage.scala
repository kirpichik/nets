package org.polushin.chat_tree.messages

import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.util.UUID

/**
 * Уведомление о существовании нового узла.
 */
class HelloMessage(guid: UUID, port: Int) extends Message(guid, port) {

  def this() = this()

  override def send(socket: DatagramSocket, target: (InetAddress, Int)): Unit = {
    val array = formPacketPrefix()
    socket.send(new DatagramPacket(array, array.length, target._1, target._2))
  }

  override protected def getId: Short = 2
}
