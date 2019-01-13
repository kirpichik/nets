package org.polushin.rest_chat

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import spray.json.{JsObject, JsString}

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class ServerChatProvider(port: Int) extends ChatProvider with JsonSupport {

  private val messagesHistory = new ArrayBuffer[Message]()
  private val users = new TrieMap[UUID, User]()
  private val usersNicknames = new TrieMap[String, User]()

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val route = (path("login") & post & parameter("nickname")) { nickname =>
    restLogin(nickname)
  } ~ optionalHeaderValueByName("Authorization") { auth =>
    (path("logout") & post) {
      checkRequestToken(auth, restLogout)
    } ~ (path("users") & get) {
      checkRequestToken(auth, restUsers)
    } ~ (path("users" / Remaining) & get) { nickname =>
      checkRequestToken(auth, restUserByNick(_: User, nickname))
    } ~ pathPrefix("messages") {
      (path("send") & post & entity(as[InputMessage])) { message =>
        checkRequestToken(auth, restMessageSend(_: User, message))
      } ~ (path("get") & get & parameters("offset".as[Int].?, "count".as[Int].?)) { (offset, count) =>
        checkRequestToken(auth, restMessagesGet(_: User, offset, count))
      } ~ (path("listen") & extractUpgradeToWebSocket) { upgrade =>
        checkRequestToken(auth, restMessagesWebSocket(_: User, upgrade))
      }
    }
  }

  /**
   * Исполняет REST-метод login, который регистрирует пользователя в системе.
   *
   * @param nickname Предпочитаемый ник пользователя.
   */
  private def restLogin(nickname: String): Route = {
    if (nickname == AdminUser.username || usersNicknames.contains(nickname)) {
      respondWithHeader(RawHeader("WWW-Authenticate", "Token realm='Username is already in use'")) {
        complete(StatusCodes.Unauthorized, "Username is already in use")
      }
    } else {
      val user = new User(nickname)
      usersNicknames.put(nickname, user)
      users.put(user.uuid, user)
      broadcastMessage(s"${user.username} join chat.")
      complete(UserAccess(nickname, user.uuid.toString))
    }
  }

  /**
   * Исполняет REST-метод logout, который удаляет пользователя из системы.
   *
   * @param user Удаляемый пользователь.
   */
  private def restLogout(user: User): Route = {
    usersNicknames.remove(user.username)
    users.remove(user.uuid)
    broadcastMessage(s"${user.username} left chat.")
    complete {
      JsObject(
        "message" -> JsString("bye!"),
      )
    }
  }

  /**
   * Исполняет REST-метод users без параметра, который отображает список всех пользователей.
   */
  private def restUsers(requester: User): Route = complete(users.values
    .map(user => user.toUserData).toList)

  /**
   * Исполняет REST-метод users с параметром, который отображает информацию о конкретном пользователе.
   *
   * @param nickname Ник запрашиваемого пользователя.
   */
  private def restUserByNick(requester: User, nickname: String): Route = usersNicknames.get(nickname) match {
    case Some(user) => complete(user.toUserData)
    case _ => complete(StatusCodes.NotFound, "Unknown user")
  }

  /**
   * Исполняет REST-метод messages/listen, который позволяет создать WebSocket для
   * мгновенного получения и отправки сообщений.
   *
   * @param upgrade Состояние WebSocket-а.
   */
  private def restMessagesWebSocket(user: User, upgrade: UpgradeToWebSocket): Route = {
    complete(upgrade.handleMessagesWithSinkSource(Sink foreach {
      case tm: TextMessage.Strict =>
        if (user.updateActivity())
          broadcastMessage(s"${user.username} back online.")
        broadcastMessage(tm.text, user)
      case tm: TextMessage.Streamed =>
        tm.textStream.runWith(Sink.ignore)
        Nil
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }, Source.fromIterator(() => Iterator.continually(
      messagesHistory.synchronized {
        val prevSize = messagesHistory.size()
        while (messagesHistory.size() == prevSize)
          messagesHistory.wait()
        val msg = messagesHistory.last
        TextMessage.Strict(outputMessageFormat.write(OutputMessage(msg.text, msg.id, msg.user.username)).compactPrint)
      }
    ))))
  }

  /**
   * Исполняет REST-метод messages/send, который отправляет сообщение в чат.
   *
   * @param user Отправитель сообщения.
   * @param message Сообщение.
   */
  private def restMessageSend(user: User, message: InputMessage): Route = {
    val id = broadcastMessage(message.text, user)
    complete(OutputMessage(message.text, id, user.username))
  }

  /**
   * Исполняет REST-метод messages/get, который позволяет получить историю сообщений.
   *
   * @param offset Смещение от конца истории.
   * @param count Количество сообщений.
   */
  private def restMessagesGet(user: User, offset: Option[Int], count: Option[Int]): Route = {
    val offsetValue = offset match {
      case Some(value) => value
      case _ => 0
    }
    val countValue = count match {
      case Some(value) => value
      case _ => 10
    }

    if (countValue >= 100 || countValue < 1)
      complete(StatusCodes.BadRequest, "Count must be > 0 and <= 100")
    else if (offsetValue < 0)
      complete(StatusCodes.BadRequest, "Offset must be > 0")
    else
      messagesHistory.synchronized {
        complete(messagesHistory
          .reverse
          .drop(offsetValue)
          .map(msg => OutputMessage(msg.text, msg.id, msg.user.username))
          .take(countValue).toList)
      }
  }

  /**
   * Подготавливает токен из заголовков запроса, преобразуя его в UUID,
   * ищет пользователя с данным токеном и в случае успеха исполняет переданную
   * функцию.
   *
   * @param auth Опциональное значение токена аутентификации из запроса.
   * @param executor Исполняемая в случае успеха функция.
   */
  private def checkRequestToken(auth: Option[String], executor: User => Route): Route = {
    auth match {
      case Some(value) => getUserByHeaderValue(value) match {
        case Some(user) =>
          if (user.updateActivity())
            broadcastMessage(s"${user.username} back online.")
          executor(user)
        case _ => complete(StatusCodes.Forbidden, "Unknown user token")
      }
      case _ => complete(StatusCodes.Unauthorized, "No user token provided")
    }
  }

  /**
   * Ищет пользователя в системе по значению токена из заголовков запроса.
   *
   * @param auth Значение токена.
   *
   * @return Опциональный пользователь.
   */
  private def getUserByHeaderValue(auth: String): Option[User] = {
    if (!auth.startsWith("Token ")) return None

    try {
      getUser(UUID.fromString(auth.substring("Token ".length)))
    } catch {
      case _: IllegalArgumentException => None
    }
  }

  /**
   * Отправляет сообщение всем пользователям в чате.
   *
   * @param msg Сообщение.
   * @param user Отправитель сообщения.
   *
   * @return ID сообщения.
   */
  private def broadcastMessage(msg: String, user: User = SystemUser): Int = {
    sendMessageToOwner(msg, user)
    messagesHistory.synchronized {
      messagesHistory += Message(user, msg, System.currentTimeMillis(), messagesHistory.length)
      messagesHistory.notifyAll()
      messagesHistory.length - 1
    }
  }

  /**
   * Ищет пользователя по его UUID.
   *
   * @param uuid Токен UUID пользователя.
   *
   * @return Опциональный пользователь.
   */
  private def getUser(uuid: UUID): Option[User] = users.get(uuid)

  private val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", port)

  try {
    Await.result(bindingFuture, Duration.Inf)
  } catch {
    case e: Throwable => shutdown(); throw e
  }

  @volatile
  private var running = true

  /**
   * Поток для обновления статусов пользователей по таймауту.
   */
  new Thread(() => {
    while (running) {
      TimeUnit.SECONDS.sleep(ServerChatProvider.USERS_TIMINGS_UPDATE_DELAY)

      users.values foreach { user =>
        if (user.markAsLeave())
          broadcastMessage(s"${user.username} reached activity timeout.")
      }
    }
  }).start()

  override def sendMessageToChat(msg: String): Unit = broadcastMessage(msg, AdminUser)

  override def getUsers: Set[String] = usersNicknames.keySet.toSet

  override def getUser(nickname: String): Option[UserData] = usersNicknames.get(nickname) match {
    case Some(user) => Option(user.toUserData)
    case _ => None
  }

  override def shutdown(): Unit = {
    running = false
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}

object ServerChatProvider {
  val USERS_TIMINGS_UPDATE_DELAY: Int = 1
}
