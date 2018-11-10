package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

object Users extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit =
    state.sendMessageToOwner(state.getUsers.mkString("\n"))
}
