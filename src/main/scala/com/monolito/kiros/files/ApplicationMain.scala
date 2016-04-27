package com.monolito.kiros.files

import scala.concurrent.Future
import scala.concurrent.duration._
import java.io.File
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import scala.io.StdIn
import akka.stream.javadsl._
import akka.http.scaladsl.coding.Deflate
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes.MovedPermanently
import akka.pattern.ask
import akka.util.Timeout
import scala.compat.java8.FutureConverters._

case class Entry(filename: String, modifiedBy: String)

object WebServer {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("my-system")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher

    val route = pathSingleSlash {
        get {
          complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "<h1>Say hello to akka-http</h1>"))
        }
      } ~
      pathPrefix("app") {
        encodeResponse {
          getFromDirectory("app")
        }
      } ~
    path("api/upload") {
      entity(as[Multipart.FormData]) { formData =>
        val allPartsF: Future[Map[String, Any]] = formData.parts.mapAsync[(String, Any)](1) {
          case b: BodyPartEntity if b.name == "file" =>
            val file = File.createTempFile("upload", "tmp")
            toScala(b.entity.dataBytes.runWith(FileIO.toFile(file))).map(_ =>
                (b.name -> file))
            case b: BodyPartEntity =>
              b.toStrict(2.seconds).map(strict =>
                  (b.name -> strict.data.utf8String))
        }.runFold(Map.empty[String, Any])((map, tuple) => map + tuple)
        val done = allPartsF.map { allParts =>
          println(Entry(
            filename = allParts("filename").asInstanceOf[String],
            modifiedBy = allParts("modifiedBy").asInstanceOf[String]))
        }
        onSuccess(allPartsF) { allParts =>
          complete {
            "ok!"
          }
        }
      }
    }

    val (host, port) = ("localhost", 8089)
    val bindingFuture = Http().bindAndHandle(route, host, port)
    println(s"Server online at http://$host:$port/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }
}
