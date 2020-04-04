package io.kafkamate

import com.github.mlangc.slf4zio.api._
import http._
import zio._

object Main extends App with LoggingSupport {

  private lazy val runtime: Runtime[ZEnv] = this

  def run(args: List[String]): URIO[ZEnv, Int] =
    HttpServerProvider
      .startServer
      .catchAll((e: Throwable) => logger.errorIO(s"Failed running app: ${e.getMessage}", e).as(1))
      .provideLayer(ZLayer.succeed(runtime) >>> HttpServerProvider.liveLayer)

}
