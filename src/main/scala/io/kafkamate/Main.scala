package io.kafkamate

import com.github.mlangc.slf4zio.api._
import util._
import zio._

object Main extends App with LoggingSupport {

  val liveApp: URIO[LiveEnv, Int] = {
    val app: RIO[LiveEnv, Int] =
      ZIO.accessM[LiveEnv](_.httpServer.start).as(0)
    app.catchAll((e: Throwable) => logger.errorIO(s"Main error: ${e.getMessage}")).as(1)
  }

  def run(args: List[String]): URIO[ZEnv, Int] =
    liveApp.provide(LiveEnv.Live(this))

}
