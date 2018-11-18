package org.polushin.rest_chat

import java.util.UUID

class User(name: String, id: UUID) {

  def this(name: String) = {
    this(name, UUID.randomUUID())
    updateActivity()
  }

  val username: String = name
  val uuid: UUID = id

  private var lastActivity: Long = -1
  private var leave: Boolean = false

  /**
   * @return Время последней активности пользователя.
   */
  def getLastActivity: Long = lastActivity

  /**
   * @return { @code true} если пользователь отвалился по таймауту.
   */
  def isTimedOut: Boolean = User.ACTIVITY_TIMEOUT < System.currentTimeMillis() - lastActivity

  /**
   * Обновляет время последней активности пользователя.
   *
   * @return { @code true} если до обновления активности пользователь считался отвалившимся.
   */
  def updateActivity(): Boolean = {
    lastActivity = System.currentTimeMillis()
    if (leave) {
      leave = false
      true
    } else
      false
  }

  /**
   * Если пользователь давно не был активен, отмечает его как отвалившегося.
   *
   * @return { @code true} если в результате проверки, пользователь был отмечен отвалившимся.
   */
  def markAsLeave(): Boolean = {
    if (!leave && isTimedOut) {
      leave = true
      true
    } else
      false
  }

  def toUserData: UserData = UserData(name, !isTimedOut)

  override def toString: String = username
}

object User {
  val ACTIVITY_TIMEOUT: Long = 30 * 1000

  def fromUserAccess(user: UserAccess): User = new User(user.nickname, UUID.fromString(user.token))
}
