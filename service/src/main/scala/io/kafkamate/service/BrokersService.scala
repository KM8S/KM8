package io.kafkamate
package service

import io.grpc.Status
import zio.{ULayer, ZEnv, ZIO}

import brokers._
import kafka.KafkaExplorer

object BrokersService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer

  lazy val liveLayer: ULayer[Env] =
    ZEnv.live ++ KafkaExplorer.liveLayer.orDie

  object Service extends ZioBrokers.RBrokersService[Env] {
    def getBrokers(request: BrokerRequest): ZIO[Env, Status, BrokerResponse] =
      KafkaExplorer
        .listBrokers
        .bimap(Status.fromThrowable, r => BrokerResponse(r))
  }
}
