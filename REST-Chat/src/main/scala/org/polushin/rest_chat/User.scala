package org.polushin.rest_chat

import java.util.UUID

case class User(name: String) {

  val username: String = name
  val uuid: UUID = UUID.randomUUID()

  override def toString: String = username
}

case object SystemUser extends User("System")

case object AdminUser extends User("Admin")
