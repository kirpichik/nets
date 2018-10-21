package org.polushin.chat_tree.messages

import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Подтверждение получения сообщения.
 */
class AcceptTextMessage(guid: UUID, port: Int, accepted: UUID) extends Message(guid, port) {

  def this(accepted: UUID) = this()

  val acceptedUuid: UUID = accepted

  override def send(socket: DatagramSocket, target: (InetAddress, Int)): Unit = {
    val prefix = formPacketPrefix()
    val array = ByteBuffer.allocate(prefix.length + Message.UUID_BYTES)
      .put(prefix)
      .putLong(acceptedUuid.getMostSignificantBits)
      .putLong(acceptedUuid.getLeastSignificantBits)
      .array()
    socket.send(new DatagramPacket(array, array.length, target._1, target._2))
  }

  override protected def getId: Short = 1
}
