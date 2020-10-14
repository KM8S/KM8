package io.kafkamate
package config

import zio._
import zio.json._
import zio.macros.accessible

@accessible object ClustersConfig {

  type ClustersConfigService = Has[Service]

  case class ClusterSettings(id: String, name: String, kafkaHosts: List[String]) {
    val kafkaHostsAsString: String = kafkaHosts.mkString(",")
  }
  object ClusterSettings {
    implicit val decoder: JsonDecoder[ClusterSettings] = DeriveJsonDecoder.gen[ClusterSettings]
    implicit val encoder: JsonEncoder[ClusterSettings] = DeriveJsonEncoder.gen[ClusterSettings]
  }

  case class ClusterProperties(clusters: List[ClusterSettings])
  object ClusterProperties {
    implicit val decoder: JsonDecoder[ClusterProperties] = DeriveJsonDecoder.gen[ClusterProperties]
    implicit val encoder: JsonEncoder[ClusterProperties] = DeriveJsonEncoder.gen[ClusterProperties]
  }

  trait Service {
    def readClusters: Task[ClusterProperties]
    def writeClusters(props: ClusterProperties): Task[Unit]

    def getCluster(clusterId: String): Task[ClusterSettings] =
      for {
        all <- readClusters
        cs <- ZIO
          .fromOption(all.clusters.find(_.id == clusterId))
          .orElseFail(new Throwable(s"Cluster ($clusterId) doesn't exist!"))
      } yield cs
  }

  lazy val liveLayer: ULayer[ClustersConfigService] = ZLayer.succeed {
    new Service {
      private val configFilepath = os.pwd / "clusters.json"

      def readClusters: Task[ClusterProperties] =
        for {
          b <- Task(os.exists(configFilepath))
          _ <- ZIO.fail(new Throwable(s"$configFilepath does not exist!")).unless(b)
          s <- Task(os.read(configFilepath))
          r <- ZIO.fromEither(s.fromJson[ClusterProperties]).mapError(new Throwable(_))
        } yield r

      def writeClusters(props: ClusterProperties): Task[Unit] =
        for {
          c <- readClusters
          json = c.copy(clusters = c.clusters ++ props.clusters).toJsonPretty
          _ <- Task(os.write.over(configFilepath, json, createFolders = true))
        } yield ()
    }
  }

}
