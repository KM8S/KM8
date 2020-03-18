package io.kafkamate.config

import zio.Task

trait ConfigProvider {
  def configProvider: ConfigProvider.Service
}

object ConfigProvider {
  case class Config(kafkaHost: String)

  trait Service {
    def config: Task[Unit]
  }

  trait LiveConfig extends Service {
    override def config: Task[Unit] = {

      ???
    }
  }
}
