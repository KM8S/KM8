package io.kafkamate

import api._
import config._
import com.twitter.finagle.{Http, ListeningServer}
import zio.interop.twitter._
import zio._

package object http {
  type HttpServerProvider = Has[HttpServerProvider.Service]

  object HttpServerProvider {

    trait Service {
      def start: Task[Int]
    }

    def startServer: RIO[HttpServerProvider, Int] =
      ZIO.accessM[HttpServerProvider](_.get.start)

    private[http] val httpLayer: URLayer[ApiProvider with ConfigProvider, HttpServerProvider] =
      ZLayer.fromServices[ApiProvider.Service, ConfigProvider.Service, HttpServerProvider.Service] { (apiProvider, configProvider) =>
        new Service {
          def start: Task[Int] = {
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
      ApiProvider.liveLayer ++ ConfigProvider.live >>> httpLayer
  }
}
