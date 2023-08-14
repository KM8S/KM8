package io.kafkamate
package kafka

import java.util.UUID
import scala.util.{Failure, Success, Try}

import com.google.protobuf.util.JsonFormat
import com.google.protobuf.{Message, MessageOrBuilder}
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import io.kafkamate.config.ClustersConfig._
import io.kafkamate.config._
import io.kafkamate.kafka.KafkaExplorer._
import io.kafkamate.messages._
import org.apache.kafka.common.TopicPartition
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.consumer.Consumer._
import zio.kafka.consumer._
import zio.kafka.serde.Deserializer
import zio.logging._
import zio.macros.accessible
import zio.stream.ZStream

@accessible object KafkaConsumer {
  type KafkaConsumer = Has[Service]
  type Env = Clock with Blocking with Logging with HasKafkaExplorer

  trait Service {
    def consume(request: ConsumeRequest): ZStream[Env, Throwable, LogicMessage]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, KafkaConsumer] =
    ZLayer.fromService(createService)

  private def createService(clustersConfigService: ClustersConfig.Service): Service =
    new Service {

      private def safeOffset(maxResults: Long)(offset: Long): Long = {
        val r = offset - maxResults
        if (r < 0L) 0L else r
      }

      private def extractOffsetStrategy(request: ConsumeRequest): RIO[Env, OffsetRetrieval] =
        request.offsetStrategy match {
          case OffsetStrategy.REAL_TIME      => UIO(OffsetRetrieval.Auto(AutoOffsetStrategy.Latest))
          case OffsetStrategy.FROM_BEGINNING => UIO(OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest))
          case OffsetStrategy.LATEST =>
            for {
              b <- ZIO.service[Blocking.Service]
              c <- ZIO.service[Clock.Service]
              k <- ZIO.service[KafkaExplorer.Service]
              dep = Has.allOf(b, c, k)
              f = (tps: Set[TopicPartition]) =>
                KafkaExplorer
                  .getLatestOffset(request.clusterId, tps)
                  .map(_.view.mapValues(safeOffset(request.maxResults)).toMap)
                  .provide(dep)
              r = OffsetRetrieval.Manual(f)
            } yield r
          case v => ZIO.fail(new IllegalArgumentException(s"Unrecognized OffsetStrategy: $v"))
        }

      private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[Message]]] =
        Deserializer
          .fromKafkaDeserializer(new KafkaProtobufDeserializer[Message](), settings.configs, false)
          .map(_.asTry)

      private def consumerSettings(
        config: ClusterSettings,
        request: ConsumeRequest
      ): RIO[Env, ConsumerSettings] =
        for {
          offsetRetrieval <- extractOffsetStrategy(request)
        } yield {
          val uuid = UUID.randomUUID().toString
          ConsumerSettings(config.kafkaHosts)
            .withGroupId(s"group-kafkamate-$uuid")
            .withClientId(s"client-kafkamate-$uuid")
            .withProperties(config.protoSerdeSettings.map(_.configs).getOrElse(Map.empty))
            .withOffsetRetrieval(offsetRetrieval)
            .withCloseTimeout(10.seconds)
        }

      def toJson(messageOrBuilder: MessageOrBuilder): Task[String] =
        Task(JsonFormat.printer.print(messageOrBuilder))

      private def makeConsumerLayer(request: ConsumeRequest) =
        ZLayer.fromManaged {
          for {
            cs <- clustersConfigService.getCluster(request.clusterId).toManaged_
            settings <- consumerSettings(cs, request).toManaged_
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
              *> fallback(t))
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
                    .orElseFail(new RuntimeException("SchemaRegistry url was not provided!")))
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
          makeConsumerLayer(request)
        )
      }
    }
}
