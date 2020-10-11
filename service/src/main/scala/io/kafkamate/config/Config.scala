package io.kafkamate
package config

import zio._

object Config {

  type HasConfig = Has[ConfigProperties]

  case class ConfigProperties(port: String, kafkaHosts: List[String])

  val accessConfig: URIO[HasConfig, ConfigProperties] =
    ZIO.service[ConfigProperties]

  lazy val liveLayer: ULayer[HasConfig] =
    ZLayer.succeed(ConfigProperties("8081", List("localhost:9092")))

}
