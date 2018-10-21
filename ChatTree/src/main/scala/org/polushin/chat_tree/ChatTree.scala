package org.polushin.chat_tree

import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.util.{Scanner, UUID}

import org.polushin.chat_tree.messages._

import scala.util.Random

class ChatTree(parent: Option[(InetAddress, Int)] = None, currentPort: Int, dropChance: Int) {

  type HistoryEntry = (UUID, MessageType)

  private val outputMessagesHandler = new OutputMessagesHandler(noRecipientResponseHandler)
  private val socket = new DatagramSocket(currentPort)
  private val random = new Random

  private val neighbors = createConcurrentHashSet[MessageTarget]()

  private val messagesHistory = createFixedSizeSet[HistoryEntry](ChatTree.UUIDS_HISTORY_SIZE)

  /**
   * Запускает обработку сообщений.
   */
  def start(): Unit = {
    parent foreach {
      parent => outputMessagesHandler.sendMessage(new HelloMessage, parent)
    }

    inputMessagesHandler.setDaemon(true)
    inputMessagesHandler.start()

    handleUserInput()
  }

  private def handleUserInput(): Unit = {
    val scanner = new Scanner(System.in, TextMessage.DEFAULT_CHARSET.name())
    while (scanner.hasNextLine)
      neighbors.foreach(children => outputMessagesHandler.sendMessage(new TextMessage(scanner.nextLine()), children))
  }

  private val inputMessagesHandler = new Thread(() => {
    val buffer = new Array[Byte](ChatTree.BUFFER_SIZE)
    while (true) {
      val packet = new DatagramPacket(buffer, buffer.length)
      socket.receive(packet)
      // TODO - big packet?
      if (dropChance >= random.nextInt(100))
        Message.parseMessage(packet.getData) foreach {
          message => handleMessage(packet, message)
        }
    }
  })

  private def handleMessage(packet: DatagramPacket, message: Message): Unit = {
    val receiver = (packet.getAddress, message.senderPort)

    message match {
      case m: HelloMessage =>
        if (messagesHistory.add(m.uuid, classOf[HelloMessage]))
          neighbors.add((packet.getAddress, packet.getPort))
        outputMessagesHandler.sendMessage(new AcceptHelloMessage(m.uuid), receiver, repeat = false)
      case m: TextMessage =>
        if (messagesHistory.add(m.uuid, classOf[TextMessage]))
          println("Input message: " + m.message)
        outputMessagesHandler.sendMessage(new AcceptTextMessage(m.uuid), receiver, repeat = false)
      case m: AcceptHelloMessage =>
        parent foreach {
          parent =>
            if (parent == receiver) {
              neighbors.add(parent)
              outputMessagesHandler.cancelRepeatByType(classOf[HelloMessage], m.acceptedUuid)
            }
        }
      case m: AcceptTextMessage => outputMessagesHandler.cancelRepeatByType(classOf[TextMessage], m.acceptedUuid)
    }
  }

  private def noRecipientResponseHandler(messageTarget: MessageTarget): Unit = {
    neighbors.remove(messageTarget)
  }
}

object ChatTree {
  private val BUFFER_SIZE = 1024
  private val UUIDS_HISTORY_SIZE = 1024
}
