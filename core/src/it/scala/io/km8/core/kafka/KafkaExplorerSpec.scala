package io.km8.core.kafka

import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.*
import zio.kafka.consumer.*
import zio.kafka.serde.Serde
import zio.test.DefaultRunnableSpec
import zio.test.*
import zio.test.Assertion.*

import io.km8.common.*

object KafkaExplorerSpec extends DefaultRunnableSpec:

  override def spec =
    suite("Kafka explorer spec")(
      testM("test listConsumerGroups & listConsumerOffsets") {
        val clusterId = "123"
        val consumerGroup = "group-km8"

        val layer =
          Clock.live >+>
            Blocking.live >+>
            itlayers.kafkaContainer >+>
            itlayers.clusterConfig(clusterId) >+>
            KafkaExplorer.liveLayer >+>
            itlayers.consumerLayer(consumerGroup) >+>
            KafkaProducer.liveLayer

        val test =
          for {
            topic <- UIO("topic10")
            _ <- KafkaProducer.produce(topic, "123", "test")(clusterId)
            _ <- Consumer
                   .subscribeAnd(Subscription.topics(topic))
                   .plainStream(Serde.string, Serde.string)
                   .mapM(cr =>
                     ZIO.debug(s"${"-" * 10}> key: ${cr.key}, value: ${cr.value}") *>
                       cr.offset.commit.as(cr.value)
                   )
                   .take(1)
                   .runCollect
            r1 <- KafkaExplorer(_.listConsumerGroups(clusterId))
            r2 <- KafkaExplorer(_.listConsumerOffsets(clusterId, consumerGroup))
          } yield {
            assert(r1.groups.map(_.groupId))(equalTo(List(consumerGroup))) &&
            assert(r2.offsets)(equalTo(Map(TopicPartitionInternal(topic, 0) -> 1)))
          }

        test.provideLayer(layer)
      } @@ TestAspect.timeout(30.seconds)
    )
