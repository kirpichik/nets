package org.polushin.chat_tree

import java.net.DatagramSocket
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue}

import org.polushin.chat_tree.messages.Message

class OutputMessagesHandler(handler: MessageTarget => Unit) {

  type OutputMessage = (Message, MessageTarget)

  private val socket = new DatagramSocket

  private val sendQueue = new LinkedBlockingQueue[(OutputMessage, Boolean)]
  private val repeatingMessages = new ConcurrentHashMap[OutputMessage, Int]

  repeaterThread.setDaemon(true)
  senderThread.setDaemon(true)
  repeaterThread.start()
  senderThread.start()

  /**
   * Добавляет сообщение в очередь на отправку.
   *
   * @param message Новое сообщение.
   * @param target Получатель сообщения.
   * @param repeat Требуется ли повторять отправку сообщения.
   */
  def sendMessage(message: Message, target: MessageTarget, repeat: Boolean = true): Unit = {
    sendQueue.add(((message, target), repeat))
  }

  /**
   * Отменяет повторные попытки отправки сообщений.
   *
   * @param msgType Тип сообщения.
   * @param uuid UUID сообщения.
   */
  def cancelRepeatByType(msgType: MessageType, uuid: UUID): Unit = {
    // TODO - concurrent
    repeatingMessages.entrySet().removeIf(entry => entry.getKey._1.getClass == msgType && entry.getKey._1.uuid == uuid)
  }

  private val senderThread = new Thread(() =>
    while (true) {
      val output = sendQueue.take()
      val msg = output._1
      msg._1.send(socket, msg._2)

      if (output._2)
        repeatingMessages.put(msg, 0)
    }
  )

  private val repeaterThread = new Thread(() => {
    // TODO - concurrent
  })
}

object OutputMessagesHandler {

  val MAX_REPEAT_TRY: Int = 10
  val REPEAT_TRY_DELAY: Int = 5

}
