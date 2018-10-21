package org.polushin.chat_tree.messages

import java.net.{DatagramSocket, InetAddress}
import java.nio.ByteBuffer
import java.util.UUID

abstract class Message(guid: UUID, port: Int) {

  def this() {
    this(UUID.randomUUID(), 0)
  }

  val senderPort: Int = port
  val uuid: UUID = guid

  def send(socket: DatagramSocket, target: (InetAddress, Int))

  protected def getId: Short

  protected def formPacketPrefix(): Array[Byte] = {
    ByteBuffer.allocate(Message.PREFIX_BYTES)
      .putLong(uuid.getMostSignificantBits)
      .putLong(uuid.getLeastSignificantBits)
      .putInt(senderPort)
      .putShort(getId)
      .array()
  }

}

object Message {

  val UUID_BYTES: Int = 8 + 8
  val PORT_BYTES: Int = 4
  val TYPE_BYTES: Int = 2
  val PREFIX_BYTES: Int = UUID_BYTES + PORT_BYTES + TYPE_BYTES

  def parseMessage(bytes: Array[Byte]): Option[Message] = {
    if (bytes.length < PREFIX_BYTES)
      return null

    val buffer = ByteBuffer.wrap(bytes)
    val uuid = loadUuid(buffer)
    val port = buffer.getInt

    buffer.getShort match {
      case 0 =>
        val text = new String(bytes, PREFIX_BYTES, bytes.length - PREFIX_BYTES, TextMessage.DEFAULT_CHARSET)
        Option(new TextMessage(uuid, port, text))
      case 1 =>
        val accept = loadUuid(buffer)
        Option(new AcceptTextMessage(uuid, port, accept))
      case 2 => Option(new HelloMessage(uuid, port))
      case 3 =>
        val accept = loadUuid(buffer)
        Option(new AcceptHelloMessage(uuid, port, accept))
      case _ => None
    }
  }

  private def loadUuid(buffer: ByteBuffer): UUID = {
    val mostBits = buffer.getLong
    val leastBits = buffer.getLong
    new UUID(mostBits, leastBits)
  }

}
