package io.kafkamate
package kafka

import zio.*
import zio.blocking.*
import zio.kafka.serde.*
import zio.kafka.producer.*

import config.*, ClustersConfig.*

object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {

    def produce(
      topic: String,
      key: String,
      value: String
    )(
      clusterId: String
    ): RIO[Blocking, Unit]
  }

  lazy val liveLayer: URLayer[Has[ClusterConfig], KafkaProducer] =
    ZLayer.fromService { clusterConfigService =>
      new Service {
        lazy val serdeLayer: ULayer[Has[Serializer[Any, String]]] =
          ZLayer.succeed(Serde.string)

        def settingsLayer(clusterId: String): Task[ProducerSettings] =
          clusterConfigService
            .getCluster(clusterId)
            .map(c => ProducerSettings(c.kafkaHosts))

        def producerLayer(clusterId: String) =
          settingsLayer(clusterId).toManaged_
            .flatMap(settings => Producer.make(settings))
            .provideLayer(Blocking.any ++ serdeLayer ++ ZLayer.succeed(clusterConfigService))
            .toLayer

        def produce(
          topic: String,
          key: String,
          value: String
        )(
          clusterId: String
        ): RIO[Blocking, Unit] =
          Producer
            .produce[Any, String, String](topic, key, value, Serde.string, Serde.string)
            .unit
            .provideSomeLayer[Blocking](producerLayer(clusterId))
      }
    }

}
