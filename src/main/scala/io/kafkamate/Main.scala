package io.kafkamate

import com.github.mlangc.slf4zio.api._
import http._
import zio._

object Main extends App with LoggingSupport {

  private lazy val runtimeLayer: ULayer[Has[Runtime[ZEnv]]] = ZLayer.succeed(this)

  def run(args: List[String]): URIO[ZEnv, Int] =
    HttpServer
      .startServer
      .catchAll((e: Throwable) => logger.errorIO(s"Failed running app: ${e.getMessage}", e).as(1))
      .provideLayer(runtimeLayer >>> HttpServer.liveLayer)

}
