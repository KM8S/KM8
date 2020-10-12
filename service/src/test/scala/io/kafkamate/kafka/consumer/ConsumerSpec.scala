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
  import KafkaConsumer._
  import messages._

  val testLayer: ZLayer[Any, TestFailure[Throwable], Clock with Blocking with StringProducer with KafkaConsumer] =
    (Clock.live >+>
      Blocking.live >+>
      KafkaEmbedded.Kafka.embedded >+>
      stringProducer >+>
      testConfigLayer >+>
      KafkaConsumer.kafkaConsumerLayer).mapError(TestFailure.fail)

  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Kafka Consumer")(
      testM("consume N messages from kafka") {
        for {
          topic <- UIO("topic150")
          kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _ <- produceMany(topic, kvs)
          records <- KafkaConsumer.consumeN(topic, 5)
        } yield assert(records.map(v => (v.key, v.value)))(equalTo(kvs.map(v => (v._1, v._2))))
      }
    ).provideLayerShared(testLayer) @@ timeout(30.seconds)
}
