package io.kafkamate
package kafka

import java.util.UUID

import com.github.mlangc.slf4zio.api._
import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.stream.ZStream
import zio.kafka.consumer._
import zio.kafka.consumer.Consumer._
import zio.kafka.serde.Deserializer
import zio.macros.accessible

import config._, Config._
import messages.Message

@accessible object KafkaConsumer {
  type KafkaConsumer = Has[Service]

  trait Service {
    def consumeN(topic: String, nrOfMessages: Long): RIO[Clock with Blocking, List[Message]]
    def consumeStream(topic: String): ZStream[Clock with Blocking, Throwable, Message]
  }

  private [kafka] lazy val kafkaConsumerLayer: URLayer[HasConfig, KafkaConsumer] =
    ZLayer.fromService(createService)

  lazy val liveLayer: ULayer[KafkaConsumer] =
    Config.liveLayer >>> kafkaConsumerLayer

  private def createService(config: ConfigProperties): Service =
    new Service with LoggingSupport {
      private lazy val timeout: Duration = 1000.millis
      private def consumerSettings: ConsumerSettings =
        ConsumerSettings(config.kafkaHosts)
          .withGroupId(s"kafkamate-${UUID.randomUUID().toString}")
          .withClientId("kafkamate")
          .withOffsetRetrieval(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
          .withCloseTimeout(30.seconds)

      private def makeConsumerLayer: RLayer[Clock with Blocking, Consumer] =
        Consumer.make(consumerSettings).toLayer

      def consumeN(topic: String, nrOfMessages: Long): RIO[Clock with Blocking, List[Message]] = {
        val consumer =
          for {
            _ <- Consumer.subscribe(Subscription.Topics(Set(topic)))
            endOffsets <- Consumer.assignment.repeatUntil(_.nonEmpty).flatMap(Consumer.endOffsets(_, timeout))
            _ <- logger.infoIO( s"End offsets: $endOffsets")
            records <- Consumer
              .plainStream(Deserializer.string, Deserializer.string)
              .takeUntil(cr => untilExists(endOffsets, cr))
              .take(nrOfMessages)
              .runCollect
              .map(_.map(v => Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value)))
          } yield records.toList
        consumer.provideSomeLayer[Clock with Blocking](makeConsumerLayer)
      }

      private def untilExists(endOffsets: Map[TopicPartition, Long],
                              cr: CommittableRecord[String, String]): Boolean =
        endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)

      def consumeStream(topic: String): ZStream[Clock with Blocking, Throwable, Message] =
        Consumer
          .subscribeAnd(Subscription.Topics(Set(topic)))
          .plainStream(Deserializer.string, Deserializer.string)
          .tap(cr => logger.debugIO(s"Msg: ${cr.record.key}"))
          .map(v => Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value))
          .provideSomeLayer[Clock with Blocking](makeConsumerLayer)
    }
}
