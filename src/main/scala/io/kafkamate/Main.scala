package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import service.KafkaMate
import kafka.consumer.KafkaConsumer
import kafka.producer.KafkaProducer

object Main extends ServerMain {

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(KafkaMate.Service)
      .provideLayer(ZEnv.live ++ KafkaProducer.liveLayer ++ KafkaConsumer.liveLayer)

}
