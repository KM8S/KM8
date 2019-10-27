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
    def server: ZManaged[Env, Throwable, ListeningServer]
  }

  trait LiveHttpServer extends HttpServerProvider {
    def httpServer: Service = new Service {
      def server: ZManaged[Env, Throwable, ListeningServer] = {
        val acquire =
          ZIO.access[Env](_.apiProvider.api) >>= (s => ZIO(Http.server.serve(":8081", s)))
        def release(s: ListeningServer): UIO[Unit] =
          ZIO
            .fromFuture(_ => s.close())
            .catchAll((e: Throwable) => UIO(println(e.getMessage)))
        ZManaged.make(acquire)(release)
      }
    }
  }

}
