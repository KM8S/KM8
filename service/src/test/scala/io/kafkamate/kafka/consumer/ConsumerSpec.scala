package io.kafkamate
package kafka
package consumer

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test.environment._
import zio.logging._
import zio.console._
import zio.test.{DefaultRunnableSpec, _}

import util.{HelperSpec, KafkaEmbedded}

object ConsumerSpec extends DefaultRunnableSpec with HelperSpec {
  import KafkaConsumer._
  import messages._

  val testLayer
    : Layer[TestFailure[Throwable], Clock with Blocking with Logging with StringProducer with KafkaConsumer] =
    (Clock.live >+>
      Console.live >+>
      Blocking.live >+>
      KafkaEmbedded.Kafka.embedded >+>
      stringProducer >+>
      testConfigLayer >+>
      Main.liveLoggingLayer >+>
      KafkaConsumer.kafkaConsumerLayer).mapError(TestFailure.fail)

  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Kafka Consumer")(
      testM("consume N messages from kafka") {
        for {
          topic   <- UIO("topic150")
          kvs      = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _       <- produceMany(topic, kvs)
          records <- KafkaConsumer.consumeN(topic, 5, "earliest")("test-id")
        } yield assert(records.map(v => (v.key, v.value)))(equalTo(kvs.map(v => (v._1, v._2))))
      }
    ).provideLayerShared(testLayer) @@ timeout(30.seconds)
}
