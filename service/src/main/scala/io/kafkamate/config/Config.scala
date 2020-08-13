package io.kafkamate
package config

import zio.{Has, ULayer, URLayer, ZLayer}

object Config {

  type Config = Has[ConfigProperties]

  case class ConfigProperties(port: String, kafkaHosts: List[String])

  def config: URLayer[Config, Config] =
    ZLayer.service[ConfigProperties]

  lazy val liveLayer: ULayer[Config] =
    ZLayer.succeed(ConfigProperties("8081", List("localhost:9092")))

}
