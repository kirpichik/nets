package org.polushin

import java.net.InetAddress
import java.util
import java.util.Collections

import org.polushin.chat_tree.messages.Message

import scala.collection.JavaConverters._
import scala.collection.mutable

package object chat_tree {

  type MessageTarget = (InetAddress, Int)
  type MessageType = Class[_ <: Message]

  def createFixedSizeSet[T](queueSize: Int): mutable.Set[T] = {
    Collections.newSetFromMap(new util.LinkedHashMap[T, java.lang.Boolean]() {
      override def removeEldestEntry(eldest: util.Map.Entry[T, java.lang.Boolean]): Boolean = {
        this.size() > queueSize
      }
    }).asScala
  }

  def createConcurrentHashSet[T](): mutable.Set[T] = {
    java.util.Collections.newSetFromMap(
      new java.util.concurrent.ConcurrentHashMap[T, java.lang.Boolean]).asScala
  }

}
