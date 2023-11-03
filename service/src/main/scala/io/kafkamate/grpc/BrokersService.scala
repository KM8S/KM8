package io.kafkamate
package grpc

import io.grpc.Status
import io.kafkamate.brokers._
import io.kafkamate.kafka._
import io.kafkamate.utils._
import zio.logging._
import zio.{ZEnv, ZIO}

object BrokersService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer with Logging

  object GrpcService extends ZioBrokers.RBrokersService[Env] {

    def getBrokers(request: BrokerRequest): ZIO[Env, Status, BrokerResponse] =
      KafkaExplorer
        .listBrokers(request.clusterId)
        .tapError(e => log.throwable(s"Get brokers error: ${e.getMessage}", e))
        .mapBoth(GRPCStatus.fromThrowable, BrokerResponse(_))
  }
}
