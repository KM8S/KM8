package io.kafkamate
package grpc

import io.grpc.Status
import zio.{ZEnv, ZIO}

import brokers._
import kafka.KafkaExplorer

object BrokersService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer

  object GrpcService extends ZioBrokers.RBrokersService[Env] {
    def getBrokers(request: BrokerRequest): ZIO[Env, Status, BrokerResponse] =
      KafkaExplorer
        .listBrokers
        .tapError(e => zio.UIO(println(s"---------------------- Get brokers error: ${e.getMessage}")))
        .bimap(Status.fromThrowable, r => BrokerResponse(r))
  }
}
