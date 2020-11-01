package io.kafkamate
package grpc

import io.grpc.Status
import zio.{URLayer, ZEnv, ZIO}
import zio.stream.ZStream

import kafka.KafkaConsumer
import kafka.KafkaProducer
import messages._

object MessagesService {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer

  lazy val liveLayer: URLayer[ZEnv, Env] =
    ZEnv.any ++ KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer

  object GrpcService extends ZioMessages.RMessagesService[Env] {
    override def produceMessage(request: ProduceRequest): ZIO[Env, Status, ProduceResponse] =
      KafkaProducer
        .produce(request.topicName, request.key, request.value)(request.clusterId)
        .tapError(e => zio.UIO(println(s"---------------------- kafka produce error: ${e.getMessage}")))
        .bimap(Status.fromThrowable, _ => ProduceResponse(s"$request produced successfully!"))

    override def consumeMessages(request: ConsumeRequest): ZStream[Env, Status, Message] =
      KafkaConsumer
        .consumeStream(request.topicName)(request.clusterId)
        .onError(e => zio.UIO(println("----------------------\n" + e.prettyPrint)))
        .mapError(Status.fromThrowable)
  }
}
