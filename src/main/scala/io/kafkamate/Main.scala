package io.kafkamate

import com.github.mlangc.slf4zio.api._
import http._
import zio._

object Main extends App with LoggingSupport {

  private lazy val runtimeLayer: ULayer[Has[Runtime[ZEnv]]] = ZLayer.succeed(this)

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    HttpServer
      .startServer
      .provideLayer(runtimeLayer >>> HttpServer.liveLayer)
      .catchAll(e => logger.errorIO(s"Failed running app: ${e.getMessage}", e).as(ExitCode.failure))

}
