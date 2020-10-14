package io.kafkamate
package grpc

import io.grpc.Status
import zio.{ZEnv, ZIO}

import kafka.KafkaExplorer
import topics._

object TopicsService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer

  object GrpcService extends ZioTopics.RTopicsService[Env] {
    def getTopics(request: TopicRequest): ZIO[Env, Status, TopicResponse] =
      KafkaExplorer
        .listTopics(request.clusterId)
        .tapError(e => zio.UIO(println(s"---------------------- Got topics error: ${e.getMessage}")))
        .bimap(Status.fromThrowable, r => TopicResponse(r))
  }
}
