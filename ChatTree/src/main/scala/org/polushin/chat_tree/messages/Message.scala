package org.polushin.chat_tree.messages

import java.net.{DatagramSocket, InetAddress}
import java.nio.ByteBuffer
import java.util.UUID

abstract class Message(guid: UUID, port: Int) {

  val senderPort: Int = port
  val uuid: UUID = guid

  /**
   * Отправляет сообщение на заданный адрес и порт используя указанный датаграм-сокет.
   *
   * @param socket Датаграм-сокет.
   * @param target Получатель пакета.
   */
  def send(socket: DatagramSocket, target: (InetAddress, Int))

  override def toString: String = s"(type: ${getClass.getSimpleName}, uuid: $uuid)"

  /**
   * @return Уникальный ID пакета.
   */
  protected def getId: Short

  /**
   * Формирует префиксный пакет в виде:
   * (UUID-пакета)(порт-получателя)(id-пакета)
   *
   * @return Сформированный префикс в виде массива байт.
   */
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

  /**
   * Разбирает массив байт в виде объекта сообщения.
   *
   * @param bytes Массив байт.
   *
   * @return Опционально: объект сообщения.
   */
  def parseMessage(bytes: Array[Byte], length: Int): Option[Message] = {
    if (bytes.length < PREFIX_BYTES)
      return null

    val buffer = ByteBuffer.wrap(bytes)
    val uuid = loadUuid(buffer)
    val port = buffer.getInt

    buffer.getShort match {
      case 0 =>
        val text = new String(bytes, PREFIX_BYTES, length - PREFIX_BYTES, TextMessage.DEFAULT_CHARSET)
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
