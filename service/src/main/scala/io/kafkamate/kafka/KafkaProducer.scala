package io.kafkamate
package kafka

import zio._
import zio.kafka.serde._
import zio.kafka.producer._
import zio.blocking.Blocking
import zio.macros.accessible

import config._, Config._

@accessible object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {
    def produce(topic: String, key: String, value: String): RIO[Blocking, Unit]
  }

  private [kafka] lazy val kafkaProducerLayer: URLayer[HasConfig, KafkaProducer] =
    ZLayer.fromService { config =>
      new Service {
        lazy val serdeLayer: ULayer[Has[Serializer[Any, String]]] =
          ZLayer.succeed(Serde.string)

        lazy val settingsLayer: ULayer[Has[ProducerSettings]] =
          ZLayer.succeed(ProducerSettings(config.kafkaHosts))

        def producerLayer =
          serdeLayer ++ settingsLayer >>> Producer.live[Any, String, String]

        def produce(topic: String, key: String, value: String): RIO[Blocking, Unit] =
          Producer
            .produce[Any, String, String](topic, key, value)
            .unit
            .provideSomeLayer[Blocking](producerLayer)
      }
    }

  lazy val liveLayer: ULayer[KafkaProducer] =
    Config.liveLayer >>> kafkaProducerLayer
}
