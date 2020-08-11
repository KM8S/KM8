package io.kafkamate
package kafka
package consumer

import util.{HelperSpec, KafkaEmbedded}
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment._
import zio.test.{DefaultRunnableSpec, _}

object ConsumerSpec extends DefaultRunnableSpec with HelperSpec {
  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Consumer Streaming")(
      testM("plainStream emits messages for a topic subscription") {
        for {
          topic <- UIO("topic150")
          kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _ <- produceMany(topic, kvs)
          records <- ZIO.accessM[KafkaConsumerProvider](_.get.consumeAll(topic))
        } yield assert(records)(equalTo(kvs))
      }
    ).provideSomeLayerShared[TestEnvironment](
      Clock.live ++
        (KafkaEmbedded.Kafka.embedded >>> stringProducer ++ KafkaEmbedded.Kafka.embedded).mapError(TestFailure.fail) ++
        ((KafkaEmbedded.Kafka.embedded.orDie ++ Clock.live ++ Blocking.live) >>> testConfigLayer) >>> KafkaConsumerProvider.kafkaConsumerLayer
    ) @@ timeout(10.seconds)
}
