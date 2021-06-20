package io.kafkamate
package kafka

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.admin._
import zio.macros.accessible
import org.apache.kafka.common.config.ConfigResource

import config._, ClustersConfig._
import topics._
import brokers.BrokerDetails

@accessible object KafkaExplorer {

  type HasKafkaExplorer = Has[Service]
  type HasAdminClient   = Has[AdminClient]

  val CleanupPolicyKey = "cleanup.policy"
  val RetentionMsKey   = "retention.ms"

  trait Service {
    def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]]
    def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]]
    def addTopic(req: AddTopicRequest): RIO[Blocking with Clock, TopicDetails]
    def deleteTopic(req: DeleteTopicRequest): RIO[Blocking with Clock, DeleteTopicResponse]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, HasKafkaExplorer] =
    ZLayer.fromService { clustersConfigService =>
      new Service {
        private def adminClientLayer(clusterId: String): TaskLayer[HasAdminClient] =
          ZLayer.fromManaged {
            for {
              cs     <- clustersConfigService.getCluster(clusterId).toManaged_
              client <- AdminClient.make(AdminClientSettings(cs.kafkaHosts, 2.seconds, Map.empty))
            } yield client
          }

        private implicit class AdminClientProvider[A](eff: RIO[HasAdminClient with Blocking, A]) {
          def withAdminClient(clusterId: String): RIO[Blocking with Clock, A] = eff
            .timeoutFail(new Exception("Timed out"))(6.seconds)
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
                .flatMap(ls => ZIO.filterNotPar(ls)(t => UIO(t.startsWith("_"))))
                .flatMap(ls =>
                  ac.describeTopics(ls) <&> ac.describeConfigs(ls.map(new ConfigResource(ConfigResource.Type.TOPIC, _)))
                )
                .map { case (nameDescriptionMap, topicConfigMap) =>
                  val configs = topicConfigMap.map { case (res, conf) => (res.name(), conf) }
                  nameDescriptionMap.map { case (name, description) =>
                    val conf                   = configs.get(name).map(_.entries)
                    def getConfig(key: String) = conf.flatMap(_.get(key).map(_.value())).getOrElse("unknown")
                    TopicDetails(
                      name = name,
                      partitions = description.partitions.size,
                      replication = description.partitions.headOption.map(_.replicas.size).getOrElse(0),
                      cleanupPolicy = getConfig(CleanupPolicyKey),
                      retentionMs = getConfig(RetentionMsKey)
                    )
                  }.toList.sortBy(_.name)
                }
            }
            .withAdminClient(clusterId)

        def addTopic(req: AddTopicRequest): RIO[Blocking with Clock, TopicDetails] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              env
                .get[AdminClient]
                .createTopic(
                  AdminClient.NewTopic(
                    req.name,
                    req.partitions,
                    req.replication.toShort,
                    Map(CleanupPolicyKey -> req.cleanupPolicy)
                  )
                )
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

}
