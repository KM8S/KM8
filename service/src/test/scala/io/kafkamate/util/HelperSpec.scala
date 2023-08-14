package io.kafkamate
package util

import io.kafkamate.config.ClustersConfig._
import io.kafkamate.config._
import io.kafkamate.util.KafkaEmbedded.Kafka
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio._
import zio.blocking.Blocking
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde

trait HelperSpec {

  val producerSettings: URLayer[Kafka, Has[ProducerSettings]] =
    ZIO.service[Kafka.Service].map(s => ProducerSettings(s.bootstrapServers)).toLayer

  val testConfigLayer: URLayer[Kafka, ClustersConfigService] =
    ZLayer.fromService[Kafka.Service, ClustersConfig.Service] { kafka =>
      new ClustersConfig.Service {
        def readClusters: Task[ClusterProperties] =
          Task(ClusterProperties(List(ClusterSettings("test-id", "test", kafka.bootstrapServers, None))))

        def writeClusters(cluster: ClusterSettings): Task[Unit] = ???

        def deleteCluster(clusterId: String): Task[ClusterProperties] = ???
      }
    }

  def produceMany(
    topic: String,
    kvs: Iterable[(String, String)]
  ): RIO[Blocking with Has[Producer], Chunk[RecordMetadata]] =
    Producer
      .produceChunk(
        Chunk.fromIterable(kvs.map { case (k, v) => new ProducerRecord(topic, k, v) }),
        Serde.string,
        Serde.string)
}
