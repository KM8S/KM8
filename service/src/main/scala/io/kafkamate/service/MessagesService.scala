package io.kafkamate
package service

import io.grpc.Status
import zio.{ULayer, ZEnv, ZIO}
import zio.stream.ZStream

import kafka.KafkaConsumer
import kafka.KafkaProducer
import messages._

object MessagesService {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer

  lazy val liveLayer: ULayer[Env] =
    ZEnv.live ++ KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer

  object Service extends ZioMessages.RMessagesService[Env] {
    override def produceMessage(request: ProduceRequest): ZIO[Env, Status, ProduceResponse] =
      KafkaProducer
        .produce(request.topicName, request.key, request.value)
        .bimap(Status.fromThrowable, _ => ProduceResponse(s"$request produced successfully!"))

    override def consumeMessages(request: ConsumeRequest): ZStream[Env, Status, Message] =
      ZStream
        .unwrap(KafkaConsumer.consumeStream(request.topicName))
        .mapError(Status.fromThrowable)
  }
}
