package io.kafkamate

import util._
import zio._

object Main extends App {

  val liveApp: URIO[LiveEnv, Int] = {
    val app: RIO[LiveEnv, Int] = for {
      fiber <- ZIO.accessM[LiveEnv](_.httpServer.start)
      _ <- fiber.join
    } yield 0
    app.catchAll((e: Throwable) => UIO(println(s"Main error: ${e.getMessage}")).as(1))
  }

  def run(args: List[String]): URIO[ZEnv, Int] =
    liveApp.provide(LiveEnv.Live(this))

}
