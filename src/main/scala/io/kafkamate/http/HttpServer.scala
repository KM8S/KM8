package io.kafkamate
package http

import api._
import config._
import com.github.mlangc.slf4zio.api._
import com.twitter.finagle.{Http, ListeningServer}
import zio.interop.twitter._
import zio._
import zio.macros.accessible

@accessible object HttpServer {
  type HttpServerProvider = Has[Service]

  trait Service {
    def startServer: Task[ExitCode]
  }

  private [http] val httpURLayer: URLayer[ApiProvider with Config, HttpServerProvider] =
    ZLayer.fromServices[ApiProvider.Service, ConfigProperties, HttpServer.Service] { (apiProvider, config) =>
      new Service with LoggingSupport {
        def startServer: Task[ExitCode] = {
          val acquire: Task[ListeningServer] =
            Task(Http.server.withHttp2.serve(s":${config.port}", apiProvider.api))

          def release(s: ListeningServer): UIO[Unit] =
            Task.fromTwitterFuture(s.close()).tapError(e => logger.errorIO(e.getMessage, e)).orDie

          ZManaged.make(acquire)(release).useForever.as(ExitCode.success)
        }
      }
    }

  val liveLayer: URLayer[RuntimeProvider, HttpServerProvider] =
    ApiProvider.liveLayer ++ config.liveLayer >>> httpURLayer
}
