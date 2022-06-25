package io.km8.core
package kafka

import zio.*

import zio.kafka.producer.*
import zio.kafka.serde.*

import io.km8.core.config.*
import io.km8.core.config.ClustersConfig.*

trait KafkaProducer {

  def produce(
    topic: String,
    key: String,
    value: String
  )(
    clusterId: String
  ): ZIO[Scope, Throwable, Unit]
}

object KafkaProducer {

  def produce(
    topic: String,
    key: String,
    value: String
  )(
    clusterId: String
  ): ZIO[Scope & KafkaProducer, Throwable, Unit] = ZIO.environmentWithZIO[KafkaProducer](_.get.produce(topic, key, value)(clusterId))

  lazy val liveLayer: URLayer[ClusterConfig & Scope, KafkaProducer] =ZLayer.fromZIO( for {
    clusterConfigService <- ZIO.service[ClusterConfig]
  } yield new KafkaProducer {

    lazy val serdeLayer: ULayer[Serializer[Any, String]] =
      ZLayer.succeed(Serde.string)

    def settingsLayer(clusterId: String): Task[ProducerSettings] =
      clusterConfigService
        .getCluster(clusterId)
        .map(c => ProducerSettings(c.kafkaHosts))

    def producerLayer(clusterId: String): ZIO[Scope, Throwable, Producer] =
      settingsLayer(clusterId)
        .flatMap(settings => Producer.make(settings))

    def produce(
      topic: String,
      key: String,
      value: String
    )(
      clusterId: String
    ): ZIO[Scope, Throwable, Unit] =
      Producer
        .produce[Any, String, String](topic, key, value, Serde.string, Serde.string)
        .unit
        .provideLayer(ZLayer.fromZIO(producerLayer(clusterId)))
  }
  )
}