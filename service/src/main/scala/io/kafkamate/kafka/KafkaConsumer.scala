package io.kafkamate
package kafka

import java.util.UUID
import scala.jdk.CollectionConverters._
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
      private def extractOffsetStrategy(offsetValue: String): AutoOffsetStrategy =
        offsetValue match {
          case "earliest" => AutoOffsetStrategy.Earliest
          case _          => AutoOffsetStrategy.Latest
        }

      private def protobufDeserializer(settings: ProtoSerdeSettings): Deserializer[Any, Try[Message]] =
        Deserializer {
          val protoDeser = new KafkaProtobufDeserializer[Message]()
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

      def toJson(messageOrBuilder: MessageOrBuilder): Task[String] =
        Task(JsonFormat.printer.print(messageOrBuilder))

      private def makeConsumerLayer(clusterId: String, offsetStrategy: String) =
        ZLayer.fromManaged {
          for {
            cs       <- clustersConfigService.getCluster(clusterId).toManaged_
            settings <- consumerSettings(cs, offsetStrategy).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      private def deserializeProto(
        cachedDeserializer: Task[Deserializer[Any, Try[Message]]],
        record: CommittableRecord[String, Array[Byte]],
        fallback: Throwable => Task[LogicMessage]
      ) =
        cachedDeserializer.flatMap {
          _.deserialize(record.record.topic, record.record.headers, record.value).flatMap {
            case Success(message) =>
              toJson(message).map { str =>
                LogicMessage(record.offset.offset, record.partition, record.timestamp, record.key, str)
              }
            case Failure(e) =>
              Task.fail(e)
          }
            .catchAll(t => fallback(t))
        }

      private def deserializeString(v: CommittableRecord[String, Array[Byte]]) =
        Deserializer.string.deserialize(v.record.topic, v.record.headers, v.value).map { str =>
          LogicMessage(v.offset.offset, v.partition, v.timestamp, v.key, str)
        }

      def consume(request: ConsumeRequest): ZStream[Env, Throwable, LogicMessage] = {
        val stream =
          for {
            cachedDeserializer <- ZStream.fromEffect(
                                    clustersConfigService
                                      .getCluster(request.clusterId)
                                      .flatMap(c =>
                                        ZIO
                                          .fromOption(c.protoSerdeSettings)
                                          .orElseFail(new Exception("SchemaRegistry url was not provided!"))
                                      )
                                      .map(protobufDeserializer)
                                      .cached(Duration.Infinity)
                                  )
            response <- Consumer
                          .subscribeAnd(Subscription.topics(request.topicName))
                          .plainStream(Deserializer.string, Deserializer.byteArray)
                          .mapM { r =>
                            request.messageFormat match {
                              case MessageFormat.AUTO =>
                                deserializeProto(
                                  cachedDeserializer,
                                  r,
                                  _ => deserializeString(r)
                                )
                              case MessageFormat.PROTOBUF =>
                                deserializeProto(
                                  cachedDeserializer,
                                  r,
                                  e => UIO(LogicMessage(r.offset.offset, r.partition, r.timestamp, r.key, e.getMessage))
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
