package org.polushin.chat_tree

import java.net.DatagramSocket
import java.util.UUID
import java.util.concurrent.{LinkedBlockingQueue, TimeUnit}

import org.polushin.chat_tree.messages.Message

class OutputMessagesHandler(handler: MessageTarget => Unit) {

  type OutputMessage = (Message, MessageTarget)

  private val socket = new DatagramSocket

  private val sendQueue = new LinkedBlockingQueue[(OutputMessage, Boolean)]
  private val repeatingMessages = new collection.concurrent.TrieMap[OutputMessage, Int]

  /**
   * Добавляет сообщение в очередь на отправку.
   *
   * @param message Новое сообщение.
   * @param target Получатель сообщения.
   * @param repeat Требуется ли повторять отправку сообщения.
   */
  def sendMessage(message: Message, target: MessageTarget, repeat: Boolean = true): Unit = {
    println(s"Queuing message: $message, repeat = $repeat, target = $target")
    sendQueue.add(((message, target), repeat))
  }

  /**
   * Отменяет повторные попытки отправки сообщений.
   *
   * @param uuid UUID сообщения.
   * @param target Получатель сообщения.
   */
  def cancelRepeat(uuid: UUID, target: MessageTarget): Unit = {
    repeatingMessages foreach {
      case (outputMessage, _) =>
        val (message, checkTarget) = outputMessage
        if (message.uuid == uuid && checkTarget == target)
          repeatingMessages.remove(outputMessage)
    }
    println(s"Cancelled repeating: uuid = $uuid to $target")
  }

  private val senderThread = new Thread(() =>
    while (true) {
      val (outputMessage, repeat) = sendQueue.take()
      val (message, target) = outputMessage
      message.send(socket, target)
      println(s"Message sent: $message")

      if (repeat)
        repeatingMessages.put(outputMessage, 0)
    }
  )

  private val repeaterThread = new Thread(() => {
    while (true) {
      TimeUnit.SECONDS.sleep(OutputMessagesHandler.REPEAT_TRY_DELAY)

      repeatingMessages foreach {
        case (outputMessage, count) =>
          val (message, target) = outputMessage
          if (count >= OutputMessagesHandler.MAX_REPEAT_TRY) {
            println(s"Message resend tries to $target expired: $message")
            repeatingMessages.remove(outputMessage)
            handler(target)
          } else {
            message.send(socket, target)
            println(s"Message resent (${count + 1}): $message to target $target")
            repeatingMessages.update(outputMessage, count + 1)
          }
      }
    }
  })

  repeaterThread.setDaemon(true)
  senderThread.setDaemon(true)
  repeaterThread.start()
  senderThread.start()
}

object OutputMessagesHandler {

  val MAX_REPEAT_TRY: Int = 10
  val REPEAT_TRY_DELAY: Int = 5

}
