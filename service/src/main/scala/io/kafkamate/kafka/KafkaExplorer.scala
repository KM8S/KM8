package io.kafkamate
package kafka

import zio._
import zio.clock.Clock
import zio.duration.Duration
import zio.blocking.Blocking
import zio.kafka.admin._
import zio.macros.accessible
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.AdminClientConfig

import scala.concurrent.duration._

import config._, ClustersConfig._
import topics.TopicDetails
import brokers.BrokerDetails

@accessible object KafkaExplorer {

  type HasKafkaExplorer = Has[Service]
  type HasAdminClient = Has[AdminClient]

  trait Service {
    def listBrokers: RIO[Blocking, List[BrokerDetails]]
    def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]]
  }

  lazy val kafkaExplorerLayer: URLayer[ClustersConfigService, HasKafkaExplorer] =
    ZLayer.fromService { clustersConfigService =>
      new Service {
        def adminClientLayer(clusterId: String): TaskLayer[HasAdminClient] =
          ZLayer.fromManaged {
            for {
              cs <- clustersConfigService.getCluster(clusterId).toManaged_
              client <- AdminClient.make(AdminClientSettings(cs.hosts))
            } yield client
          }

        def listBrokers: RIO[Blocking, List[BrokerDetails]] =
          /*adminClient
            .describeConfigs(List(new ConfigResource(ConfigResource.Type.BROKER, AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG)))*/
          Task(List(BrokerDetails(1, "localhost:9092")))

        def internalListTopics: RIO[HasAdminClient with Blocking, List[TopicDetails]] =
          ZIO.accessM { adminClientService =>
            val ac = adminClientService.get
            ac.listTopics()
              .map(_.keys.toList)
              .flatMap(ac.describeTopics(_))
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

        def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]] =
          internalListTopics
            .timeout(Duration.fromScala(5.seconds))
            .flatMap(op => ZIO.fromOption(op))
            .orElseFail(new Exception("Timed out"))
            .provideSomeLayer[Blocking with Clock](adminClientLayer(clusterId))
      }
    }

  lazy val liveLayer: ULayer[HasKafkaExplorer] =
    ClustersConfig.liveLayer >>> kafkaExplorerLayer

}
