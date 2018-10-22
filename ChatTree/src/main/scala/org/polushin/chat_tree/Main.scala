package org.polushin.chat_tree

import java.net.InetAddress

object Main {

  def main(args: Array[String]): Unit = {
    if (args.length == 3 || args.length < 2) {
      println("Usage: <current-port> <drop-change> [parent-ip] [parent-port]")
      System.exit(-1)
    } else if (args.length == 2)
      new ChatTree(currentPort = Integer.parseInt(args(0)), dropChance = Integer.parseInt(args(1))).start()
    else {
      val parent = Option((InetAddress.getByName(args(2)), Integer.parseInt(args(3))))
      new ChatTree(parent, Integer.parseInt(args(0)), Integer.parseInt(args(1))).start()
    }
  }

}
