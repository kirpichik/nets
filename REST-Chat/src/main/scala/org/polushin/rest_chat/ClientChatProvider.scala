package org.polushin.rest_chat

import java.net.InetAddress

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage, WebSocketRequest, Message => WsMessage}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class ClientChatProvider(nick: String, address: InetAddress, port: Int) extends ChatProvider with JsonSupport {

  private val uri: Uri = Uri(s"http://${address.getHostAddress}:$port/")

  private implicit val system: ActorSystem = ActorSystem()
  private implicit val materializer: ActorMaterializer = ActorMaterializer()
  private implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val initialRequest = Http().singleRequest(HttpRequest(HttpMethods.POST, uri + s"login?nickname=$nick"))

  private val user: User = Unmarshal(Await.result(initialRequest, Duration.Inf)).to[UserAccess].value match {
    case Some(data) => User.fromUserAccess(data.get)
    case _ => throw new RuntimeException("Wrong server response")
  }

  private val tokenHeaders = List(RawHeader("Authorization", s"Token ${user.uuid}"))

  {
    val request = Http().singleRequest(HttpRequest(HttpMethods.GET, uri + "messages/get", tokenHeaders))
    try {
      val response = Await.result(request, Duration.Inf)

      if (response.status != StatusCodes.OK)
        sendMessageToOwner("Cannot receive old messages from server.")
      else {
        val messages = Await.result(Unmarshal(response).to[List[OutputMessage]], Duration.Inf)
        messages.reverse foreach { message => sendMessageToOwner(message.text, new User(message.sender)) }
      }
    } catch {
      case e: Exception =>
        sendMessageToOwner("Cannot receive old messages from server because of: " + e.getMessage)
    }
  }

  private val printSink: Sink[WsMessage, Future[Done]] =
    Sink.foreach {
      case message: TextMessage.Strict =>
        println(message.text)
      case tm: TextMessage.Streamed =>
        tm.textStream.runWith(Sink.ignore)
        Nil
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

  private val flow: Flow[WsMessage, WsMessage, Future[Done]] =
    Flow.fromSinkAndSourceMat(printSink, Source.empty)(Keep.left)

  private val (upgradeResponse, closed) =
    Http().singleWebSocketRequest(WebSocketRequest(s"ws://${address.getHostAddress}:$port/messages/listen",
      extraHeaders = tokenHeaders), flow)

  private val connected = upgradeResponse.map { upgrade =>
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  connected.onComplete(println)
  closed.foreach(_ => println("closed"))

  override def sendMessageToChat(msg: String): Unit = {
    val request = Marshal(InputMessage(msg)).to[RequestEntity] flatMap { entity =>
      Http().singleRequest(HttpRequest(HttpMethods.POST, uri + "messages/send", tokenHeaders, entity))
    }

    request.onComplete {
      case Success(_) =>
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
