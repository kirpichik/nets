package org.polushin.rest_chat.commands

import org.polushin.rest_chat.ChatState

trait ChatCommand {

  def perform(state: ChatState, args: Array[String]): Unit

}
