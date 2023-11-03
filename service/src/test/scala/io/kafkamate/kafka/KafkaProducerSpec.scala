package io.kafkamate.kafka

import io.kafkamate.util.HelperSpec
import zio._
import zio.duration._
import zio.test._

object KafkaProducerSpec extends DefaultRunnableSpec with HelperSpec {

  // add tests for KafkaProducer using KafkaEmbedded and SchemaRegistryEmbedded and zio-test
  // the test should testing the proto serialization and consume the messages from kafka and check the deserialization
  // the test should testing the json serialization and consume the messages from kafka and check the deserialization

  override def spec = suite("Kafka Producer")(
    testM("produce proto messages to kafka") {
      for {
        _ <- ZIO.unit
      } yield assertCompletes
    }
  ) @@ TestAspect.timeout(30.seconds)

}
