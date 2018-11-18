package org.polushin.rest_chat

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util.{Failure, Success}

class ClientChatProvider(nick: String, address: InetAddress, port: Int) extends ChatProvider with JsonSupport {

  private val uri: Uri = Uri(s"http://${address.getHostAddress}:$port/")

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  @volatile
  private var lastMessageId: Int = -1

  private val initialRequest = Http().singleRequest(HttpRequest(HttpMethods.POST, uri + s"login?nickname=$nick"))

  private val user: User = Unmarshal(Await.result(initialRequest, Duration.Inf)).to[UserAccess].value match {
    case Some(data) => User.fromUserAccess(data.get)
    case _ => throw new RuntimeException("Wrong server response")
  }

  private val tokenHeaders = List(RawHeader("Authorization", s"Token ${user.uuid}"))

  override def sendMessageToChat(msg: String): Unit = {
    val request = Marshal(InputMessage(msg)).to[RequestEntity] flatMap { entity =>
      Http().singleRequest(HttpRequest(HttpMethods.POST, uri + "messages/send", tokenHeaders, entity))
    }

    request.onComplete {
      case Success(_) => sendMessageToOwner(msg, user)
      case Failure(exception) => sendMessageToOwner("Cannot send message: " + exception.getMessage)
    }
  }

  override def getUsers: Set[String] = {
    val request = Http().singleRequest(HttpRequest(HttpMethods.GET, uri + "users", tokenHeaders))
    Unmarshal(Await.result(request, Duration.Inf)).to[List[UserData]].value match {
      case Some(data) => data.get.map(data => data.nickname).toSet
      case _ => throw new RuntimeException("Wrong server response")
    }
  }

  override def getUser(nickname: String): Option[UserData] = {
    val request = Http().singleRequest(HttpRequest(HttpMethods.GET, uri + "users/" + nickname, tokenHeaders))
    val response = Await.result(request, Duration.Inf)
    if (response.status != StatusCodes.OK)
      return None
    val result = Unmarshal(response).to[UserData]
    Option(Await.result(result, Duration.Inf))
  }

  override def shutdown(): Unit = {
    Http().singleRequest(HttpRequest(HttpMethods.POST, uri + "logout", tokenHeaders))
  }
}
