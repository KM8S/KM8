package io.kafkamate

import zio._

package object config {

  type ConfigProvider = Has[ConfigProvider.Service]

  object ConfigProvider {

    case class Config(port: String, kafkaHosts: List[String])

    trait Service {
      def config: Task[Config]
    }

    val live: ULayer[ConfigProvider] =
      ZLayer.succeed {
        new Service {
          override def config: Task[Config] = Task(Config("8081", List("localhost:9092")))
        }
      }
  }

}
