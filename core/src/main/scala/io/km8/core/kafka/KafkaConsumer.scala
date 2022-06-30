package io.km8.core
package kafka

import zio.*

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

  lazy val liveLayer: URLayer[ClusterConfig, KafkaConsumer] =
    ZLayer {
      for {
        config <- ZIO.service[ClusterConfig]
      } yield KafkaConsumerLive(config)
    }

  def consume(request: ConsumeRequest): ZStream[KafkaConsumer, Throwable, Message] =
    ZStream.environmentWithStream[KafkaConsumer](_.get.consume(request))

  case class KafkaConsumerLive(
    clustersConfigService: ClusterConfig)
      extends KafkaConsumer {

    // TODO Ciprian transform to enum or reuse the Consumer enum + serdes
    private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
      offsetValue.toLowerCase match {
        case "earliest" => AutoOffsetStrategy.Earliest
        case _          => AutoOffsetStrategy.Latest
      }

    private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[GMessage]]] =
      Deserializer
        .fromKafkaDeserializer(new KafkaProtobufDeserializer(), settings.configs, false)
        .map(_.asTry)

    private def consumerSettings(config: ClusterSettings, offsetStrategy: String): Task[ConsumerSettings] =
      ZIO.attempt {
        val uuid = UUID.randomUUID().toString
        ConsumerSettings(config.kafkaHosts)
          .withGroupId(s"group-kafkamate-$uuid")
          .withClientId(s"client-kafkamate-$uuid")
          .withProperties(config.protoSerdeSettings.map(_.configs).getOrElse(Map.empty))
          .withOffsetRetrieval(OffsetRetrieval.Auto(extractOffsetStrategy(offsetStrategy)))
          .withCloseTimeout(10.seconds)
      }

    private def makeConsumerLayer(
      clusterId: String,
      offsetStrategy: String
    ): ZLayer[Any, Throwable, Consumer] =
      ZLayer.scoped {
        for {
          cs <- clustersConfigService.getCluster(clusterId)
          settings <- consumerSettings(cs, offsetStrategy)
          consumer <- Consumer.make(settings)
        } yield consumer
      }

    def consume(request: ConsumeRequest): ZStream[Any, Throwable, Message] = {
      def consumer[T](
        valueDeserializer: Deserializer[Any, Try[T]]
      ): ZStream[Consumer, Throwable, Message] = Consumer
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
            .fromZIO(protoSettings.flatMap(protobufDeserializer))
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
        makeConsumerLayer(request.clusterId, request.offsetStrategy)
      )
    }
  }
}
