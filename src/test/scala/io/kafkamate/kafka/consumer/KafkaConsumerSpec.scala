package io.kafkamate.kafka
package consumer

import net.manub.embeddedkafka.EmbeddedKafka
import zio._
import zio.clock.Clock
import zio.duration._
import util._
import zio.kafka.client.Subscription
import zio.kafka.client.serde.Serde
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment._
import zio.test.{DefaultRunnableSpec, _}

object KafkaConsumerSpec extends DefaultRunnableSpec(
  suite("Consumer Streaming")(
    testM("test dummy") {
      val kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
      for {
        _ <- ZIO.effectTotal(EmbeddedKafka.createCustomTopic("topic", partitions = 2))
        _ <- produceMany("topic", kvs)
        records <- withConsumer("group150", "client150") {
          _.subscribeAnd(Subscription.Topics(Set("topic")))
            .plainStream(Serde.string, Serde.string)
            .flattenChunks
            .take(5)
            .runCollect
        }
        kvOut = records.map { r =>
          (r.record.key, r.record.value)
        }
      } yield assert(kvOut, equalTo(kvs))
    }
  ).provideManagedShared(embeddedKafkaEnvironment) @@ timeout(180.seconds)
)