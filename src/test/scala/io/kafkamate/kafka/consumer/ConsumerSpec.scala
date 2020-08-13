package io.kafkamate
package kafka
package consumer

import io.kafkamate.kafka.consumer.KafkaConsumer.KafkaConsumer
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

  val testLayer: ZLayer[Any, TestFailure[Throwable], KafkaConsumer] =
    (Clock.live >+>
      (KafkaEmbedded.Kafka.embedded >+> stringProducer) ++
        ((KafkaEmbedded.Kafka.embedded ++ Clock.any ++ Blocking.live) >>> testConfigLayer) >>> KafkaConsumer.kafkaConsumerLayer)
      .mapError(TestFailure.fail)

  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Consumer Streaming")(
      testM("plainStream emits messages for a topic subscription") {
        for {
          topic <- UIO("topic150")
          kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _ <- produceMany(topic, kvs)
          records <- KafkaConsumer.consumeN(topic, 5)
        } yield assert(records)(equalTo(kvs))
      }
    ).provideLayerShared[TestEnvironment](testLayer) @@ timeout(10.seconds)
}
