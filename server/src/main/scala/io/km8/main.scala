package io.km8
import zio.*

object main extends App {

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val io = Web.startServer()

    io.provideLayer(layers.appLayer).exitCode
  }
}
