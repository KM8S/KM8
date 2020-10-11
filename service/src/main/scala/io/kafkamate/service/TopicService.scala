package io.kafkamate
package service

import io.grpc.Status
import zio.{ULayer, ZEnv, ZIO}

import kafka.TopicExplorer
import io.topics._

object TopicService {
  type Env = ZEnv with TopicExplorer.HasTopicExplorer

  lazy val liveLayer: ULayer[Env] =
    ZEnv.live ++ TopicExplorer.liveLayer.orDie

  object Service extends ZioTopics.RTopicsService[Env] {
    def getTopics(request: TopicRequest): ZIO[Env, Status, TopicResponse] =
      TopicExplorer
        .listTopics
        .bimap(Status.fromThrowable, r => TopicResponse(r))
  }
}
