package io.km8.core.kafka

import zio.blocking.*
import zio.clock.Clock
import zio.*
import zio.kafka.consumer.Consumer
import com.dimafeng.testcontainers.KafkaContainer
import io.km8.core.config.{ClusterConfig, ClusterProperties, ClusterSettings}
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.ConsumerSettings

object itlayers:

  val kafkaContainer: ZLayer[Blocking, Nothing, Has[KafkaContainer]] =
    ZManaged.make {
      effectBlocking {
        val container = new KafkaContainer()
        container.start()
        container
      }.orDie
    }(container => effectBlocking(container.stop()).orDie).toLayer

  def consumerSettings(cg: String): ZManaged[Has[KafkaContainer], Nothing, ConsumerSettings] =
    ZIO
      .service[KafkaContainer]
      .map(c =>
        ConsumerSettings(List(c.bootstrapServers))
          .withGroupId(cg)
          .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
      )
      .toManaged_

  def consumerLayer(cgroup: String): ZLayer[Has[KafkaContainer] with Clock with Blocking, Nothing, Has[Consumer]] =
    consumerSettings(cgroup).flatMap(Consumer.make(_)).orDie.toLayer

  def clusterConfig(clusterId: String): ZLayer[Has[KafkaContainer], Nothing, Has[ClusterConfig]] =
    ZIO
      .service[KafkaContainer]
      .map(kafkaContainer =>
        new ClusterConfig {

          override def readClusters: Task[ClusterProperties] = Task(
            ClusterProperties(clusters =
              List(
                ClusterSettings(
                  id = clusterId,
                  name = kafkaContainer.containerName,
                  kafkaHosts = List(kafkaContainer.bootstrapServers),
                  schemaRegistryUrl = None
                )
              )
            )
          )

          override def writeClusters(cluster: ClusterSettings): Task[Unit] = ???

          override def deleteCluster(clusterId: String): Task[ClusterProperties] = ???
        }
      )
      .toLayer
