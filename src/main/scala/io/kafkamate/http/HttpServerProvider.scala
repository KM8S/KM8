package io.kafkamate
package http

import api.ApiProvider
import com.twitter.finagle.{Http, ListeningServer}
import zio.interop.twitter._
import zio._

trait HttpServerProvider {
  def httpServer: HttpServerProvider.Service
}

object HttpServerProvider {

  trait Env extends ApiProvider

  trait Service {
    def start: RIO[Env, Unit]
  }

  trait LiveHttpServer extends HttpServerProvider {
    def httpServer: Service = new Service {
      def start: RIO[Env, Unit] = {
        val acquire =
          for {
            api <- ZIO.access[Env](_.apiProvider.api)
            server <- ZIO(Http.server.withHttp2.serve(":8081", api))
          } yield server

        def release(s: ListeningServer): UIO[Unit] =
          Task.fromTwitterFuture(Task(s.close())).orDie

        ZManaged.make(acquire)(release).useForever
      }
    }
  }

}
