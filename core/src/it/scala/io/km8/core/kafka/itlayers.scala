package io.km8.core.kafka

import com.dimafeng.testcontainers.KafkaContainer
import zio.*
import zio.kafka.consumer.Consumer
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.ConsumerSettings

import io.km8.core.config.{ClusterConfig, ClusterProperties, ClusterSettings}

object itlayers:

  val kafkaContainer: ZLayer[Any, Nothing, KafkaContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease(ZIO.attemptBlocking {
        val container = new KafkaContainer()
        container.start()
        container
      })(container => ZIO.attemptBlocking(container.stop()).orDie)
    }.orDie

  def consumerSettings(cg: String): ZIO[KafkaContainer with Scope, Nothing, ConsumerSettings] =
    ZIO
      .service[KafkaContainer]
      .map(c =>
        ConsumerSettings(List(c.bootstrapServers))
          .withGroupId(cg)
          .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
      )

  def consumerLayer(cgroup: String): ZLayer[KafkaContainer with Clock, Nothing, Consumer] =
    ZLayer.scoped {
      consumerSettings(cgroup).flatMap(Consumer.make(_)).orDie
    }

  def clusterConfig(clusterId: String): ZLayer[KafkaContainer, Nothing, ClusterConfig] =
    ZLayer.fromZIO {
      ZIO
        .service[KafkaContainer]
        .map(kafkaContainer =>
          new ClusterConfig {

            override def readClusters: Task[ClusterProperties] = ZIO.attempt(
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
    }
