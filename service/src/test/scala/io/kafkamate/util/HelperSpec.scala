package io.kafkamate
package util

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.{Serde, Serializer}

import config.Config._
import KafkaEmbedded.Kafka

trait HelperSpec {
  type StringProducer = Producer[Any, String, String]

  val producerSettings: URIO[Kafka, ProducerSettings] =
    ZIO.access[Kafka](_.get.bootstrapServers).map(ProducerSettings(_))

  val stringProducer: ZLayer[Kafka, Throwable, StringProducer] =
    (producerSettings.toLayer ++ ZLayer.succeed(Serde.string: Serializer[Any, String])) >>>
      Producer.live[Any, String, String]

  val testConfigLayer: RLayer[Clock with Blocking with Kafka, Config with Clock with Blocking] =
    ZLayer.requires[Clock] ++
      ZLayer.requires[Blocking] ++
      ZLayer.fromService[Kafka.Service, ConfigProperties] { kafka =>
        ConfigProperties("8080", kafka.bootstrapServers)
      }

  def produceMany(
       topic: String,
       kvs: Iterable[(String, String)]
     ): RIO[Blocking with StringProducer, Chunk[RecordMetadata]] =
    Producer
      .produceChunk[Any, String, String](Chunk.fromIterable(kvs.map {
        case (k, v) => new ProducerRecord(topic, k, v)
      }))
}
