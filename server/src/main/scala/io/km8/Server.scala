package io.km8

import zio.logging._
import zio.logging.slf4j.Slf4jLogger
import zio._

object Server extends App:

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    val app = Web(_.startServer)

    app
      .inject(
        ConfigLive.layer,
        WebLive.layer,
        Slf4jLogger.make((_, message) => message)
      )
      .exitCode
