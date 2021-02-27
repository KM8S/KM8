package io.kafkamate
package kafka

import java.util.UUID

import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.logging._
import zio.stream.ZStream
import zio.kafka.consumer._
import zio.kafka.consumer.Consumer._
import zio.kafka.serde.Deserializer
import zio.macros.accessible

import config._, ClustersConfig._
import messages._

@accessible object KafkaConsumer {
  type KafkaConsumer = Has[Service]
  type Env = Clock with Blocking with Logging

  trait Service {
    def consumeN(topic: String, nrOfMessages: Long, offsetStrategy: String)(clusterId: String): RIO[Env, List[Message]]
    def consumeStream(request: ConsumeRequest): ZStream[Env, Throwable, Message]
  }

  lazy val kafkaConsumerLayer: URLayer[ClustersConfigService, KafkaConsumer] =
    ZLayer.fromService(createService)

  lazy val liveLayer: URLayer[Logging, KafkaConsumer] =
    ClustersConfig.liveLayer >>> kafkaConsumerLayer

  private def createService(clustersConfigService: ClustersConfig.Service): Service =
    new Service {
      private lazy val timeout: Duration = 1000.millis

      private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
        offsetValue match {
          case "latest" => AutoOffsetStrategy.Latest
          case _        => AutoOffsetStrategy.Earliest
        }

      private def consumerSettings(config: ClusterSettings, offsetStrategy: String): Task[ConsumerSettings] =
        Task {
          val uuid = UUID.randomUUID().toString
          ConsumerSettings(config.hosts)
            .withGroupId(s"group-kafkamate-$uuid")
            .withClientId(s"client-kafkamate-$uuid")
            .withOffsetRetrieval(OffsetRetrieval.Auto(extractOffsetStrategy(offsetStrategy)))
            .withCloseTimeout(10.seconds)
        }

      private def makeConsumerLayer(clusterId: String, offsetStrategy: String): RLayer[Clock with Blocking, Consumer] =
        ZLayer.fromManaged {
          for {
            cs <- clustersConfigService.getCluster(clusterId).toManaged_
            settings <- consumerSettings(cs, offsetStrategy).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      def consumeN(topic: String, nrOfMessages: Long, offsetStrategy: String)(clusterId: String): RIO[Env, List[Message]] = {
        val consumer =
          for {
            _ <- Consumer.subscribe(Subscription.topics(topic))
            endOffsets <- Consumer.assignment.repeatUntil(_.nonEmpty).flatMap(Consumer.endOffsets(_, timeout))
            _ <- log.info( s"End offsets: $endOffsets")
            records <- Consumer
              .plainStream(Deserializer.string, Deserializer.string)
              .takeUntil(cr => untilExists(endOffsets, cr))
              .take(nrOfMessages)
              .runCollect
              .map(_.map(v => Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value)))
          } yield records.toList
        consumer.provideSomeLayer[Env](makeConsumerLayer(clusterId, offsetStrategy))
      }

      private def untilExists(endOffsets: Map[TopicPartition, Long],
                              cr: CommittableRecord[String, String]): Boolean =
        endOffsets.exists(o => o._1 == cr.offset.topicPartition && o._2 == 1 + cr.offset.offset)

      def consumeStream(request: ConsumeRequest): ZStream[Env, Throwable, Message] = {
        val stream = Consumer
          .subscribeAnd(Subscription.topics(request.topicName))
          .plainStream(Deserializer.string, Deserializer.string)
          .map(v => Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value))

        val withLimit = if (request.maxResults <= 0L) stream else stream.take(request.maxResults)

        val withLimitFilter =
          if (request.filterKeyword.isEmpty) withLimit
          else withLimit.filter(m =>
            m.key.contains(request.filterKeyword) ||
              m.value.contains(request.filterKeyword)
          )

        withLimitFilter.provideSomeLayer[Clock with Blocking](makeConsumerLayer(request.clusterId, request.offsetStrategy))
      }
    }
}
