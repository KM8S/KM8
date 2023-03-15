package io.kafkamate.kafka

import io.kafkamate.util.HelperSpec
import zio.test.DefaultRunnableSpec
import zio._
import zio.test._
import zio.duration._

object KafkaProducerSpec extends DefaultRunnableSpec with HelperSpec {

  // add tests for KafkaProducer using KafkaEmbedded and SchemaRegistryEmbedded and zio-test
  // the test should testing the proto serialization and consume the messages from kafka and check the deserialization
  // the test should testing the json serialization and consume the messages from kafka and check the deserialization

  override def spec = suite("Kafka Producer")(
    testM("produce proto messages to kafka") {
      for {
        topic <- UIO("topic150")
        kvs    = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
        _     <- produceMany(topic, kvs)
      } yield assertCompletes
    }
  ).provideLayerShared(???) @@ TestAspect.timeout(30.seconds)


}
