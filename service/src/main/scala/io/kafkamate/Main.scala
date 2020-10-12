package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import service._

object Main extends ServerMain {

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(MessagesService.Service)
      .add(TopicsService.Service)
      .add(BrokersService.Service)
      .provideLayer(MessagesService.liveLayer ++ BrokersService.liveLayer)

}
