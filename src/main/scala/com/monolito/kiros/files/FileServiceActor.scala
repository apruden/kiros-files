package com.monolito.kiros.files

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import java.io.File
import com.typesafe.config.ConfigFactory


class FileServiceActor extends Actor with ActorLogging {
  import FileServiceActor._

  val conf = ConfigFactory.load()
  val rootPath = conf.getString("kiros.files.root")
  
  def receive = {
    case GetFiles =>
      log.info("Get file list")
      sender() ! getFiles
    case _ =>
      println("Unknown message")
  }

  def getFiles: List[String] = {
    val d = new File(rootPath)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).map { _.getName }toList
    } else {
      List()
    }
  }
}

object FileServiceActor {
  val props  = Props[FileServiceActor]
  case object GetFiles
}