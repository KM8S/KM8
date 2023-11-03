package io.kafkamate
package utils

import zio.URLayer
import zio.clock.Clock
import zio.console.Console
import zio.logging._

object Logger {

  lazy val liveLayer: URLayer[Console with Clock, Logging] =
    // Slf4jLogger.make((_, message) => message)
    Logging.console(
      logLevel = LogLevel.Debug,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("kafkamate")

}
