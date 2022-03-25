package io.km8.core.kafka

import zio.blocking.{Blocking, effectBlocking}
import zio.{Has, Task, UIO, ZIO, ZLayer, ZManaged}

import com.dimafeng.testcontainers.KafkaContainer
import io.km8.core.config.{ClusterConfig, ClusterProperties, ClusterSettings}

object it_layers {

  val kafkaContainer: ZLayer[Blocking, Nothing, Has[KafkaContainer]] =
    ZManaged.make {
      effectBlocking {
        val container = new KafkaContainer()
        container.start()
        container
      }.orDie
    }(container => effectBlocking(container.stop()).orDie).toLayer

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

}
