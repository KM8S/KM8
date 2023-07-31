package io.kafkamate
package grpc

import io.grpc.Status
import zio.{ZEnv, ZIO}
import zio.logging._

import kafka.KafkaExplorer
import topics._
import utils._

object TopicsService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer with Logging

  object GrpcService extends ZioTopics.RTopicsService[Env] {
    def getTopics(request: GetTopicsRequest): ZIO[Env, Status, TopicResponse] =
      KafkaExplorer
        .listTopics(request.clusterId)
        .tapError(e => log.throwable(s"Get topics error: ${e.getMessage}", e))
        .mapBoth(GRPCStatus.fromThrowable, r => TopicResponse(r))

    def addTopic(request: AddTopicRequest): ZIO[Env, Status, TopicDetails] =
      KafkaExplorer
        .addTopic(request)
        .tapError(e => log.throwable(s"Add topic error: ${e.getMessage}", e))
        .mapError(GRPCStatus.fromThrowable)

    def deleteTopic(request: DeleteTopicRequest): ZIO[Env with Any, Status, DeleteTopicResponse] =
      KafkaExplorer
        .deleteTopic(request)
        .tapError(e => log.throwable(s"Delete topic error: ${e.getMessage}", e))
        .mapError(GRPCStatus.fromThrowable)
  }
}
