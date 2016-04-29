package com.monolito.kiros.files

import java.io.File

import scala.compat.java8.FutureConverters.toScala
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.io.StdIn

import com.monolito.kiros.commons.CorsSupport

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.ToResponseMarshallable.apply
import akka.http.scaladsl.model.BodyPartEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.http.scaladsl.server.directives.OnSuccessMagnet.apply
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import javax.net.ssl.SSLContext
import akka.stream.TLSClientAuth
import javax.net.ssl.SSLParameters
import akka.http.scaladsl.HttpsConnectionContext
import javax.net.ssl.KeyManagerFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.io.FileInputStream
import javax.net.ssl.TrustManagerFactory
import akka.util.ByteString
import akka.util.ByteString
import akka.stream.scaladsl.Sink

case class Entry(filename: String, modifiedBy: String)

object WebServer extends App with CorsSupport {
  val serverContext = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(new FileInputStream("keystore.jks"), "Admin123".toCharArray)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    kmf.init(keyStore, "Admin123".toCharArray)
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    tmf.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null)
    new HttpsConnectionContext(context)
  }

  override val corsAllowOrigins: List[String] = List("*")

  override val corsAllowedHeaders: List[String] = List("Origin", "X-Requested-With", "Content-Type", "Accept", "Accept-Encoding", "Accept-Language", "Host", "Referer", "User-Agent")

  override val corsAllowCredentials: Boolean = true

  override val optionsCorsHeaders: List[HttpHeader] = List[HttpHeader](
    `Access-Control-Allow-Headers`(corsAllowedHeaders.mkString(", ")),
    `Access-Control-Max-Age`(60 * 60 * 24 * 20), // cache pre-flight response for 20 days
    `Access-Control-Allow-Credentials`(corsAllowCredentials))

  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  // needed for the future flatMap/onComplete in the end
  implicit val executionContext = system.dispatcher

  val route = pathSingleSlash {
    get {
      complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, "kiros-files"))
    }
  } ~
    cors {
      pathPrefix("files") {
        pathEnd {
          get {
            complete { "test " }
          } ~
            post {
              entity(as[Multipart.FormData]) { formData =>
                val done = formData.parts.mapAsync(parallelism = 4) { bodyPart =>
                  bodyPart.entity.dataBytes.runWith(FileIO.toFile(new File("totoakka")))
                }.runForeach( _ => ())
                
                onComplete(done) { res =>
                  complete("ok")
                }
              }
            
              /*entity(as[Multipart.FormData]) { formData =>
                println(">>>>xxx")
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
                    "ok"
                  }
                }
              }*/
            }
        } ~ 
        get {
          encodeResponse {
            println(">>>yyy")
            getFromDirectory("data")
          }
        }
      }
    }

  val (host, port) = ("localhost", 8089)
  val bindingFuture = Http().bindAndHandle(route, host, port) //, serverContext)
  println(s"Server online at https://$host:$port/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}
