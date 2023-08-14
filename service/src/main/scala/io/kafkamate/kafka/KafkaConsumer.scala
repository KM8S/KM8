package io.kafkamate
package kafka

import java.util.UUID
import scala.util.{Try, Success, Failure}
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import com.google.protobuf.{MessageOrBuilder, Message}
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
import io.kafkamate.messages.MessageFormat
import config._
import ClustersConfig._
import com.google.protobuf.util.JsonFormat
import messages._

@accessible object KafkaConsumer {
  type KafkaConsumer = Has[Service]
  type Env           = Clock with Blocking with Logging

  trait Service {
    def consume(request: ConsumeRequest): ZStream[Env, Throwable, LogicMessage]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, KafkaConsumer] =
    ZLayer.fromService(createService)

  private def createService(clustersConfigService: ClustersConfig.Service): Service =
    new Service {
      private def extractOffsetStrategy(offsetValue: OffsetStrategy): AutoOffsetStrategy =
        offsetValue match {
          case OffsetStrategy.FROM_BEGINNING => AutoOffsetStrategy.Earliest
          case _                             => AutoOffsetStrategy.Latest
        }

      private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[Message]]] =
        Deserializer
          .fromKafkaDeserializer(new KafkaProtobufDeserializer[Message](), settings.configs, false)
          .map(_.asTry)

      private def consumerSettings(config: ClusterSettings, offsetStrategy: OffsetStrategy): Task[ConsumerSettings] =
        Task {
          val uuid = UUID.randomUUID().toString
          ConsumerSettings(config.kafkaHosts)
            .withGroupId(s"group-kafkamate-$uuid")
            .withClientId(s"client-kafkamate-$uuid")
            .withProperties(config.protoSerdeSettings.map(_.configs).getOrElse(Map.empty))
            .withOffsetRetrieval(OffsetRetrieval.Auto(extractOffsetStrategy(offsetStrategy)))
            .withCloseTimeout(10.seconds)
        }

      def toJson(messageOrBuilder: MessageOrBuilder): Task[String] =
        Task(JsonFormat.printer.print(messageOrBuilder))

      private def makeConsumerLayer(clusterId: String, offsetStrategy: OffsetStrategy) =
        ZLayer.fromManaged {
          for {
            cs       <- clustersConfigService.getCluster(clusterId).toManaged_
            settings <- consumerSettings(cs, offsetStrategy).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      private def deserializeAuto(
        messageFormat: MessageFormat,
        cachedDeserializer: Task[Deserializer[Any, Try[Message]]],
        record: CommittableRecord[String, Array[Byte]],
        fallback: Throwable => Task[LogicMessage]
      ) =
        cachedDeserializer.flatMap {
          _.deserialize(record.record.topic, record.record.headers, record.value).flatMap {
            case Success(message) =>
              toJson(message).map { jsonStr =>
                LogicMessage(
                  offset = record.offset.offset,
                  partition = record.partition,
                  timestamp = record.timestamp,
                  key = record.key,
                  valueFormat = messageFormat,
                  value = jsonStr
                )
              }
            case Failure(e) =>
              Task.fail(e)
          }.catchAll(t =>
            log.throwable(s"Failed auto deserializing $messageFormat with key: '${record.key}': ${t.getMessage}", t)
              *> fallback(t)
          )
        }

      private def deserializeString(v: CommittableRecord[String, Array[Byte]]) =
        Deserializer.string.deserialize(v.record.topic, v.record.headers, v.value).map { str =>
          LogicMessage(v.offset.offset, v.partition, v.timestamp, v.key, MessageFormat.STRING, str)
        }

      def consume(request: ConsumeRequest): ZStream[Env, Throwable, LogicMessage] = {
        val stream =
          for {
            _ <- ZStream.fromEffect(log.debug(s"Consuming request: $request"))
            cachedProtoDeserializer <- ZStream.fromEffect(
                                         clustersConfigService
                                           .getCluster(request.clusterId)
                                           .flatMap(c =>
                                             ZIO
                                               .fromOption(c.protoSerdeSettings)
                                               .orElseFail(new RuntimeException("SchemaRegistry url was not provided!"))
                                           )
                                           .flatMap(protobufDeserializer)
                                           .zipLeft(log.debug(s"Created proto deserializer"))
                                           .cached(Duration.Infinity)
                                       )
            response <- Consumer
                          .subscribeAnd(Subscription.topics(request.topicName))
                          .plainStream(Deserializer.string, Deserializer.byteArray)
                          .mapM { r =>
                            request.messageFormat match {
                              case MessageFormat.AUTO =>
                                deserializeAuto(
                                  MessageFormat.PROTOBUF,
                                  cachedProtoDeserializer,
                                  r,
                                  _ => deserializeString(r)
                                )
                              case MessageFormat.PROTOBUF =>
                                deserializeAuto(
                                  MessageFormat.PROTOBUF,
                                  cachedProtoDeserializer,
                                  r,
                                  e =>
                                    UIO(
                                      LogicMessage(
                                        offset = r.offset.offset,
                                        partition = r.partition,
                                        timestamp = r.timestamp,
                                        key = r.key,
                                        valueFormat = MessageFormat.PROTOBUF,
                                        value = e.getMessage
                                      )
                                    )
                                )
                              case _ =>
                                deserializeString(r)
                            }
                          }
          } yield response

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
