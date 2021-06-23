package io.kafkamate
package util

import io.github.embeddedkafka.{EmbeddedK, EmbeddedKafka, EmbeddedKafkaConfig}
import zio._

object KafkaEmbedded {
  type Kafka = Has[Kafka.Service]

  object Kafka {
    trait Service {
      def bootstrapServers: List[String]
      def stop(): UIO[Unit]
    }

    case class EmbeddedKafkaService(embeddedK: EmbeddedK) extends Service {
      override def bootstrapServers: List[String] = List(s"localhost:${embeddedK.config.kafkaPort}")
      override def stop(): UIO[Unit]              = ZIO.effectTotal(embeddedK.stop(true))
    }

    case object DefaultLocal extends Service {
      override def bootstrapServers: List[String] = List(s"localhost:9092")
      override def stop(): UIO[Unit]              = UIO.unit
    }

    lazy val embedded: TaskLayer[Kafka] = ZLayer.fromManaged {
      implicit val embeddedKafkaConfig = EmbeddedKafkaConfig(
        customBrokerProperties = Map("group.min.session.timeout.ms" -> "500", "group.initial.rebalance.delay.ms" -> "0")
      )
      ZManaged.make(Task(EmbeddedKafkaService(EmbeddedKafka.start())))(_.stop())
    }

    lazy val local: ULayer[Kafka] = ZLayer.succeed(DefaultLocal)
  }
}
