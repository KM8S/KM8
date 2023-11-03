package io.kafkamate
package kafka

import java.nio.ByteBuffer
import java.util.UUID
import scala.util.{Failure, Success, Try}

import com.google.protobuf.util.JsonFormat
import com.google.protobuf.{Message, MessageOrBuilder}
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import io.kafkamate.config.ClustersConfig._
import io.kafkamate.config._
import io.kafkamate.kafka.KafkaExplorer._
import io.kafkamate.messages._
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
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
            } yield OffsetRetrieval.Manual(f)
          case v => ZIO.fail(new IllegalArgumentException(s"Unrecognized OffsetStrategy: $v"))
        }

      private def consumerSettings(
        config: ClusterSettings,
        request: ConsumeRequest
      ): RIO[Env, ConsumerSettings] =
        for {
          offsetRetrieval <- extractOffsetStrategy(request)
          manualResetOffset = offsetRetrieval match {
            case _: OffsetRetrieval.Manual => Map(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> "earliest")
            case _                         => Map.empty[String, AnyRef]
          }
        } yield {
          val uuid = UUID.randomUUID().toString
          ConsumerSettings(config.kafkaHosts)
            .withGroupId(s"group-kafkamate-$uuid")
            .withClientId(s"client-kafkamate-$uuid")
            .withProperties(config.protoSerdeSettings.map(_.configs).getOrElse(Map.empty))
            .withOffsetRetrieval(offsetRetrieval)
            .withProperties(manualResetOffset)
            .withCloseTimeout(10.seconds)
        }

      private def makeConsumerLayer(request: ConsumeRequest) =
        ZLayer.fromManaged {
          for {
            cs <- clustersConfigService.getCluster(request.clusterId).toManaged_
            settings <- consumerSettings(cs, request).toManaged_
            consumer <- Consumer.make(settings)
          } yield consumer
        }

      private def toJson(messageOrBuilder: MessageOrBuilder): Task[String] =
        Task(JsonFormat.printer.print(messageOrBuilder))

      private def protobufDeserializer(settings: ProtoSerdeSettings): Task[Deserializer[Any, Try[Message]]] =
        Deserializer
          .fromKafkaDeserializer(new KafkaProtobufDeserializer[Message](), settings.configs, false)
          .map(_.asTry)

      private def getSchemaId(record: CommittableRecord[String, Array[Byte]]): URIO[Logging, Option[Int]] =
        Task {
          val buffer = ByteBuffer.wrap(record.value)
          if (buffer.get != 0x0) throw new SerializationException("Unknown magic byte for schema id!")
          buffer.getInt()
        }.asSome.catchAll { t =>
          log
            .throwable(
              s"Failed extracting schema id with key: '${record.key}'; will ignore; error message: ${t.getMessage}",
              t
            )
            .as(None)
        }

      private def asLogicMessage(
        record: CommittableRecord[String, Array[Byte]],
        valueFormat: MessageFormat,
        valueSchemaId: Option[Int],
        valueStr: Option[String]
      ) =
        LogicMessage(
          offset = record.offset.offset,
          partition = record.partition,
          timestamp = record.timestamp,
          key = Option(record.key),
          valueFormat = valueFormat,
          valueSchemaId = valueSchemaId,
          value = valueStr
        )

      private def deserializeAuto(
        messageFormat: MessageFormat,
        cachedDeserializer: RIO[Logging, Deserializer[Any, Try[Message]]],
        record: CommittableRecord[String, Array[Byte]],
        fallback: Throwable => Task[LogicMessage]
      ) =
        for {
          d <- cachedDeserializer
          tmp = for {
            pair <- d.deserialize(record.record.topic, record.record.headers, record.value) <&> getSchemaId(record)
            r <- pair match {
              case (Success(message), schemaId) =>
                toJson(message).map(jsonStr => asLogicMessage(record, MessageFormat.PROTOBUF, schemaId, Some(jsonStr)))
              case (Failure(e), _) =>
                Task.fail(e)
            }
          } yield r
          r <- tmp.catchAll { t =>
            log.throwable(
              s"Failed auto deserializing $messageFormat with key: '${record.key}'; error message: ${t.getMessage}",
              t) *> fallback(t)
          }
        } yield r

      private def deserializeString(record: CommittableRecord[String, Array[Byte]]) =
        Deserializer
          .string
          .deserialize(record.record.topic, record.record.headers, record.value)
          .map(str => asLogicMessage(record, MessageFormat.STRING, None, Some(str)))

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
                .memoize
            )
            response <- Consumer
              .subscribeAnd(Subscription.topics(request.topicName))
              .plainStream(Deserializer.string, Deserializer.byteArray)
              .mapM { record =>
                request.messageFormat match {
                  case MessageFormat.AUTO =>
                    deserializeAuto(
                      MessageFormat.PROTOBUF,
                      cachedProtoDeserializer,
                      record,
                      _ => deserializeString(record)
                    )
                  case MessageFormat.PROTOBUF =>
                    deserializeAuto(
                      MessageFormat.PROTOBUF,
                      cachedProtoDeserializer,
                      record,
                      e => UIO(asLogicMessage(record, MessageFormat.PROTOBUF, None, Option(e.getMessage)))
                    )
                  case _ =>
                    deserializeString(record)
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
