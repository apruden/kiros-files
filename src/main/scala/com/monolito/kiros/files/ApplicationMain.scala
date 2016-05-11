package com.monolito.kiros.files

import java.io.File

import scala.concurrent.duration.DurationInt
import scala.io.StdIn

import com.monolito.kiros.commons.CorsSupport

import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.Timeout
import com.typesafe.config.ConfigFactory


case class Entry(filename: String, modifiedBy: String)


object WebServer extends App with CorsSupport with SprayJsonSupport {
  import spray.json.DefaultJsonProtocol._
  
  override val corsAllowOrigins: List[String] = List("*")

  override val corsAllowedHeaders: List[String] = List("Origin",
      "X-Requested-With",
      "Content-Type",
      "Accept",
      "Authorization",
      "Accept-Encoding",
      "Accept-Language",
      "Host",
      "Referer",
      "User-Agent")

  override val corsAllowCredentials: Boolean = true

  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20),
    `Access-Control-Allow-Credentials`(corsAllowCredentials))

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(5 seconds)
  val conf = ConfigFactory.load()
  val rootPath = conf.getString("kiros.files.root")

  val fileService = system.actorOf(Props[FileServiceActor], name="FileService")

  val route = pathSingleSlash {
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "kiros-files"))
    }
  } ~
    cors {
      pathPrefix("files") {
        pathEnd {
          get {
            import FileServiceActor._
            onSuccess((fileService ? GetFiles).mapTo[List[String]]) { files =>
              complete { files }
            }
          } ~
            post {
              entity(as[Multipart.FormData]) { formData =>
                val done = formData.parts.mapAsync(parallelism = 4) { bodyPart =>
                  bodyPart.entity.dataBytes.runWith(FileIO.toFile(new File(s"$rootPath/${bodyPart.filename.get}")))
                }.runForeach( _ => ())

                onComplete(done) { res =>
                  complete("ok")
                }
              }
            }
        } ~
        get {
          encodeResponse {
            getFromDirectory(s"$rootPath")
          }
        }
      }
    }

  val (host, port) = (conf.getString("kiros.files.host"), conf.getInt("kiros.files.port"))
  val bindingFuture = Http().bindAndHandle(route, host, port) //, serverContext)
  println(s"Server online at http://$host:$port/ ...")
  sys.addShutdownHook(() => bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate()))
}
