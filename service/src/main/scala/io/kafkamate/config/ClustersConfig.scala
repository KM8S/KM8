package io.kafkamate
package config

import zio._
import zio.json._
import zio.logging._
import zio.macros.accessible
import zio.system.System

@accessible object ClustersConfig {
  import ConfigPathService._

  type ClustersConfigService = Has[Service]

  case class ClusterSettings(id: String, name: String, hosts: List[String]) {
    val hostsString: String = hosts.mkString(",")
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
    def writeClusters(cluster: ClusterSettings): Task[Unit]
    def deleteCluster(clusterId: String): Task[ClusterProperties]

    def getCluster(clusterId: String): Task[ClusterSettings] =
      for {
        all <- readClusters
        cs <- ZIO
          .fromOption(all.clusters.find(_.id == clusterId))
          .orElseFail(new Exception(s"Cluster ($clusterId) doesn't exist..!"))
      } yield cs
  }

  lazy val liveLayer: URLayer[Logging, ClustersConfigService] =
    (System.live >>> ConfigPathService.liveLayer) ++ ZLayer.requires[Logging] >>> configLayer

  lazy val configLayer: URLayer[HasConfigPath with Logging, ClustersConfigService] =
    ZLayer.fromServices[ConfigPath, Logger[String], Service] { case (configPath, log) =>
      new Service {
        private val configFilepath = configPath.path
        private val emptyProperties = ClusterProperties(List.empty)

        case class ParseException(message: String) extends Exception(message)

        private def writeJson(json: String) =
          Task(os.write.over(configFilepath, json, createFolders = true))

        def readClusters: Task[ClusterProperties] =
          for {
            b <- Task(os.exists(configFilepath))
            _ <- writeJson(emptyProperties.toJsonPretty).unless(b)
            s <- Task(os.read(configFilepath))
            r <- ZIO
              .fromEither(s.fromJson[ClusterProperties])
              .mapError(ParseException)
              .catchSome {
                case e: ParseException =>
                  log.warn(s"Parsing error: ${e.getMessage}") *>
                    writeJson(emptyProperties.toJsonPretty).as(emptyProperties)
              }
          } yield r

        def writeClusters(cluster: ClusterSettings): Task[Unit] =
          for {
            c <- readClusters
            json = c.copy(clusters = c.clusters :+ cluster).toJsonPretty
            _ <- writeJson(json)
          } yield ()

        def deleteCluster(clusterId: String): Task[ClusterProperties] =
          for {
            c <- readClusters
            ls <- ZIO.filterNotPar(c.clusters)(s => Task(s.id == clusterId))
            json = ClusterProperties(ls).toJsonPretty
            _ <- writeJson(json)
            r <- readClusters
          } yield r
      }
  }

}
