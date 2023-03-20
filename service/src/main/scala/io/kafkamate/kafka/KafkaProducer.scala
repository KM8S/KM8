package io.kafkamate
package kafka

import zio._
import zio.blocking._
import zio.magic._
import zio.kafka.serde._
import zio.kafka.producer._
import zio.macros.accessible
import config._
import ClustersConfig._
import com.google.protobuf.{Descriptors, DynamicMessage, Message}
import com.google.protobuf.util.JsonFormat
import io.confluent.kafka.formatter.SchemaMessageSerializer
import io.confluent.kafka.schemaregistry.{ParsedSchema, SchemaProvider}
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.protobuf.{ProtobufSchema, ProtobufSchemaProvider}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufSerializer
import io.kafkamate.messages._

import scala.jdk.CollectionConverters._

@accessible object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {
    def getSchemaSubjects(request: GetSchemaSubjectRequest): Task[SchemaSubjectResponse]
    def produce(request: ProduceRequest): RIO[Blocking, Unit]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, KafkaProducer] =
    ZLayer.fromService { clusterConfigService =>
      new Service {
        lazy val serdeLayer: ULayer[Has[Serializer[Any, String]] with Has[Serializer[Any, Array[Byte]]]] =
          UIO(Serde.string).toLayer[Serializer[Any, String]] ++
            UIO(Serde.byteArray).toLayer[Serializer[Any, Array[Byte]]]

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

//        lazy val serializer: KafkaProtobufSerializer[Message] = {
//          val r = new KafkaProtobufSerializer[Message](/*schemaRegistryClient*/)
//          val cfg = Map(
////            "reference.subject.name.strategy" -> "io.confluent.kafka.serializers.subject.TopicNameStrategy",
//            "schema.registry.url" -> "http://localhost:8081",
//            "auto.register.schema" -> "true"
//          )
//          r.configure(cfg.asJava, false)
//          r
//        }

        def readFrom(valueString: String, valueSchema: ParsedSchema, valueDescriptor: Option[String]): Task[Message] =
          Task {
            val protobufSchema = valueSchema.asInstanceOf[ProtobufSchema]
            val schemaDescriptor: Descriptors.Descriptor =
              valueDescriptor.fold(protobufSchema.toDescriptor)(name => protobufSchema.toDescriptor(name))
            val builder = DynamicMessage.newBuilder(schemaDescriptor)
            JsonFormat.parser.merge(valueString, builder)
            builder.build
          }.tapError(e =>
            ZIO.debug(s"Error (${e.getMessage}) while reading from ($valueString) and schema ($valueSchema)")
          )

        def readMessage(request: ProduceProtoRequest): RIO[Has[ClusterSettings], Array[Byte]] =
          for {
            _           <- ZIO.debug(s"request:\n$request")
            registry    <- getSchemaRegistryClient
            valueSchema <- getSchema(registry, request.schemaId)
            value       <- readFrom(request.value, valueSchema, request.valueDescriptor)
            _           <- ZIO.debug(s"value message:\n$value")
            serializer  <- getSerializer(registry)
            bytes <-
              Task(serializer.serialize(request.valueSubject, request.topicName, isKey = false, value, valueSchema))
//            bytes <- Task(serializer.serialize(topic, value))
          } yield bytes

        def producerLayer(
          clusterId: String
        ): RLayer[Blocking, Producer[Any, String, Array[Byte]] with Has[ClusterSettings]] =
          ZLayer.wireSome[Blocking, Producer[Any, String, Array[Byte]] with Has[ClusterSettings]](
            serdeLayer,
            custerSettingsLayer(clusterId),
            settingsLayer,
            Producer.live[Any, String, Array[Byte]]
          )

        override def produce(produceRequest: ProduceRequest): RIO[Blocking, Unit] =
          produceRequest.request match {
            case ProduceRequest.Request.ProtoRequest(r) =>
              readMessage(r).flatMap { bytes =>
                Producer
                  .produce[Any, String, Array[Byte]](r.topicName, r.key, bytes)
                  .unit
              }.provideSomeLayer[Blocking](producerLayer(r.clusterId))
            case ProduceRequest.Request.StringRequest(r) =>
              Producer
                .produce[Any, String, Array[Byte]](r.topicName, r.key, r.value.getBytes)
                .unit
                .provideSomeLayer[Blocking](producerLayer(r.clusterId))
            case _ => ZIO.fail(new RuntimeException("Not implemented!"))
          }

        override def getSchemaSubjects(request: GetSchemaSubjectRequest): Task[SchemaSubjectResponse] = {
          val process = for {
            baseUrl <- ZIO
                         .service[ClusterSettings]
                         .map(_.schemaRegistryUrl)
                         .someOrFail(new RuntimeException("Schema registry url not provided!"))
            subject   = s"${request.topicName}-value"
            registry <- getSchemaRegistryClient
            versions <- Task(registry.getAllVersions(subject).asScala.toList)
            metas    <- ZIO.foreachPar(versions)(v => Task(registry.getSchemaMetadata(subject, v)))
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
    useLatest: Boolean = true
  ) extends AbstractKafkaProtobufSerializer[Message]
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
