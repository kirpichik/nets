package org.polushin.chat_tree.messages

import java.net.{DatagramPacket, DatagramSocket}
import java.nio.ByteBuffer
import java.util.UUID

import org.polushin.chat_tree.MessageTarget

/**
 * Подтверждение подключения нового узла.
 */
class AcceptHelloMessage(guid: UUID, port: Int, accepted: UUID) extends Message(guid, port) {

  def this(port: Int, accepted: UUID) = this(UUID.randomUUID(), port, accepted)

  val acceptedUuid: UUID = accepted

  override def send(socket: DatagramSocket, target: MessageTarget): Unit = {
    val prefix = formPacketPrefix()
    val array = ByteBuffer.allocate(prefix.length + Message.UUID_BYTES)
      .put(prefix)
      .putLong(acceptedUuid.getMostSignificantBits)
      .putLong(acceptedUuid.getLeastSignificantBits)
      .array()
    socket.send(new DatagramPacket(array, array.length, target._1, target._2))
  }

  override protected def getId: Short = 3
}
