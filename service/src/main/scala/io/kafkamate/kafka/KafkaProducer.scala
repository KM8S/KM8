package io.kafkamate
package kafka

import zio._
import zio.blocking._
import zio.kafka.serde._
import zio.kafka.producer._
import zio.macros.accessible
import config._
import ClustersConfig._
import com.google.protobuf.{DynamicMessage, Message}
import com.google.protobuf.util.JsonFormat
import io.confluent.kafka.formatter.SchemaMessageSerializer
import io.confluent.kafka.schemaregistry.{ParsedSchema, SchemaProvider}
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.protobuf.{ProtobufSchema, ProtobufSchemaProvider, ProtobufSchemaUtils}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.protobuf.AbstractKafkaProtobufSerializer

import scala.jdk.CollectionConverters._

@accessible object KafkaProducer {
  type KafkaProducer = Has[Service]

  trait Service {
    def produce(topic: String, key: String, value: String)(clusterId: String): RIO[Blocking, Unit]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, KafkaProducer] =
    ZLayer.fromService { clusterConfigService =>
      new Service {
        lazy val serdeLayer: ULayer[Has[Serializer[Any, String]] with Has[Serializer[Any, Array[Byte]]]] =
          UIO(Serde.string).toLayer[Serializer[Any, String]] ++ UIO(Serde.byteArray).toLayer[Serializer[Any, Array[Byte]]]

        def settingsLayer(clusterId: String): TaskLayer[Has[ProducerSettings]] =
          clusterConfigService
            .getCluster(clusterId)
            .map(c => ProducerSettings(c.kafkaHosts))
            .toLayer

        lazy val providers: List[SchemaProvider] = List(new ProtobufSchemaProvider())

        lazy val schemaRegistryClient = new CachedSchemaRegistryClient(
          List("http://localhost:8081").asJava,
          AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_DEFAULT,
          providers.asJava,
          Map("auto.register.schema" -> "true").asJava
        )

        val schemaId = 7
        def valueSubject(topic: String) = s"$topic-value"

        def getSchema(id: Int): Task[ParsedSchema] =
          Task(schemaRegistryClient.getSchemaById(id))

        lazy val serializer: SchemaMessageSerializer[Message] =
          KM8ProtobufMessageSerializer(schemaRegistryClient)

        def readFrom(jsonString: String, schema: ParsedSchema): Task[Message] =
          Task {
            println("?" * 100)
            //val s = ProtobufSchemaUtils.toObject(jsonString, schema.asInstanceOf[ProtobufSchema]).asInstanceOf[Message]
            val messageBuilder: DynamicMessage.Builder = schema.asInstanceOf[ProtobufSchema].newMessageBuilder
            //JsonFormat.parser.merge(jsonString, messageBuilder)
            JsonFormat.parser().ignoringUnknownFields().merge(jsonString, messageBuilder)
            val s = messageBuilder.build
            println(">" * 100)
            s
          }.tapError(e => ZIO.debug(s"Error (${e.getMessage}) while reading from ($jsonString) and schema ($schema)"))

        def readMessage(topic: String, valueString: String): Task[Array[Byte]] =
          for {
            valueSchema <- getSchema(schemaId)
            value <- readFrom(valueString, valueSchema)
            bytes <- Task(serializer.serialize(valueSubject(topic), topic, false, value, valueSchema))
          } yield bytes

        def producerLayer(clusterId: String): RLayer[Blocking, Producer[Any, String, Array[Byte]]] =
          Blocking.any ++ serdeLayer ++ settingsLayer(clusterId) >>> Producer.live[Any, String, Array[Byte]]

        def produce(topic: String, key: String, value: String)(clusterId: String): RIO[Blocking, Unit] = {
          readMessage(topic, value).flatMap { bytes =>
            Producer
              .produce[Any, String, Array[Byte]](topic, key, bytes)
              .unit
              .provideSomeLayer[Blocking](producerLayer(clusterId))
          }
        }
      }
    }

  case class KM8ProtobufMessageSerializer(
      schemaRegistryClient: SchemaRegistryClient,
      autoRegister: Boolean = true,
      useLatest: Boolean = true
    ) extends AbstractKafkaProtobufSerializer[Message] with SchemaMessageSerializer[Message] {

    this.schemaRegistry = schemaRegistryClient
    this.autoRegisterSchema = autoRegister
    this.useLatestVersion = useLatest

    override def getKeySerializer = ???

    override def serializeKey(topic: String, payload: Object) = ???

    override def serialize(subject: String, topic: String, isKey: Boolean, `object`: Message, schema: ParsedSchema): Array[Byte] =
      super.serializeImpl(subject, topic, isKey, `object`, schema.asInstanceOf[ProtobufSchema])
  }

}
