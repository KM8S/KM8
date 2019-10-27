package io.kafkamate

import util._
import zio._

object Main extends App {

  val liveApp: URIO[LiveEnv, Int] = {
    val zio: RIO[LiveEnv, Int] = for {
      s <- ZIO.access[LiveEnv](_.httpServer.server)
      _ <- s.use(_ => ZIO.never)
    } yield 0
    zio.catchAll((e: Throwable) => UIO(println(s"Main error: ${e.getMessage}")).as(1))
  }

  def run(args: List[String]): URIO[ZEnv, Int] =
    ZIO.runtime[ZEnv] >>= (rtm => liveApp.provide(LiveEnv.Live(rtm)))

}
