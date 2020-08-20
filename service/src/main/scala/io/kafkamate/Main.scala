package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import service.KafkaMate

object Main extends ServerMain {

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(KafkaMate.Service)
      .provideLayer(KafkaMate.liveLayer)

}
