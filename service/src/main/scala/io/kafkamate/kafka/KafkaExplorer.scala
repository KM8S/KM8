package io.kafkamate
package kafka

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.admin._
import zio.macros.accessible
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.clients.admin.AdminClientConfig

import config._, ClustersConfig._
import topics._
import brokers.BrokerDetails

@accessible object KafkaExplorer {

  type HasKafkaExplorer = Has[Service]
  type HasAdminClient = Has[AdminClient]

  trait Service {
    def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]]
    def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]]
    def addTopic(req: AddTopicRequest): RIO[Blocking with Clock, TopicDetails]
    def deleteTopic(req: DeleteTopicRequest): RIO[Blocking with Clock, DeleteTopicResponse]
  }

  lazy val kafkaExplorerLayer: URLayer[ClustersConfigService, HasKafkaExplorer] =
    ZLayer.fromService { clustersConfigService =>
      new Service {
        private def adminClientLayer(clusterId: String): TaskLayer[HasAdminClient] =
          ZLayer.fromManaged {
            for {
              cs <- clustersConfigService.getCluster(clusterId).toManaged_
              client <- AdminClient.make(AdminClientSettings(cs.hosts, 10.seconds, Map.empty))
            } yield client
          }

        private implicit class AdminClientProvider[A](eff: RIO[HasAdminClient with Blocking, A]) {
          def withAdminClient(clusterId: String): RIO[Blocking with Clock, A] = eff
            .timeoutFail(new Exception("Timed out"))(5.seconds)
            .provideSomeLayer[Blocking with Clock](adminClientLayer(clusterId))
        }

        def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              val ac = env.get[AdminClient]
              for {
                (nodes, controllerId) <- ac.describeClusterNodes() <&> ac.describeClusterController().map(_.id())
                brokers = nodes.map { n =>
                  val nodeId = n.id()
                  if (controllerId != nodeId) BrokerDetails(nodeId)
                  else BrokerDetails(nodeId, isController = true)
                }
                //resources = nodes.map(n => new ConfigResource(ConfigResource.Type.BROKER, n.idString()))
                //_ <- ac.describeConfigs(resources)
              } yield brokers
            }
            .withAdminClient(clusterId)

        def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              val ac = env.get[AdminClient]
              ac.listTopics()
                .map(_.keys.toList)
                .flatMap(ls => ZIO.filterNotPar(ls)(t => UIO(t.startsWith("__"))))
                .flatMap(ac.describeTopics(_))
                .map(
                  _.map { case (name, description) =>
                    TopicDetails(
                      name,
                      description.partitions.size,
                      description.partitions.headOption.map(_.replicas.size).getOrElse(0),
                      "delete" //todo fix this
                    )
                  }.toList
                )
            }
            .withAdminClient(clusterId)

        def addTopic(req: AddTopicRequest): RIO[Blocking with Clock, TopicDetails] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              env
                .get[AdminClient]
                .createTopic(AdminClient.NewTopic(req.name, req.partitions, req.replication.toShort))
                .as(TopicDetails(req.name, req.partitions, req.replication, req.cleanupPolicy))
            }
            .withAdminClient(req.clusterId)

        def deleteTopic(req: DeleteTopicRequest): RIO[Blocking with Clock, DeleteTopicResponse] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              env
                .get[AdminClient]
                .deleteTopic(req.topicName)
                .as(DeleteTopicResponse(req.topicName))
            }
            .withAdminClient(req.clusterId)

      }
    }

  lazy val liveLayer: ULayer[HasKafkaExplorer] =
    ClustersConfig.liveLayer >>> kafkaExplorerLayer

}
