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
      case (msg, _) =>
        if (msg._2 == target && msg._1.uuid == uuid)
          repeatingMessages.remove(msg)
    }
    println(s"Cancelled repeating: uuid = $uuid to $target")
  }

  private val senderThread = new Thread(() =>
    while (true) {
      val output = sendQueue.take()
      val msg = output._1
      msg._1.send(socket, msg._2)
      println(s"Message sent: ${msg._1}")

      if (output._2)
        repeatingMessages.put(msg, 0)
    }
  )

  private val repeaterThread = new Thread(() => {
    while (true) {
      TimeUnit.SECONDS.sleep(OutputMessagesHandler.REPEAT_TRY_DELAY)

      repeatingMessages foreach {
        case (msg, count) =>
          if (count >= OutputMessagesHandler.MAX_REPEAT_TRY) {
            println(s"Message resend tries to ${msg._2} expired: ${msg._1}")
            repeatingMessages.remove(msg)
            handler(msg._2)
          } else {
            msg._1.send(socket, msg._2)
            println(s"Message resent (${count + 1}): ${msg._1} to target ${msg._2}")
            repeatingMessages.update(msg, count + 1)
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
