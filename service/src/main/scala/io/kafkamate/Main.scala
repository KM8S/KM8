package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import service._

object Main extends ServerMain {

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(KafkaMate.Service)
      .add(TopicService.Service)
      .provideLayer(KafkaMate.liveLayer ++ TopicService.liveLayer)

}
