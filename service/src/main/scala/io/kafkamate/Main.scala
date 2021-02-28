package io.kafkamate

import scalapb.zio_grpc.{ServerMain, ServiceList}
import zio.{ZEnv, URLayer}
import zio.logging._
import zio.console.Console
import zio.clock.Clock

import config._
import grpc._
import kafka._

object Main extends ServerMain {

  val liveLoggingLayer: URLayer[Console with Clock, Logging] =
    Logging.console(
      logLevel = LogLevel.Info,
      format = LogFormat.ColoredLogFormat()
    ) >>> Logging.withRootLoggerName("kafkamate")

  override def services: ServiceList[ZEnv] =
    ServiceList
      .add(ClustersService.GrpcService)
      .add(BrokersService.GrpcService)
      .add(TopicsService.GrpcService)
      .add(MessagesService.GrpcService)
      .provideLayer(
        ZEnv.live >+>
          liveLoggingLayer >+>
          ConfigPathService.liveLayer >+>
          ClustersConfig.liveLayer >+>
          MessagesService.liveLayer >+>
          KafkaExplorer.liveLayer
      )

}
