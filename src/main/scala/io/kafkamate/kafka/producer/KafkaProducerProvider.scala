package io.kafkamate
package kafka
package producer

import zio._
import zio.duration._
import zio.kafka.client._
import zio.kafka.client.serde._
import org.apache.kafka.clients.producer.ProducerRecord
import zio.blocking.Blocking

trait KafkaProducerProvider {
  def kafkaProducer: KafkaProducerProvider.Service
}

object KafkaProducerProvider {

  trait Env extends Blocking

  trait Service {
    def produce(topic: String, key: String, value: String): RIO[Env, Unit]
  }

  trait LiveProducer extends KafkaProducerProvider {

    val producerSettings: ProducerSettings =
      ProducerSettings(
        bootstrapServers = List("localhost:9092"),
        closeTimeout = 30.seconds,
        extraDriverSettings = Map.empty
      )

    def kafkaProducer: Service = new Service {
      def produce(topic: String, key: String, value: String): RIO[Blocking, Unit] = {
        Producer.make(producerSettings, Serde.string, Serde.string).use {
          _.produce(new ProducerRecord(topic, key, value)).unit
        }
      }
    }

  }
}
