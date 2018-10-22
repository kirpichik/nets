package org.polushin.chat_tree.messages

import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.UUID

/**
 * Пакет отправки текстового сообщения.
 */
class TextMessage(guid: UUID, port: Int, msg: String) extends Message(guid, port) {

  def this(port: Int, msg: String) = this(UUID.randomUUID(), port, msg)

  val message: String = msg

  override def send(socket: DatagramSocket, target: (InetAddress, Int)): Unit = {
    val prefix = formPacketPrefix()
    val messageBytes = msg.getBytes(TextMessage.DEFAULT_CHARSET)
    val array = ByteBuffer.allocate(prefix.length + messageBytes.length).put(prefix).put(messageBytes).array()

    socket.send(new DatagramPacket(array, array.length, target._1, target._2))
  }

  override protected def getId: Short = 0
}

object TextMessage {
  val DEFAULT_CHARSET: Charset = Charset.forName("UTF-8")
}
