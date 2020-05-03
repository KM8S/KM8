package io.kafkamate
package kafka

import config._
import org.apache.kafka.clients.producer.ProducerRecord
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

    private [producer] val kafkaProducerLayer: URLayer[Blocking with ConfigProvider, KafkaProducerProvider] =
      ZLayer.fromServices[Blocking.Service, ConfigProvider.Service, KafkaProducerProvider.Service] { (blockingService, configProvider) =>
        new Service {
          def produce(topic: String, key: String, value: String): Task[Unit] =
            for {
              c <- configProvider.config
              producerLayer = Producer.make(ProducerSettings(c.kafkaHosts), Serde.string, Serde.string).orDie
              _ <- Producer
                .produce[Any, String, String](new ProducerRecord(topic, key, value))
                .flatten
                .provideSomeLayer[Blocking](producerLayer)
                .provide(Has(blockingService))
            } yield ()
        }
      }

    val liveLayer: ULayer[KafkaProducerProvider] = Blocking.live ++ ConfigProvider.liveLayer >>> kafkaProducerLayer
  }
}
