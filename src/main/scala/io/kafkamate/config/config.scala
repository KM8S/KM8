package io.kafkamate

import zio._

package object config {

  type Config = Has[ConfigProperties]

  case class ConfigProperties(port: String, kafkaHosts: List[String])

  val liveLayer: ULayer[Config] =
    ZLayer.succeed(ConfigProperties("8081", List("localhost:9092")))

}
