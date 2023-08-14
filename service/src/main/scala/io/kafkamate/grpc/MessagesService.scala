package io.kafkamate
package grpc

import io.grpc.Status
import io.kafkamate.config.ClustersConfig._
import io.kafkamate.kafka.KafkaExplorer.HasKafkaExplorer
import io.kafkamate.kafka.{KafkaConsumer, KafkaProducer}
import io.kafkamate.messages._
import io.kafkamate.utils._
import zio.logging._
import zio.magic._
import zio.stream.ZStream
import zio.{URLayer, ZEnv, ZIO, ZLayer}

object MessagesService {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer with HasKafkaExplorer with Logging

  lazy val liveLayer: URLayer[ZEnv with Logging with ClustersConfigService with HasKafkaExplorer, Env] =
    ZLayer.wireSome[ZEnv with Logging with ClustersConfigService with HasKafkaExplorer, Env](
      KafkaProducer.liveLayer,
      KafkaConsumer.liveLayer
    )

  object GrpcService extends ZioMessages.RMessagesService[Env] {

    override def produceMessage(request: ProduceRequest): ZIO[Env, Status, ProduceResponse] =
      KafkaProducer
        .produce(request)
        .tapError(e => log.throwable(s"Producer error: ${e.getMessage}", e))
        .mapBoth(GRPCStatus.fromThrowable, _ => ProduceResponse("OK"))

    override def consumeMessages(request: ConsumeRequest): ZStream[Env, Status, LogicMessage] =
      KafkaConsumer
        .consume(request)
        .onError(e => log.error("Consumer error: \n" + e.prettyPrint, e))
        .mapError(GRPCStatus.fromThrowable)

    override def getSchemaSubject(request: GetSchemaSubjectRequest): ZIO[Env, Status, SchemaSubjectResponse] =
      KafkaProducer
        .getSchemaSubjects(request)
        .tapError(e => log.throwable(s"Error retrieving schemas: ${e.getMessage}", e))
        .mapError(GRPCStatus.fromThrowable)
  }
}
