package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

object Exit extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    state.shutdown()
    state.sendMessageToOwner("Connection shutdown.")
  }
}
