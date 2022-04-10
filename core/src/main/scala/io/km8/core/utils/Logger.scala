package io.km8.core
package utils

import zio.URLayer
import zio.clock.Clock
import zio.console.Console
import zio.logging.*

object Logger {

  lazy val liveLayer: URLayer[Console with Clock, Logging] =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("kafkamate")

}
