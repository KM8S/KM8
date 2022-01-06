package io.km8.core
package kafka

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import com.google.protobuf.{Message => GMessage}
import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.*
import zio.logging.*
import zio.stream.ZStream
import zio.kafka.consumer.*
import zio.kafka.consumer.Consumer.*
import zio.kafka.serde.Deserializer

import config.*, ClustersConfig.*
import com.google.protobuf.Extension.MessageType
import io.km8.common.*

trait KafkaConsumer {
  def consume(request: ConsumeRequest): ZStream[Any, Throwable, Message]
}

object KafkaConsumer {

  lazy val liveLayer: URLayer[Clock with Blocking with Has[ClusterConfig], Has[KafkaConsumer]] =
    (KafkaConsumerLive(_, _, _)).toLayer

  case class KafkaConsumerLive(
    clock: Clock.Service,
    blocking: Blocking.Service,
    clustersConfigService: ClusterConfig)
      extends KafkaConsumer {
    val clockLayer = ZLayer.succeed(clock)
    val blockingLayer = ZLayer.succeed(blocking)

    private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
      offsetValue match {
        case "earliest" => AutoOffsetStrategy.Earliest
        case _          => AutoOffsetStrategy.Latest
      }

    private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[GMessage]]] =
      Deserializer
        .fromKafkaDeserializer(new KafkaProtobufDeserializer(), settings.configs, false)
        .map(_.asTry)

    private def consumerSettings(config: ClusterSettings, offsetStrategy: String): Task[ConsumerSettings] =
      Task {
        val uuid = UUID.randomUUID().toString
        ConsumerSettings(config.kafkaHosts)
          .withGroupId(s"group-kafkamate-$uuid")
          .withClientId(s"client-kafkamate-$uuid")
          .withProperties(config.protoSerdeSettings.map(_.configs).getOrElse(Map.empty))
          .withOffsetRetrieval(OffsetRetrieval.Auto(extractOffsetStrategy(offsetStrategy)))
          .withCloseTimeout(10.seconds)
      }

    private def makeConsumerLayer(clusterId: String, offsetStrategy: String) =
      ZLayer.fromManaged {
        for {
          cs <- clustersConfigService.getCluster(clusterId).toManaged_
          settings <- consumerSettings(cs, offsetStrategy).toManaged_
          consumer <- Consumer.make(settings)
        } yield consumer
      }

    def consume(request: ConsumeRequest): ZStream[Any, Throwable, Message] = {
      def consumer[T](valueDeserializer: Deserializer[Any, Try[T]]) = Consumer
        .subscribeAnd(Subscription.topics(request.topicName))
        .plainStream(Deserializer.string, valueDeserializer)
        .collect {
          case v if v.value.isSuccess =>
            Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value.get.toString)
        }

      val stream = request.messageFormat match {
        case MessageFormat.PROTOBUF =>
          val protoSettings = clustersConfigService
            .getCluster(request.clusterId)
            .flatMap(c =>
              ZIO
                .fromOption(c.protoSerdeSettings)
                .orElseFail(new Exception("SchemaRegistry url was not provided!"))
            )
          ZStream
            .fromEffect(protoSettings.flatMap(protobufDeserializer))
            .flatMap(consumer)
        case _ => consumer(Deserializer.string.asTry)
      }

      val withFilter = {
        val trimmed = request.filterKeyword.trim
        if (trimmed.isEmpty) stream
        else stream.filter(m => m.key.contains(trimmed) || m.value.contains(trimmed))
      }

      val withFilterLimit =
        if (request.maxResults <= 0L) withFilter
        else withFilter.take(request.maxResults)

      withFilterLimit.provideLayer(
        clockLayer ++ blockingLayer >>>
          makeConsumerLayer(request.clusterId, request.offsetStrategy)
      )
    }
  }
}
