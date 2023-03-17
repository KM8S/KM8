package io.kafkamate
package kafka

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.admin._
import zio.kafka.admin.AdminClient.{ConfigResource, ConfigResourceType, TopicPartition}
import zio.macros.accessible
import config._
import ClustersConfig._
import topics._
import brokers.BrokerDetails
import org.apache.kafka.clients.admin.{DescribeLogDirsOptions, ReplicaInfo}

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
        private def adminClientLayer(clusterId: String): RLayer[Blocking, HasAdminClient] =
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
                (nodes, controllerId) <- ac.describeClusterNodes() <&> ac.describeClusterController().map(_.id)
                brokers = nodes.map { n =>
                            val nodeId = n.id
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
                .map(_.keys.toList.filterNot(_.startsWith("_")))
                .flatMap(ls =>
                  ac.describeTopics(ls) <&> ac.describeConfigs(ls.map(ConfigResource(ConfigResourceType.Topic, _)))
                )
                .map { case (nameDescriptionMap, topicConfigMap) =>
                  val configs = topicConfigMap.map { case (res, conf) => (res.name, conf) }
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

//        def getTopicsSize(topicDescription: Map[String, AdminClient.TopicDescription]): RIO[Blocking, Map[TopicPartition, Long]] = {
//          for {
//            // Get the partition info
////            topicPartitions = topicDescription.partitions.map { partitionInfo =>
////              TopicPartition(topic, partitionInfo.partition)
////            }
//            _ <- ZIO.unit
//            // Query log directory information from each broker
//            brokerIds = topicDescription.values.flatMap(_.partitions).map(_.leader.id).toSet
//            logDirInfos <- ZIO.foreachPar(brokerIds)(describeLogDirs)
//          } yield {
//            logDirInfos.flatMap { logDirInfo =>
//              logDirInfo.flatMap { case (tp, replicaInfo) =>
//                if (tp.topic == topic) {
//                  Some(tp -> replicaInfo.size)
//                } else {
//                  None
//                }
//              }
//            }.toMap
//          }
//        }
//
//        def describeLogDirs(brokerId: Int): Task[Map[String, ReplicaInfo]] = {
//          val asJavaFuture = AdminClient.describeLogDirs(List(brokerId)).all()
//          AdminClient.fromKafkaFuture(asJavaFuture).map { logDirInfo =>
//            logDirInfo.get(brokerId).asScala.flatMap {
//              case (_, replicaInfos) => replicaInfos.asScala
//            }.toMap
//          }
//        }

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
                    Map(
                      CleanupPolicyKey -> req.cleanupPolicy,
                      RetentionMsKey   -> req.retentionMs
                    )
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
