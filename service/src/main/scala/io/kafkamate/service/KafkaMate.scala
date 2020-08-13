package io.kafkamate
package service

import io.grpc.Status
import zio.{ZEnv, ZIO}
import zio.stream.ZStream

import kafka.KafkaConsumer
import kafka.KafkaProducer
import kafkamate.{Message, Request, Response}
import kafkamate.ZioKafkamate

object KafkaMate {
  type Env = ZEnv with KafkaConsumer.KafkaConsumer with KafkaProducer.KafkaProducer

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
