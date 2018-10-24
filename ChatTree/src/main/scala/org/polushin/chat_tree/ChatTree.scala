package org.polushin.chat_tree

import java.net.{DatagramPacket, DatagramSocket, InetAddress}
import java.util.{Scanner, UUID}

import org.polushin.chat_tree.messages._

import scala.util.Random

class ChatTree(parent: Option[(InetAddress, Int)] = None, currentPort: Int, dropChance: Int) {

  private val outputMessagesHandler = new OutputMessagesHandler(noRecipientResponseHandler)
  private val socket = new DatagramSocket(currentPort)
  private val random = new Random

  private val neighbors = createConcurrentHashSet[MessageTarget]()

  private val messagesHistory = createFixedSizeSet[UUID](ChatTree.UUIDS_HISTORY_SIZE)

  /**
   * Запускает обработку сообщений.
   */
  def start(): Unit = {
    parent foreach {
      parent => outputMessagesHandler.sendMessage(new HelloMessage(currentPort), parent)
    }

    inputMessagesHandler.setDaemon(true)
    inputMessagesHandler.start()

    handleUserInput()
  }

  private def handleUserInput(): Unit = {
    val scanner = new Scanner(System.in, TextMessage.DEFAULT_CHARSET.name())
    while (scanner.hasNextLine) {
      val message = new TextMessage(currentPort, scanner.nextLine())
      messagesHistory.add(message.uuid)
      neighbors.foreach(target =>
        outputMessagesHandler.sendMessage(message, target)
      )
    }
  }

  private val inputMessagesHandler = new Thread(() => {
    val buffer = new Array[Byte](ChatTree.BUFFER_SIZE)
    while (true) {
      val packet = new DatagramPacket(buffer, buffer.length)
      socket.receive(packet)
      if (dropChance <= random.nextInt(100))
        Message.parseMessage(packet.getData, packet.getLength) foreach {
          message => handleMessage(packet, message)
        }
      else
        println(s"Message from ${packet.getAddress}:${packet.getPort} dropped.")
    }
  })

  private def handleMessage(packet: DatagramPacket, message: Message): Unit = {
    val receiver = (packet.getAddress, message.senderPort)
    println(s"Input message: $message")

    message match {
      case m: HelloMessage =>
        if (messagesHistory.add(m.uuid)) {
          neighbors.add((packet.getAddress, m.senderPort))
          println(s"New child: $receiver")
          println("Current neighbors:")
          neighbors.foreach(println)
        }
        outputMessagesHandler.sendMessage(new AcceptHelloMessage(currentPort, m.uuid), receiver, repeat = false)
      case m: TextMessage =>
        if (messagesHistory.add(m.uuid)) {
          println(s"========== Input message text: ${m.message} ==========")
          neighbors.foreach(target =>
            outputMessagesHandler.sendMessage(new TextMessage(m.uuid, currentPort, m.message), target)
          )
        }
        outputMessagesHandler.sendMessage(new AcceptTextMessage(currentPort, m.uuid), receiver, repeat = false)
      case m: AcceptHelloMessage =>
        parent foreach {
          parent =>
            if (parent == receiver) {
              neighbors.add(parent)
              outputMessagesHandler.cancelRepeat(m.acceptedUuid, receiver)
            }
        }
      case m: AcceptTextMessage => outputMessagesHandler.cancelRepeat(m.acceptedUuid, receiver)
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
