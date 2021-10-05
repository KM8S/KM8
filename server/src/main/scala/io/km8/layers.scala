package io.km8
import zio.*

object layers:
  val appLayer: ZLayer[Any, Throwable, Has[Web]] = ConfigLive.layer >>> WebLive.layer
