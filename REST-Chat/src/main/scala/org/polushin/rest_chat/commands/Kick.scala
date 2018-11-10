package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

object Kick extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    if (args.length < 1) {
      state.sendMessageToOwner("Need args: <username>")
      return
    }

    state.getUser(args(0)) match {
      case Some(user) =>
        if (state.kick(user))
          state.sendMessageToOwner(s"User ${args(0)} kicked.")
        else
          state.sendMessageToOwner(s"Cannot kick ${args(0)}. You are not admin of this chat.")
      case None => state.sendMessageToOwner(s"Cannot find user \"${args(0)}\".")
    }
  }
}
