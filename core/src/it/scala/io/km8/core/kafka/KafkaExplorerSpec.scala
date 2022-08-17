package io.km8.core.kafka

import zio.*
import zio.test.*
import zio.kafka.consumer.*
import zio.kafka.serde.Serde
import zio.test.*
import zio.test.Assertion.*

import io.km8.common.*

object KafkaExplorerSpec extends ZIOSpecDefault:

  override def spec =
    suite("Kafka explorer spec")(
      test("test listConsumerGroups & listConsumerOffsets") {
        val clusterId = "123"
        val consumerGroup = "group-km8"

        val layer =
          itlayers.kafkaContainer >+>
            itlayers.clusterConfig(clusterId) >+>
            KafkaExplorer.liveLayer >+>
            itlayers.consumerLayer(consumerGroup) >+>
            KafkaProducer.liveLayer

        val test =
          for {
            topic <- ZIO.succeed("topic10")
            _ <- KafkaProducer.produce(topic, "123", "test")(clusterId)
            _ <- Consumer
                   .subscribeAnd(Subscription.topics(topic))
                   .plainStream(Serde.string, Serde.string)
                   .mapZIO(cr =>
                     ZIO.debug(s"${"-" * 10}> key: ${cr.key}, value: ${cr.value}") *>
                       cr.offset.commit.as(cr.value)
                   )
                   .take(1)
                   .runCollect
            r1 <- KafkaExplorer.listConsumerGroups(clusterId)
            r2 <- KafkaExplorer.listConsumerOffsets(clusterId, consumerGroup)
          } yield {
            assertTrue(
              r1.groups.map(_.groupId) == List(consumerGroup),
              r2.offsets == Map(TopicPartitionInternal(topic, 0) -> 1L)
            )
          }

        test.provideLayer(layer)
      } @@ TestAspect.timeout(30.seconds)
    ).provide(zio.test.liveEnvironment, zio.test.Live.default)
