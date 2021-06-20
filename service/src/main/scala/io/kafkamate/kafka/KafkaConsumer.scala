package io.kafkamate
package kafka

import java.util.UUID
import scala.jdk.CollectionConverters._
import scala.util.Try

import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import com.google.protobuf.{Message => GMessage}
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

import io.kafkamate.messages.MessageFormat.PROTOBUF
import config._, ClustersConfig._
import messages._

@accessible object KafkaConsumer {
  type KafkaConsumer = Has[Service]
  type Env           = Clock with Blocking with Logging

  trait Service {
    def consume(request: ConsumeRequest): ZStream[Env, Throwable, Message]
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

      private def protobufDeserializer(settings: ProtoSerdeSettings): Deserializer[Any, Try[GMessage]] =
        Deserializer {
          val protoDeser = new KafkaProtobufDeserializer()
          protoDeser.configure(settings.configs.asJava, false)
          protoDeser
        }.asTry

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

      private def makeConsumerLayer(clusterId: String, offsetStrategy: String): RLayer[Clock with Blocking, Consumer] =
        ZLayer.fromManaged {
          for {
            cs       <- clustersConfigService.getCluster(clusterId).toManaged_
            settings <- consumerSettings(cs, offsetStrategy).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      def consume(request: ConsumeRequest): ZStream[Env, Throwable, Message] = {
        def consumer[T](valueDeserializer: Deserializer[Any, Try[T]]) = Consumer
          .subscribeAnd(Subscription.topics(request.topicName))
          .plainStream(Deserializer.string, valueDeserializer)
          .collect {
            case v if v.value.isSuccess =>
              Message(v.offset.offset, v.partition, v.timestamp, v.key, v.value.get.toString)
          }

        val stream = request.messageFormat match {
          case PROTOBUF =>
            val protoSettings = clustersConfigService
              .getCluster(request.clusterId)
              .flatMap(c =>
                ZIO
                  .fromOption(c.protoSerdeSettings)
                  .orElseFail(new Exception("SchemaRegistry url was not provided!"))
              )
            ZStream
              .fromEffect(protoSettings)
              .flatMap(p => consumer(protobufDeserializer(p)))
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

        withFilterLimit.provideSomeLayer[Env](
          makeConsumerLayer(request.clusterId, request.offsetStrategy)
        )
      }
    }
}
