package org.polushin.rest_chat.commands

import java.io.IOException

import org.polushin.rest_chat.ChatState

object Listen extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    if (args.length < 1) {
      state.sendMessageToOwner("Need args: <port>")
      return
    }

    try {
      val port = args(0).toInt
      if (state.bindServer(port))
        state.sendMessageToOwner(s"Server started at $port.")
    } catch {
      case _: NumberFormatException => state.sendMessageToOwner("Wrong port format.")
      case _: IOException => state.sendMessageToOwner("Cannot connect to server.")
    }
  }
}
