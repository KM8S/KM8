package io.km8.core
package kafka

import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.*
import zio.kafka.consumer.*
import zio.kafka.consumer.Consumer.*
import zio.kafka.serde.Deserializer
import zio.logging.*
import zio.stream.ZStream

import com.google.protobuf.Extension.MessageType
import com.google.protobuf.Message as GMessage
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import io.km8.common.*
import io.km8.core.config.*
import io.km8.core.config.ClustersConfig.*

import scala.jdk.CollectionConverters.*
import scala.util.Try
import java.util.UUID

trait KafkaConsumer {
  def consume(request: ConsumeRequest): ZStream[Any, Throwable, Message]
}

object KafkaConsumer {

  lazy val liveLayer: URLayer[Clock with Blocking with Has[ClusterConfig], Has[KafkaConsumer]] =
    (KafkaConsumerLive(_, _, _)).toLayer

  def consume(request: ConsumeRequest): ZStream[Has[KafkaConsumer], Throwable, Message] =
    ZStream.accessStream[Has[KafkaConsumer]](_.get.consume(request))

  case class KafkaConsumerLive(
    clock: Clock.Service,
    blocking: Blocking.Service,
    clustersConfigService: ClusterConfig)
      extends KafkaConsumer {
    private val clockLayer = ZLayer.succeed(clock)
    private val blockingLayer = ZLayer.succeed(blocking)

    private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
      offsetValue match {
        case "earliest" => AutoOffsetStrategy.Earliest
        case _          => AutoOffsetStrategy.Latest
      }

    private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[GMessage]]] =
      val protoDeser = new KafkaProtobufDeserializer()
      protoDeser.configure(settings.configs.asJava, false)
      Deserializer.fromKafkaDeserializer(protoDeser, settings.configs, false).map(_.asTry)

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
