package io.kafkamate.kafka

import io.kafkamate.kafka.KafkaConsumer._
import io.kafkamate.kafka.KafkaExplorer.HasKafkaExplorer
import io.kafkamate.messages._
import io.kafkamate.util.{HelperSpec, KafkaEmbedded}
import io.kafkamate.utils.Logger
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console._
import zio.duration._
import zio.kafka.producer.Producer
import zio.logging._
import zio.magic._
import zio.test.TestAspect._
import zio.test._
import zio.test.environment._

object KafkaConsumerSpec extends DefaultRunnableSpec with HelperSpec {

  type TestEnv = Clock with Blocking with Logging with KafkaConsumer with HasKafkaExplorer with Has[Producer]

  val testLayer: ULayer[TestEnv] =
    ZLayer.wire[TestEnv](
      Clock.live,
      Console.live,
      Blocking.live,
      KafkaExplorer.liveLayer,
      KafkaEmbedded.Kafka.embedded.orDie,
      testConfigLayer,
      Logger.liveLayer,
      producerSettings,
      Producer.live.orDie,
      KafkaConsumer.liveLayer
    )

  override def spec: ZSpec[TestEnvironment, Throwable] =
    suite("Kafka Consumer")(
      testM("consume messages from kafka") {
        val io = for {
          topic <- UIO("topic150")
          kvs = (1 to 5).toList.map(i => (s"key$i", s"msg$i"))
          _ <- produceMany(topic, kvs)
          req = ConsumeRequest("test-id", topic, 5, OffsetStrategy.LATEST, "", MessageFormat.STRING)
          records <- KafkaConsumer.consume(req).runCollect
        } yield assertTrue(records.map(v => (v.key.get, v.value.get)).toList == kvs.map(v => (v._1, v._2)))
        io
      }
    ).provideLayerShared(testLayer) @@ timeout(30.seconds)
}
