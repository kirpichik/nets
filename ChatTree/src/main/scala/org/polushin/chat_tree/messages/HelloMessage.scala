package org.polushin.chat_tree.messages

import java.net.{DatagramPacket, DatagramSocket}
import java.util.UUID

import org.polushin.chat_tree.MessageTarget

/**
 * Уведомление о существовании нового узла.
 */
class HelloMessage(guid: UUID, port: Int) extends Message(guid, port) {

  def this(port: Int) = this(UUID.randomUUID(), port)

  override def send(socket: DatagramSocket, target: MessageTarget): Unit = {
    val array = formPacketPrefix()
    socket.send(new DatagramPacket(array, array.length, target._1, target._2))
  }

  override protected def getId: Short = 2
}
