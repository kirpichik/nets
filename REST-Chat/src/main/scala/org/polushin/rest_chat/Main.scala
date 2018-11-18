package org.polushin.rest_chat

import org.polushin.rest_chat.commands._

import scala.io.StdIn

object Main {

  def main(args: Array[String]) {
    val state = new ChatState

    inputLinesStream foreach { line =>
      try {
        handleInputLine(state, line)
      } catch {
        case _: IllegalStateException => state.sendMessageToOwner("No connection, type /help command.")
        case e: Exception => state.sendMessageToOwner("Error received: " + e.getMessage)
      }
    }
  }

  def handleInputLine(state: ChatState, line: String): Unit = {
    if (line.isEmpty)
      return

    if (!line.startsWith("/")) {
      state.sendMessageToChat(line)
      return
    }

    val split = line.split(" ")
    val cmd = split(0).substring(1)
    val args = new Array[String](split.length - 1)
    Array.copy(split, 1, args, 0, args.length)

    cmd.toLowerCase match {
      case "connect" => Connect.perform(state, args)
      case "exit" => Exit.perform(state, args)
      case "listen" => Listen.perform(state, args)
      case "help" => Help.perform(state, args)
      case "users" => Users.perform(state, args)
      case "user-info" => UserInfo.perform(state, args)
      case _ => state.sendMessageToOwner("Unknown command.")
    }
  }

  def inputLinesStream(): Stream[String] = {
    val line = StdIn.readLine()
    if (line == null)
      return Stream.empty[String]
    line #:: inputLinesStream
  }

}
