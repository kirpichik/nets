package org.polushin.rest_chat

trait ChatProvider {

  /**
   * Отправляет сообщение локальному пользователю.
   *
   * @param msg Сообщение.
   * @param sender Отправитель сообщения.
   */
  def sendMessageToOwner(msg: String, sender: User = SystemUser): Unit = {
    println(s"[$sender]: $msg")
  }

  /**
   * Отправляет сообщение всем пользователям в чате.
   *
   * @param msg Сообщение.
   */
  def sendMessageToChat(msg: String): Unit

  /**
   * @return Набор пользователей в чате.
   */
  def getUsers: Set[String]

  /**
   * Ищет пользователя по его нику.
   *
   * @param nickname Ник пользователя.
   *
   * @return Опциональный пользователь.
   */
  def getUser(nickname: String): Option[UserData]

  /**
   * Останавливает работу сервера или отключает клиента от сервера.
   */
  def shutdown(): Unit
}
