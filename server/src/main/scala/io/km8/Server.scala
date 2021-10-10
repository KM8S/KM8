package io.km8

import zio.logging._
import zio.logging.slf4j._
import zio._

import io.km8.config._
import io.km8.http._

object Server extends App:

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    val app = Web(_.startServer)

    app
      .provideLayer(
        ConfigLive.layer >+>
          Slf4jLogger.make((_, message) => message) >+>
          WebLive.layer
      )
      .exitCode
