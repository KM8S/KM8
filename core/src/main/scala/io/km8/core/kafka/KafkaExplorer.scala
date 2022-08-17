package io.km8.core
package kafka

import zio.*

import zio.kafka.admin.*
import zio.kafka.admin.AdminClient.*

import config.*, ClustersConfig.*
import io.km8.common.*

trait KafkaExplorer:
  def listBrokers(clusterId: String): Task[List[BrokerDetails]]
  def listTopics(clusterId: String): Task[List[TopicDetails]]
  def addTopic(req: AddTopicRequest): Task[TopicDetails]
  def deleteTopic(req: DeleteTopicRequest): Task[DeleteTopicResponse]
  def listConsumerGroups(clusterId: String): Task[ConsumerGroupsResponse]
  def listConsumerOffsets(clusterId: String, groupId: String): Task[ConsumerGroupOffsetsResponse]

object KafkaExplorer:

  val CleanupPolicyKey = "cleanup.policy"
  val RetentionMsKey = "retention.ms"

  def listConsumerGroups(clusterId: String): ZIO[KafkaExplorer, Throwable, ConsumerGroupsResponse] =
    ZIO.serviceWithZIO(_.listConsumerGroups(clusterId))

  def listConsumerOffsets(
    clusterId: String,
    groupId: String
  ): ZIO[KafkaExplorer, Throwable, ConsumerGroupOffsetsResponse] =
    ZIO.serviceWithZIO(_.listConsumerOffsets(clusterId, groupId))

  lazy val liveLayer: ZLayer[ClusterConfig, Nothing, KafkaExplorer] =
    ZLayer {
      for {
        cc <- ZIO.service[ClusterConfig]
      } yield KafkaExplorerLive(cc)
    }

case class KafkaExplorerLive(clustersConfigService: ClusterConfig) extends KafkaExplorer {

  private def adminClientLayer(clusterId: String) =
    ZLayer.fromZIO {
      for {
        cs <- clustersConfigService.getCluster(clusterId)
        client <- AdminClient.make(AdminClientSettings(cs.kafkaHosts, 2.seconds, Map.empty))
      } yield client
    }

  def withAdminClient[A](clusterId: String)(eff: AdminClient => RIO[Any, A]): ZIO[Any, Throwable, A] =
    ZIO.scoped {
      ZIO
        .service[AdminClient]
        .flatMap(eff(_).timeoutFail(new Exception("Timed out"))(6.seconds))
        .provideLayer(adminClientLayer(clusterId))
    }

  override def listBrokers(clusterId: String) =
    withAdminClient(clusterId) { ac =>
      for {
        nodes <- ac.describeClusterNodes()
        controllerId <- ac.describeClusterController().map(_.map(_.id))
        brokers = nodes.map { n =>
                    val nodeId = n.id
                    if (controllerId.contains(nodeId)) BrokerDetails(nodeId, isController = true)
                    else BrokerDetails(id = nodeId, isController = false)
                  }
        // resources = nodes.map(n => new ConfigResource(ConfigResource.Type.BROKER, n.idString()))
        // _ <- ac.describeConfigs(resources)
      } yield brokers
    }

  override def listTopics(clusterId: String) =
    withAdminClient(clusterId) { ac =>
      ac.listTopics()
        .map(_.keys.toList)
        .flatMap(ls => ZIO.filterNotPar(ls)(t => ZIO.succeed(t.startsWith("_"))))
        .flatMap(ls =>
          ac.describeTopics(ls) <&> ac.describeConfigs(ls.map(ConfigResource(ConfigResourceType.Topic, _)))
        )
        .map { case (nameDescriptionMap, topicConfigMap) =>
          val configs = topicConfigMap.map { case (res, conf) => (res.name, conf) }
          nameDescriptionMap.map { case (name, description) =>
            val conf = configs.get(name).map(_.entries)
            def getConfig(key: String) = conf.flatMap(_.get(key).map(_.value())).getOrElse("unknown")
            TopicDetails(
              name = name,
              partitions = description.partitions.size,
              replication = description.partitions.headOption.map(_.replicas.size).getOrElse(0),
              cleanupPolicy = getConfig(KafkaExplorer.CleanupPolicyKey),
              retentionMs = getConfig(KafkaExplorer.RetentionMsKey),
              size = 0
            )
          }.toList.sortBy(_.name)
        }
    }

  override def addTopic(req: AddTopicRequest) =
    withAdminClient(req.clusterId) { ac =>
      ac
        .createTopic(
          AdminClient.NewTopic(
            req.name,
            req.partitions,
            req.replication.toShort,
            Map(
              KafkaExplorer.CleanupPolicyKey -> req.cleanupPolicy,
              KafkaExplorer.RetentionMsKey -> req.retentionMs
            )
          )
        )
        .as(
          TopicDetails(
            name = req.name,
            partitions = req.partitions,
            replication = req.replication,
            cleanupPolicy = req.cleanupPolicy,
            retentionMs = req.retentionMs,
            size = 0
          )
        )
    }

  override def deleteTopic(req: DeleteTopicRequest) =
    withAdminClient(req.clusterId) {
      _.deleteTopic(req.topicName)
        .as(DeleteTopicResponse(req.topicName))
    }

  override def listConsumerGroups(clusterId: String): Task[ConsumerGroupsResponse] =
    def mapConsumerGroup(state: Option[ConsumerGroupState]): ConsumerGroupInternalState = state match {
      case None | Some(ConsumerGroupState.Unknown)      => ConsumerGroupInternalState.Unknown
      case Some(ConsumerGroupState.PreparingRebalance)  => ConsumerGroupInternalState.PreparingRebalance
      case Some(ConsumerGroupState.CompletingRebalance) => ConsumerGroupInternalState.CompletingRebalance
      case Some(ConsumerGroupState.Stable)              => ConsumerGroupInternalState.Stable
      case Some(ConsumerGroupState.Dead)                => ConsumerGroupInternalState.Dead
      case Some(ConsumerGroupState.Empty)               => ConsumerGroupInternalState.Empty
    }

    withAdminClient(clusterId) {
      _.listConsumerGroups()
        .map(lst => ConsumerGroupsResponse(lst.map(r => ConsumerGroupInternal(r.groupId, mapConsumerGroup(r.state)))))
    }

  end listConsumerGroups

  override def listConsumerOffsets(
    clusterId: String,
    groupId: String
  ): ZIO[Any, Throwable, ConsumerGroupOffsetsResponse] =
    withAdminClient(clusterId) {
      _.listConsumerGroupOffsets(groupId).map(res =>
        ConsumerGroupOffsetsResponse(res.map { case (tp, offMeta) =>
          TopicPartitionInternal(tp.name, tp.partition) -> offMeta.offset
        })
      )
    }

}
