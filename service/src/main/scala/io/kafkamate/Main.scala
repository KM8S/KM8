package io.kafkamate

import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import scalapb.zio_grpc.{ManagedServer, ServiceList}
import zio._

import io.kafkamate.config._
import io.kafkamate.grpc._
import io.kafkamate.grpc._
import io.kafkamate.kafka._
import io.kafkamate.utils._

object Main extends App {

  private val port = 61235

  def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    val builder =
      ServerBuilder
        .forPort(port)
        .addService(ProtoReflectionService.newInstance())
    val services =
      ServiceList
        .add(ClustersService.GrpcService)
        .add(BrokersService.GrpcService)
        .add(TopicsService.GrpcService)
        .add(MessagesService.GrpcService)
    ManagedServer
      .fromServiceList(builder, services)
      .provideLayer(
        ZEnv.live >+>
          Logger.liveLayer >+>
          ConfigPathService.liveLayer >+>
          ClustersConfig.liveLayer >+>
          MessagesService.liveLayer >+>
          KafkaExplorer.liveLayer
      )
      .useForever
      .exitCode
  }

}
