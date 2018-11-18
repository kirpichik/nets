package org.polushin

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

package object rest_chat {

  final case class InputMessage(text: String)

  final case class OutputMessage(text: String, id: Int, sender: String)
  final case class Message(user: User, text: String, date: Long, id: Int)

  object SystemUser extends User("System", new UUID(0, 0))
  object AdminUser extends User("Admin", new UUID(0, 1))

  final case class UserData(nickname: String, online: Boolean)

  final case class UserAccess(nickname: String, token: String)

  trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    implicit val userFormat: RootJsonFormat[UserData] = jsonFormat2(UserData)
    implicit val inputMessageFormat: RootJsonFormat[InputMessage] = jsonFormat1(InputMessage)
    implicit val outputMessageFormat: RootJsonFormat[OutputMessage] = jsonFormat3(OutputMessage)
    implicit val userAccessFormat: RootJsonFormat[UserAccess] = jsonFormat2(UserAccess)
  }

}
