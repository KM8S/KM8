package io.km8.kafka

import scala.concurrent.TimeoutException

import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration._
import zio.kafka.admin._
import zio.kafka.admin.AdminClient.{ConfigResource, ConfigResourceType}

import io.km8.store.Store
import io.km8.models._

trait KafkaExplorer:
  def listBrokers(clusterId: String): Task[List[BrokerDetails]]
  def listTopics(clusterId: String): Task[List[TopicDetails]]
  def addTopic(req: AddTopic): Task[TopicDetails]
  def deleteTopic(req: DeleteTopic): Task[Unit]

case class KafkaExplorerLive(store: Store, blocking: Blocking.Service, clock: Clock.Service):
  private val deps = Has.allOf(blocking, clock)

  private val CleanupPolicyKey = "cleanup.policy"
  private val RetentionMsKey   = "retention.ms"

  private def adminLayer(clusterId: String): RLayer[Blocking, Has[AdminClient]] =
    ZLayer.fromManaged {
      for {
        cs <- store.getCluster(clusterId).toManaged_
        client <- AdminClient
          .make(AdminClientSettings(cs.kafkaHosts, 2.seconds, Map.empty))
      } yield client
    }

  private implicit class AdminClientProvider[A](eff: RIO[Has[AdminClient] with Blocking, A]):
    def withAdminClient(clusterId: String): RIO[Blocking with Clock, A] = eff
      .timeoutFail(new TimeoutException("Timed out"))(6.seconds)
      .provideSomeLayer[Blocking with Clock](adminLayer(clusterId))

  def listBrokers(clusterId: String): RIO[Blocking with Clock, List[BrokerDetails]] =
    ZIO
      .serviceWith[AdminClient] { ac =>
        for {
          r <- ac.describeClusterNodes() <&> ac.describeClusterController().map(_.id)
          (nodes, controllerId) = r
          brokers = nodes.map { n =>
            val nodeId = n.id
            if (controllerId != nodeId) BrokerDetails(nodeId, isController = false)
            else BrokerDetails(nodeId, isController = true)
          }
          //resources = nodes.map(n => new ConfigResource(ConfigResource.Type.BROKER, n.idString()))
          //_ <- ac.describeConfigs(resources)
        } yield brokers
      }
      .withAdminClient(clusterId)

  def listTopics(clusterId: String): RIO[Blocking with Clock, List[TopicDetails]] =
    ZIO
      .accessM[Has[AdminClient] with Blocking] { env =>
        val ac = env.get[AdminClient]
        ac.listTopics()
          .map(_.keys.toList)
          .flatMap(ls => ZIO.filterNotPar(ls)(t => UIO(t.startsWith("_"))))
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
                retentionMs = getConfig(RetentionMsKey),
                size = 0
              )
            }.toList.sortBy(_.name)
          }
      }
      .withAdminClient(clusterId)

  def addTopic(req: AddTopic): RIO[Blocking with Clock, TopicDetails] =
    ZIO
      .accessM[Has[AdminClient] with Blocking] { env =>
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
          .as(TopicDetails(req.name, req.partitions, req.replication, req.cleanupPolicy, req.retentionMs, 0))
      }
      .withAdminClient(req.clusterId)

  def deleteTopic(req: DeleteTopic): RIO[Blocking with Clock, Unit] =
    ZIO
      .accessM[Has[AdminClient] with Blocking] { env =>
        env
          .get[AdminClient]
          .deleteTopic(req.topicName)
      }
      .withAdminClient(req.clusterId)
