package io.kafkamate
package http

import api._
import config._
import com.twitter.finagle.{Http, ListeningServer}
import zio.interop.twitter._
import zio._
//import zio.macros.accessible

/*@accessible*/ object HttpServer {
  type HttpServerProvider = Has[Service]

  trait Service {
    def startServer: Task[Int]
  }

  def startServer: RIO[HttpServerProvider, Int] =
    ZIO.accessM(_.get.startServer)

  private [http] val httpURLayer: URLayer[ApiProvider with ConfigProvider, HttpServerProvider] =
    ZLayer.fromServices[ApiProvider.Service, ConfigProvider.Service, HttpServer.Service] { (apiProvider, configProvider) =>
      new Service {
        def startServer: Task[Int] = {
          val acquire: Task[ListeningServer] =
            for {
              c <- configProvider.config
              r <- ZIO(Http.server.withHttp2.serve(s":${c.port}", apiProvider.api))
            } yield r

          def release(s: ListeningServer): UIO[Unit] =
            Task.fromTwitterFuture(Task(s.close())).orDie

          ZManaged.make(acquire)(release).useForever.as(0)
        }
      }
    }

  val liveLayer: URLayer[RuntimeProvider, HttpServerProvider] =
    ApiProvider.liveLayer ++ ConfigProvider.liveLayer >>> httpURLayer
}
