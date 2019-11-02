package io.kafkamate
package http

import api.ApiProvider
import com.twitter.finagle.{Http, ListeningServer}
import util.implicits._
import zio._

trait HttpServerProvider {
  def httpServer: HttpServerProvider.Service
}

object HttpServerProvider {

  trait Env extends ApiProvider

  trait Service {
    def start: URIO[Env, Fiber[Throwable, Unit]]
  }

  trait LiveHttpServer extends HttpServerProvider {
    def httpServer: Service = new Service {

      def start: URIO[Env, Fiber[Throwable, Unit]] = {
        val acquire =
          for {
            api <- ZIO.access[Env](_.apiProvider.api)
            server <- ZIO(Http.server.serve(":8081", api))
          } yield server

        def release(s: ListeningServer): UIO[Unit] =
          ZIO.fromFuture(_ => s.close()).orDie

        ZManaged.make(acquire)(release).use(_ => ZIO.never.unit).fork
      }

    }
  }

}
