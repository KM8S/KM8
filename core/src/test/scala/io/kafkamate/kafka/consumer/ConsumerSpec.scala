package io.kafkamate
package kafka
package consumer

/*
import zio.*
import zio.test.Assertion.*
import zio.test.TestAspect.*
import zio.logging.*

import util.{HelperSpec, KafkaEmbedded}

object ConsumerSpec extends ZIOSpecDefault with HelperSpec {
  import KafkaConsumer.*
  import utils.Logger
  import messages.*

  val testLayer
    : Layer[TestFailure[Throwable], StringProducer with KafkaConsumer] =
    (Clock.live >+>
      Console.live >+>
      KafkaEmbedded.Kafka.embedded >+>
      stringProducer >+>
      testConfigLayer >+>
      Logger.liveLayer >+>
      KafkaConsumer.liveLayer).mapError(TestFailure.fail)

  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Kafka Consumer")(
      testM("consume messages from kafka") {
        for {
          topic <- UIO("topic150")
          kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _ <- produceMany(topic, kvs)
          records <- KafkaConsumer.consume(ConsumeRequest("test-id", topic, 5, "earliest", "")).runCollect
        } yield assertTrue(records.map(v => (v.key, v.value)).toList == kvs.map(v => (v._1, v._2)))
      }
    ).provideLayerShared(testLayer) @@ timeout(30.seconds)
}
*/
