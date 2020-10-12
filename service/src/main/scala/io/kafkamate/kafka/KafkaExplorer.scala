package io.kafkamate
package kafka

import zio._
import zio.blocking.Blocking
import zio.kafka.admin._
import zio.macros.accessible
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.AdminClientConfig

import config._
import topics.TopicDetails
import brokers.BrokerDetails

@accessible object KafkaExplorer {

  type HasKafkaExplorer = Has[Service]
  type HasAdminClient = Has[AdminClient]

  trait Service {
    def listBrokers: RIO[Blocking, List[BrokerDetails]]
    def listTopics: RIO[Blocking, List[TopicDetails]]
  }

  lazy val kafkaExplorerLayer: URLayer[HasAdminClient, HasKafkaExplorer] =
    ZLayer.fromService { adminClient =>
      new Service {
        def listBrokers: RIO[Blocking, List[BrokerDetails]] =
          /*adminClient
            .describeConfigs(List(new ConfigResource(ConfigResource.Type.BROKER, AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG)))*/
          Task(List(BrokerDetails(1, "localhost:9092")))

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

  lazy val liveLayer: TaskLayer[HasKafkaExplorer] =
    Config.liveLayer >>>
      (Config.accessConfig.toManaged_ >>=
        (c => AdminClient.make(AdminClientSettings(c.kafkaHosts)))).toLayer >>>
      kafkaExplorerLayer

}
