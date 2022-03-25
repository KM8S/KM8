package io.km8.core
package kafka

import zio.*
import zio.blocking.*
import zio.kafka.producer.*
import zio.kafka.serde.*

import io.km8.core.config.*
import io.km8.core.config.ClustersConfig.*

object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {

    def produce(
      topic: String,
      key: String,
      value: String
    )(
      clusterId: String
    ): Task[Unit]
  }

  def produce(
    topic: String,
    key: String,
    value: String
  )(
    clusterId: String
  ): RIO[KafkaProducer, Unit] = ZIO.accessM[KafkaProducer](_.get.produce(topic, key, value)(clusterId))

  lazy val liveLayer: URLayer[Has[ClusterConfig] with Blocking, KafkaProducer] =
    ZLayer.fromServices[ClusterConfig, Blocking.Service, KafkaProducer.Service](
      (clusterConfigService: ClusterConfig, blocking: Blocking.Service) =>
        new Service {

          lazy val serdeLayer: ULayer[Has[Serializer[Any, String]]] =
            ZLayer.succeed(Serde.string)

          def settingsLayer(clusterId: String): Task[ProducerSettings] =
            clusterConfigService
              .getCluster(clusterId)
              .map(c => ProducerSettings(c.kafkaHosts))

          def producerLayer(clusterId: String):ZLayer[Any, Throwable, Has[Producer]] =
            settingsLayer(clusterId).toManaged_
              .flatMap(settings => Producer.make(settings))
              .provideLayer(ZLayer.succeed(blocking) ++ serdeLayer ++ ZLayer.succeed(clusterConfigService))
              .toLayer

          def produce(
            topic: String,
            key: String,
            value: String
          )(
            clusterId: String
          ): Task[Unit] =
            Producer
              .produce[Any, String, String](topic, key, value, Serde.string, Serde.string)
              .unit
              .provideLayer(producerLayer(clusterId))
        }
    )

}
