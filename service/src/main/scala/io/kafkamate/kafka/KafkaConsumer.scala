package io.kafkamate
package kafka

import java.util.UUID

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
  type Env           = Clock with Blocking with Logging

  trait Service {
    def consumeStream(request: ConsumeRequest): ZStream[Env, Throwable, Message]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, KafkaConsumer] =
    ZLayer.fromService(createService)

  private def createService(clustersConfigService: ClustersConfig.Service): Service =
    new Service {
      private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
        offsetValue match {
          case "earliest" => AutoOffsetStrategy.Earliest
          case _          => AutoOffsetStrategy.Latest
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
            cs       <- clustersConfigService.getCluster(clusterId).toManaged_
            settings <- consumerSettings(cs, offsetStrategy).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      def consumeStream(request: ConsumeRequest): ZStream[Env, Throwable, Message] = {
        val stream = Consumer
          .subscribeAnd(Subscription.topics(request.topicName))
          .plainStream(Deserializer.string, Deserializer.string)
          .map(v => Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value))

        val withLimit = if (request.maxResults <= 0L) stream else stream.take(request.maxResults)

        val withLimitFilter = {
          val trimmed = request.filterKeyword.trim
          if (trimmed.isEmpty)
            withLimit
          else
            withLimit.filter(m => m.key.contains(trimmed) || m.value.contains(trimmed))
        }

        withLimitFilter.provideSomeLayer[Env](
          makeConsumerLayer(request.clusterId, request.offsetStrategy)
        )
      }
    }
}
