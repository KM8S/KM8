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

    val kafkaProducerLayer: URLayer[ConfigProvider, KafkaProducerProvider] =
      ZLayer.fromService { configProvider =>
        new Service {
          def produce(topic: String, key: String, value: String): Task[Unit] =
            for {
              c <- configProvider.config
              producerSettings = ProducerSettings(c.kafkaHosts)
              producerLayer = Blocking.live ++ Producer.make[Any, String, String](producerSettings, Serde.string, Serde.string).orDie
              _ <- Producer.produce[Any, String, String](new ProducerRecord(topic, key, value)).unit.provideLayer(producerLayer)
            } yield ()
        }
      }

    val liveLayer: ULayer[KafkaProducerProvider] = ConfigProvider.live >>> kafkaProducerLayer
  }
}
