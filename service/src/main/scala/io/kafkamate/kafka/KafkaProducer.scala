package io.kafkamate
package kafka

import scala.jdk.CollectionConverters._

import com.google.protobuf.util.JsonFormat
import com.google.protobuf.{Descriptors, DynamicMessage, Message}
import io.confluent.kafka.formatter.SchemaMessageSerializer
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.protobuf.{ProtobufSchema, ProtobufSchemaProvider}
import io.confluent.kafka.schemaregistry.{ParsedSchema, SchemaProvider}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufSerializer
import io.kafkamate.config.ClustersConfig._
import io.kafkamate.config._
import io.kafkamate.messages._
import zio._
import zio.blocking._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.logging._
import zio.macros.accessible
import zio.magic._

@accessible object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {
    def getSchemaSubjects(request: GetSchemaSubjectRequest): Task[SchemaSubjectResponse]
    def produce(request: ProduceRequest): RIO[Blocking, Unit]
  }

  lazy val liveLayer: URLayer[ClustersConfigService with Logging, KafkaProducer] =
    ZLayer.fromServices[ClustersConfig.Service, Logger[String], KafkaProducer.Service] {
      (clusterConfigService, logging) =>
        new Service {
          lazy val providers: List[SchemaProvider] =
            List(new ProtobufSchemaProvider())

          lazy val producerConfig: Map[String, String] =
            Map("auto.register.schema" -> "true")

          def custerSettingsLayer(clusterId: String): TaskLayer[Has[ClusterSettings]] =
            clusterConfigService.getCluster(clusterId).toLayer

          def settingsLayer: URLayer[Has[ClusterSettings], Has[ProducerSettings]] =
            ZIO
              .service[ClusterSettings]
              .map(c => ProducerSettings(c.kafkaHosts))
              .toLayer

          def getSchemaRegistryClient: RIO[Has[ClusterSettings], CachedSchemaRegistryClient] =
            ZIO
              .service[ClusterSettings]
              .map(_.schemaRegistryUrl)
              .someOrFail(new RuntimeException("Schema registry url not provided!"))
              .mapEffect { schemaRegistryUrl =>
                new CachedSchemaRegistryClient(
                  List(schemaRegistryUrl).asJava,
                  AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_DEFAULT,
                  providers.asJava,
                  producerConfig.asJava
                )
              }

          def getSchema(registry: CachedSchemaRegistryClient, id: Int): Task[ParsedSchema] =
            Task(registry.getSchemaById(id))

          def getSerializer(registry: CachedSchemaRegistryClient): Task[KM8ProtobufMessageSerializer] =
            Task(KM8ProtobufMessageSerializer(registry))

          private def parseSchema(valueString: String, schemaDescriptor: Descriptors.Descriptor): Task[Message] =
            Task {
              val builder = DynamicMessage.newBuilder(schemaDescriptor)
              JsonFormat.parser.merge(valueString, builder)
              builder.build
            }.tapBoth(
              e =>
                logging.throwable(
                  s"Error while parsing schema with descriptor ${schemaDescriptor.getName}: ${e.getMessage}",
                  e
                ),
              _ => logging.debug(s"Success parsing schema with descriptor: ${schemaDescriptor.getName}")
            )

          private def extractDescriptors(valueDescriptor: Option[String], protobufSchema: ProtobufSchema) =
            Task {
              valueDescriptor match {
                case Some(name) => List(protobufSchema.toDescriptor(name))
                case None =>
                  List(protobufSchema.toDescriptor) ++
                    protobufSchema.toDynamicSchema.getMessageTypes.asScala
                      .map(protobufSchema.toDescriptor)
              }
            }

          def readFrom(
            valueString: String,
            valueSchema: ParsedSchema,
            valueDescriptor: Option[String]
          ): Task[Message] = {
            val process = for {
              protobufSchema <- Task(valueSchema.asInstanceOf[ProtobufSchema])
              descriptors <- extractDescriptors(valueDescriptor, protobufSchema)
              ios = descriptors.map(parseSchema(valueString, _))
              r <- ZIO.firstSuccessOf(ios.head, ios.tail)
            } yield r

            process.tapError(e =>
              logging.throwable(s"Error while reading from ($valueString) and schema ($valueSchema)", e))
          }

          def readMessage(request: ProduceProtoRequest): RIO[Has[ClusterSettings], Array[Byte]] =
            for {
              _ <- logging.debug(s"request: $request")
              registry <- getSchemaRegistryClient
              valueSchema <- getSchema(registry, request.schemaId)
              value <- readFrom(request.value, valueSchema, request.valueDescriptor)
              _ <- logging.debug(s"value message: $value")
              serializer <- getSerializer(registry)
              bytes <-
                Task(serializer.serialize(request.valueSubject, request.topicName, isKey = false, value, valueSchema))
            } yield bytes

          def producerLayer(
            clusterId: String
          ): RLayer[Blocking, Has[Producer] with Has[ClusterSettings]] =
            ZLayer.wireSome[Blocking, Has[Producer] with Has[ClusterSettings]](
              custerSettingsLayer(clusterId),
              settingsLayer,
              Producer.live
            )

          override def produce(produceRequest: ProduceRequest): RIO[Blocking, Unit] =
            produceRequest.request match {
              case ProduceRequest.Request.ProtoRequest(r) =>
                readMessage(r).flatMap { bytes =>
                  Producer
                    .produce(r.topicName, r.key, bytes, Serde.string, Serde.byteArray)
                    .unit
                }.provideSomeLayer[Blocking](producerLayer(r.clusterId))
              case ProduceRequest.Request.StringRequest(r) =>
                Producer
                  .produce(r.topicName, r.key, r.value.getBytes, Serde.string, Serde.byteArray)
                  .unit
                  .provideSomeLayer[Blocking](producerLayer(r.clusterId))
              case _ => ZIO.fail(new RuntimeException("Not implemented!"))
            }

          override def getSchemaSubjects(request: GetSchemaSubjectRequest): Task[SchemaSubjectResponse] = {
            val process =
              for {
                baseUrl <- ZIO
                  .service[ClusterSettings]
                  .map(_.schemaRegistryUrl)
                  .someOrFail(new RuntimeException("Schema registry url not provided!"))
                subject = s"${request.topicName}-value"
                registry <- getSchemaRegistryClient
                versions <- Task(registry.getAllVersions(subject).asScala.toList)
                metas <- ZIO.foreachPar(versions)(v => Task(registry.getSchemaMetadata(subject, v)))
                schemaUrl = (id: Int) => s"$baseUrl/schemas/ids/$id"
              } yield SchemaSubjectResponse(
                metas.map { meta =>
                  SchemaSubject(meta.getId, schemaUrl(meta.getId))
                }.sortBy(-_.id)
              )

            process.provideLayer(custerSettingsLayer(request.clusterId))
          }

        }
    }

  case class KM8ProtobufMessageSerializer(
    schemaRegistryClient: SchemaRegistryClient,
    autoRegister: Boolean = true,
    useLatest: Boolean = true) extends AbstractKafkaProtobufSerializer[Message]
      with SchemaMessageSerializer[Message] {

    this.schemaRegistry = schemaRegistryClient
    this.autoRegisterSchema = autoRegister
    this.useLatestVersion = useLatest

    override def getKeySerializer = ???

    override def serializeKey(topic: String, payload: Object) = ???

    override def serialize(
      subject: String,
      topic: String,
      isKey: Boolean,
      `object`: Message,
      schema: ParsedSchema
    ): Array[Byte] =
      super.serializeImpl(subject, topic, isKey, `object`, schema.asInstanceOf[ProtobufSchema])
  }

}
