package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

object Help extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    state.sendMessageToOwner(
      """Commands list:
        |/connect <host> <port> - Create connection to chat server
        |/listen <port> - Create chat server
        |/exit - Disconnect from char server or stop server hosting
        |/help - This list
        |/users - List of users in chat
      """.stripMargin)
  }
}
