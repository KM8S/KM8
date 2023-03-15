package io.kafkamate
package grpc

import io.grpc.Status
import zio.{URLayer, ZEnv, ZIO, ZLayer}
import zio.stream.ZStream
import zio.logging._

import config.ClustersConfig._
import kafka.KafkaConsumer
import kafka.KafkaProducer
import messages._
import utils._

object MessagesService {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer with Logging

  lazy val liveLayer: URLayer[ZEnv with Logging with ClustersConfigService, Env] =
    ZEnv.any ++
      ZLayer.requires[Logging] ++
      ZLayer.requires[ClustersConfigService] >+>
      KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer

  object GrpcService extends ZioMessages.RMessagesService[Env] {
    override def produceMessage(request: ProduceRequest): ZIO[Env, Status, ProduceResponse] =
      KafkaProducer
        .produce(request)
        .tapError(e => log.throwable(s"Producer error: ${e.getMessage}", e))
        .bimap(GRPCStatus.fromThrowable, _ => ProduceResponse("OK"))

    override def consumeMessages(request: ConsumeRequest): ZStream[Env, Status, Message] =
      KafkaConsumer
        .consume(request)
        .onError(e => log.error("Consumer error: \n" + e.prettyPrint, e))
        .mapError(GRPCStatus.fromThrowable)
  }
}
