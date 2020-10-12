package io.kafkamate
package service

import io.grpc.Status
import zio.{ULayer, ZEnv, ZIO}

import kafka.KafkaExplorer
import topics._

object TopicsService {
  type Env = ZEnv with KafkaExplorer.HasKafkaExplorer

  lazy val liveLayer: ULayer[Env] =
    ZEnv.live ++ KafkaExplorer.liveLayer.orDie

  object Service extends ZioTopics.RTopicsService[Env] {
    def getTopics(request: TopicRequest): ZIO[Env, Status, TopicResponse] =
      KafkaExplorer
        .listTopics
        .bimap(Status.fromThrowable, r => TopicResponse(r))
  }
}
