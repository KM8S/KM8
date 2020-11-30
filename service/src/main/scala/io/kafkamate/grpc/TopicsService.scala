package io.kafkamate
package grpc

import io.grpc.Status
import zio.{ZEnv, ZIO}

import kafka.KafkaExplorer
import topics._

object TopicsService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer

  object GrpcService extends ZioTopics.RTopicsService[Env] {
    def getTopics(request: GetTopicsRequest): ZIO[Env, Status, TopicResponse] =
      KafkaExplorer
        .listTopics(request.clusterId)
        .tapError(e => zio.UIO(println(s"---------------------- Got topics error: ${e.getMessage}")))
        .bimap(Status.fromThrowable, r => TopicResponse(r)) //todo better error status codes

    def addTopic(request: AddTopicRequest): ZIO[Env, Status, TopicDetails] =
      KafkaExplorer
        .addTopic(request)
        .tapError(e => zio.UIO(println(s"---------------------- Got add topic error: ${e.getMessage}")))
        .mapError(Status.fromThrowable) //todo better error status codes

    def deleteTopic(request: DeleteTopicRequest): ZIO[Env with Any, Status, DeleteTopicResponse] =
      KafkaExplorer
        .deleteTopic(request)
        .tapError(e => zio.UIO(println(s"---------------------- Got delete topic error: ${e.getMessage}")))
        .mapError(Status.fromThrowable) //todo better error status codes
  }
}
