package io.km8.core.kafka

import zio.*
import zio.kafka.consumer.*
import zio.kafka.serde.Serde
import zio.test.*
import zio.test.Assertion.*

import com.dimafeng.testcontainers.KafkaContainer
import io.km8.common.*
import org.apache.kafka.clients.consumer.OffsetResetStrategy

object KafkaProducerSpec extends ZIOSpecDefault:

  private val consumerLayer: ZLayer[KafkaContainer, Nothing, KafkaConsumer] =
    itlayers.clusterConfig(clusterId = "cluster_id") >>> KafkaConsumer.liveLayer

  private val producerLayer: ZLayer[KafkaContainer, Nothing, KafkaProducer] =
    itlayers.clusterConfig(clusterId = "cluster_id") >>> KafkaProducer.liveLayer

  val specLayer: ZLayer[KafkaContainer, Nothing, KafkaConsumer & KafkaProducer] =
    consumerLayer ++ producerLayer

  override def spec =
    mainSpec
      .provideSomeLayer[TestEnvironment & KafkaContainer](specLayer)
      .provideCustomLayerShared(itlayers.kafkaContainer)

  private val mainSpec =
    suite("Kafka services")(
      test("KafkaProducer sends a message and KafkaConsumer reads it correctly ") {
        for
          f1 <- KafkaConsumer
                  .consume(
                    ConsumeRequest(
                      clusterId = "cluster_id",
                      topicName = "test_topic",
                      maxResults = 100L,
                      offsetStrategy = OffsetResetStrategy.EARLIEST.toString,
                      filterKeyword = "",
                      messageFormat = MessageFormat.STRING
                    )
                  )
                  .runHead
                  .fork
          _ <- KafkaProducer.produce("test_topic", "key", "value")("cluster_id")
          maybeValue <- f1.join
        yield assertTrue(maybeValue.map(_.value).get == "value")

      }
    )
