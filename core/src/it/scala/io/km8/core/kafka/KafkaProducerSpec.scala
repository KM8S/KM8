package io.km8.core.kafka

import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.test.*
import zio.test.Assertion.*

import com.dimafeng.testcontainers.KafkaContainer
import io.km8.common.*
import io.km8.core.kafka.KafkaProducer.KafkaProducer
import org.apache.kafka.clients.consumer.OffsetResetStrategy

object KafkaProducerSpec extends DefaultRunnableSpec:

  private val consumerLayer: ZLayer[Has[KafkaContainer], Nothing, Has[KafkaConsumer]] =
    Clock.live ++ Blocking.live ++ it_layers.clusterConfig(clusterId = "cluster_id") >>> KafkaConsumer.liveLayer

  private val producerLayer: ZLayer[Has[KafkaContainer], Nothing, KafkaProducer] =
    Blocking.live ++ it_layers.clusterConfig(clusterId = "cluster_id") >>> KafkaProducer.liveLayer

  val specLayer: ZLayer[Has[KafkaContainer], Nothing, Has[KafkaConsumer] & KafkaProducer] =
    consumerLayer ++ producerLayer

  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] =
    mainSpec
      .provideSomeLayer[environment.TestEnvironment & Has[KafkaContainer]](specLayer)
      .provideCustomLayerShared(Blocking.live >>> it_layers.kafkaContainer)

  private val mainSpec =
    suite("Kafka services")(
      testM("KafkaProducer sends a message and KafkaConsumer reads it correctly ") {

        val io: ZIO[Has[KafkaConsumer] with KafkaProducer, Throwable, String] = for {
          _ <- KafkaProducer.produce("test_topic", "key", "value")("cluster_id")
          p <- Promise.make[Nothing, String]
          fiber <- KafkaConsumer
                     .consume(
                       ConsumeRequest(
                         clusterId = "cluster_id",
                         topicName = "test_topic",
                         maxResults = 100L,
                         offsetStrategy = OffsetResetStrategy.EARLIEST.toString,
                         filterKeyword = "*",
                         messageFormat = MessageFormat.STRING
                       )
                     )
                     .mapM(message => p.succeed(message.value))
                     .runDrain
                     .fork
          value <- p.await
          _ <- fiber.interrupt
        } yield value

        assertM(io)(equalTo("value"))
      }
    )
