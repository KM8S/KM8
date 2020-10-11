package io.kafkamate
package service

import io.grpc.Status
import zio.{ULayer, ZEnv, ZIO}
import zio.stream.ZStream

import kafka.KafkaConsumer
import kafka.KafkaConsumer.KafkaConsumer
import kafka.KafkaProducer
import kafka.KafkaProducer.KafkaProducer

object KafkaMate {
  type Env = ZEnv with KafkaConsumer with KafkaProducer

  lazy val liveLayer: ULayer[Env] =
    ZEnv.live ++ KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer

  object Service extends ZioKafkamate.RKafkaMateService[Env] {
    override def produceMessage(request: Request): ZIO[Env, Status, Response] =
      KafkaProducer
        .produce(request.topic, request.key, request.value)
        .bimap(Status.fromThrowable, _ => Response(s"$request produced successfully!"))

    override def consumeMessages(request: Request): ZStream[Env, Status, Message] =
      ZStream
        .unwrap(KafkaConsumer.consumeStream(request.topic))
        .mapError(Status.fromThrowable)
  }
}
