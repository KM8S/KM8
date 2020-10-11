package io.kafkamate
package kafka

import zio._
import zio.blocking.Blocking
import zio.kafka.admin._
import zio.macros.accessible

import config._
import io.topics.TopicDetails

@accessible object TopicExplorer {

  type HasTopicExplorer = Has[Service]
  type HasAdminClient = Has[AdminClient]

  trait Service {
    def listTopics: RIO[Blocking, List[TopicDetails]]
  }

  lazy val topicExplorerLayer: URLayer[HasAdminClient, HasTopicExplorer] =
    ZLayer.fromService { adminClient =>
      new Service {
        def listTopics: RIO[Blocking, List[TopicDetails]] =
          adminClient
            .listTopics()
            .map(_.keys.toList)
            .flatMap(adminClient.describeTopics(_))
            .map(
              _.map { case (name, description) =>
                TopicDetails(
                  name,
                  description.partitions.size,
                  description.partitions.headOption.map(_.replicas.size).getOrElse(0)
                )
              }.toList
            )
      }
    }

  lazy val liveLayer: TaskLayer[HasTopicExplorer] =
    Config.liveLayer >>>
      (Config.accessConfig.toManaged_ >>=
        (c => AdminClient.make(AdminClientSettings(c.kafkaHosts)))).toLayer >>>
      topicExplorerLayer

}
