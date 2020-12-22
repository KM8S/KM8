package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import config._
import grpc._
import kafka._

object Main extends ServerMain {

  //todo test with multiple kafka versions
  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(ClustersService.GrpcService)
      .add(BrokersService.GrpcService)
      .add(TopicsService.GrpcService)
      .add(MessagesService.GrpcService)
      .provideLayer(
        ZEnv.live >+>
          MessagesService.liveLayer ++
          ClustersConfig.liveLayer ++
          KafkaExplorer.liveLayer
      )

}
