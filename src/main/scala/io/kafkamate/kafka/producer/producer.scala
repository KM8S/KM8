package io.kafkamate
package kafka

import config._
import zio._
import zio.kafka.serde._
import zio.kafka.producer._
import zio.blocking.Blocking

package object producer {
  type KafkaProducerProvider = Has[KafkaProducerProvider.Service]

  object KafkaProducerProvider {
    trait Service {
      def produce(topic: String, key: String, value: String): Task[Unit]
    }

    private [producer] lazy val kafkaProducerLayer: URLayer[Blocking with Config, KafkaProducerProvider] =
      ZLayer.fromServices[Blocking.Service, ConfigProperties, KafkaProducerProvider.Service] { (blockingService, config) =>
        new Service {
          lazy val serdeLayer: ULayer[Has[Serializer[Any, String]]] =
            ZLayer.succeed(Serde.string)

          lazy val settingsLayer: ULayer[Has[ProducerSettings]] =
            ZLayer.succeed(ProducerSettings(config.kafkaHosts))

          def producerLayer =
            serdeLayer ++ settingsLayer >>> Producer.live[Any, String, String]

          def produce(topic: String, key: String, value: String): Task[Unit] =
            Producer
              .produce[Any, String, String](topic, key, value)
              .unit
              .provideSomeLayer[Blocking](producerLayer)
              .provide(Has(blockingService))
        }
      }

    lazy val liveLayer: ULayer[KafkaProducerProvider] =
      Blocking.live ++ config.liveLayer >>> kafkaProducerLayer
  }
}
