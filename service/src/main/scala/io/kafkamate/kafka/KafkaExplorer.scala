package io.kafkamate
package kafka

import zio._
import zio.blocking._
import zio.clock.Clock
import zio.duration._
import zio.kafka.admin._
import zio.kafka.admin.AdminClient.{ConfigResource, ConfigResourceType}
import org.apache.kafka.clients.admin.{OffsetSpec, AdminClient => JAdminClient}
import org.apache.kafka.common.TopicPartition
import zio.macros.accessible
import config._
import ClustersConfig._
import topics._
import brokers.BrokerDetails

import scala.jdk.CollectionConverters._
import scala.concurrent.TimeoutException

@accessible object KafkaExplorer {

  type HasKafkaExplorer = Has[Service]
  type HasAdminClient   = Has[AdminClient] with Has[JAdminClient]

  private val CleanupPolicyKey = "cleanup.policy"
  private val RetentionMsKey   = "retention.ms"

  trait Service {
    def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]]
    def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]]
    def getLatestOffset(
      clusterId: String,
      tps: Set[TopicPartition]
    ): RIO[Blocking with Clock, Map[TopicPartition, Long]]
    def addTopic(req: AddTopicRequest): RIO[Blocking with Clock, TopicDetails]
    def deleteTopic(req: DeleteTopicRequest): RIO[Blocking with Clock, DeleteTopicResponse]
  }

  lazy val liveLayer: URLayer[ClustersConfigService, HasKafkaExplorer] =
    ZLayer.fromService { clustersConfigService =>
      new Service {
        private def adminClientLayer(clusterId: String): RLayer[Blocking, HasAdminClient] = {
          def services = for {
            cs     <- clustersConfigService.getCluster(clusterId).toManaged_
            s       = AdminClientSettings(cs.kafkaHosts, 2.seconds, Map.empty)
            managed = Task(JAdminClient.create(s.driverSettings.asJava)).toManaged(ja => UIO(ja.close(s.closeTimeout)))
            client <- AdminClient.fromManagedJavaClient(managed)
            jAdmin <- managed
          } yield Has.allOf(client, jAdmin)

          ZLayer.fromManagedMany(services)
        }

        private implicit class AdminClientProvider[A](eff: RIO[HasAdminClient with Blocking, A]) {
          def withAdminClient(clusterId: String): RIO[Blocking with Clock, A] = eff
            .timeoutFail(new TimeoutException("Timed out after 15 second!"))(15.seconds)
            .provideSomeLayer[Blocking with Clock](adminClientLayer(clusterId))
        }

        def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]] =
          ZIO
            .accessM[HasAdminClient with Blocking] { env =>
              val ac = env.get[AdminClient]
              for {
                (nodes, controllerId) <- ac.describeClusterNodes() <&> ac.describeClusterController().map(_.map(_.id))
                brokers = nodes.map { n =>
                            val nodeId = n.id
                            if (!controllerId.contains(nodeId)) BrokerDetails(nodeId)
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
                  ZIO.tupledPar(
                    ac.describeTopics(ls).flatMap(r => getTopicsSize(r).map((r, _))),
                    ac.describeConfigs(ls.map(ConfigResource(ConfigResourceType.Topic, _)))
                  )
                )
                .map { case ((nameDescriptionMap, sizes), topicConfigMap) =>
                  val configs = topicConfigMap.map { case (res, conf) => (res.name, conf) }
                  nameDescriptionMap.map { case (topicName, description) =>
                    val conf                   = configs.get(topicName).map(_.entries)
                    def getConfig(key: String) = conf.flatMap(_.get(key).map(_.value())).getOrElse("unknown")
                    TopicDetails(
                      name = topicName,
                      partitions = description.partitions.size,
                      replication = description.partitions.headOption.map(_.replicas.size).getOrElse(0),
                      cleanupPolicy = getConfig(CleanupPolicyKey),
                      retentionMs = getConfig(RetentionMsKey),
                      size = sizes.getOrElse(topicName, 0L)
                    )
                  }.toList.sortBy(_.name)
                }
            }
            .withAdminClient(clusterId)

        private def getTopicsSize(
          topicDescription: Map[String, AdminClient.TopicDescription]
        ): RIO[Blocking with HasAdminClient, Map[String, Long]] = {
          val brokerIds = topicDescription.values.flatMap(_.partitions).flatMap(_.leader.map(_.id)).toSet
          aggregateTopicSizes(brokerIds)
        }

        private def aggregateTopicSizes(brokerIds: Set[Int]): RIO[HasAdminClient with Blocking, Map[String, Long]] =
          for {
            jAdmin       <- ZIO.service[JAdminClient]
            ids           = brokerIds.map(Integer.valueOf).asJavaCollection
            asJavaFuture <- effectBlocking(jAdmin.describeLogDirs(ids).descriptions())
            logDirInfo <- ZIO.foreachPar(asJavaFuture.asScala.toMap) { case (brokerId, kafkaFuture) =>
                            AdminClient.fromKafkaFuture(effectBlocking(kafkaFuture)).map(brokerId -> _.asScala)
                          }
          } yield {
            logDirInfo.values
              .flatMap(_.values)
              .flatMap(_.replicaInfos.asScala)
              .groupMapReduce({ case (tp, _) => tp.topic })({ case (_, info) => info.size })(_ + _)
          }

        override def getLatestOffset(
          clusterId: String,
          tps: Set[TopicPartition]
        ): RIO[Blocking with Clock, Map[TopicPartition, Long]] = {
          val topicPartitionOffsets = tps.map(tp => (tp, OffsetSpec.latest())).toMap.asJava
          for {
            result <- ZIO.service[JAdminClient].mapEffect(_.listOffsets(topicPartitionOffsets))
            map    <- AdminClient.fromKafkaFuture(UIO(result.all())).map(_.asScala.view.mapValues(_.offset()).toMap)
          } yield map
        }.withAdminClient(clusterId)

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
