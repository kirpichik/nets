package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

object UserInfo extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    if (args.length < 1) {
      state.sendMessageToOwner("Need args: <nickname>")
      return
    }

    state.getUser(args(0)) match {
      case Some(user) => state.sendMessageToOwner(s"User: ${user.nickname}, online: ${user.online}")
      case _ => state.sendMessageToOwner("Unknown user.")
    }
  }
}
