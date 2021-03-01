package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.ZEnv

import config._
import grpc._
import kafka._
import utils._

object Main extends ServerMain {

  override def port: Int = 61235

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(ClustersService.GrpcService)
      .add(BrokersService.GrpcService)
      .add(TopicsService.GrpcService)
      .add(MessagesService.GrpcService)
      .provideLayer(
        ZEnv.live >+>
          Logger.liveLayer >+>
          ConfigPathService.liveLayer >+>
          ClustersConfig.liveLayer >+>
          MessagesService.liveLayer >+>
          KafkaExplorer.liveLayer
      )

}
