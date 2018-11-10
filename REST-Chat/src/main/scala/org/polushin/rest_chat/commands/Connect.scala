package org.polushin.rest_chat.commands

import java.io.IOException
import java.net.{InetAddress, UnknownHostException}

import org.polushin.rest_chat.ChatState

object Connect extends ChatCommand {

  override def perform(state: ChatState, args: Array[String]): Unit = {
    if (args.length < 2) {
      state.sendMessageToOwner("Need args: <address> <port>")
      return
    }

    try {
      val port = args(1).toInt
      val address = InetAddress.getByName(args(0))
      state.connectServer(address, port)
      state.sendMessageToOwner("Connection established.")
    } catch {
      case _: NumberFormatException => state.sendMessageToOwner("Wrong port format.")
      case _: UnknownHostException => state.sendMessageToOwner("Cannot resolve address.")
      case _: IOException => state.sendMessageToOwner("Cannot connect to server.")
    }
  }
}
