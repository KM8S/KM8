package io.km8.core
package config

import zio.*
import zio.json.*
import zio.logging.*

case class ProtoSerdeSettings(schemaRegistryUrl: String, private val _configs: Map[String, AnyRef] = Map.empty) {

  val configs: Map[String, AnyRef] =
    _configs ++ Map("schema.registry.url" -> schemaRegistryUrl)
}

case class ClusterSettings(
  id: String,
  name: String,
  kafkaHosts: List[String],
  schemaRegistryUrl: Option[String]) {
  val kafkaHosts_ : String = kafkaHosts.mkString(",")
  val protoSerdeSettings: Option[ProtoSerdeSettings] = schemaRegistryUrl.map(ProtoSerdeSettings(_))
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

trait ClusterConfig {
  def readClusters: Task[ClusterProperties]
  def writeClusters(cluster: ClusterSettings): Task[Unit]
  def deleteCluster(clusterId: String): Task[ClusterProperties]

  def getCluster(clusterId: String): Task[ClusterSettings] =
    for {
      all <- readClusters
      cs <- ZIO
              .fromOption(all.clusters.find(_.id == clusterId))
              .orElseFail(new Exception(s"Cluster ($clusterId) doesn't exist!"))
    } yield cs
}

object ClustersConfig {

  def readClusters = ZIO.serviceWithZIO[ClusterConfig](_.readClusters)
  def writeClusters(cluster: ClusterSettings) = ZIO.serviceWithZIO[ClusterConfig](_.writeClusters(cluster))
  def deleteCluster(clusterId: String) = ZIO.serviceWithZIO[ClusterConfig](_.deleteCluster(clusterId))

  lazy val liveLayer: URLayer[ConfigPath, ClusterConfig] =
    ZLayer(ZIO.service[ConfigPath].map(ClusterConfigLive.apply))

  case class ClusterConfigLive(configPath: ConfigPath) extends ClusterConfig {
    private val configFilepath = configPath.path
    private def emptyProperties = ClusterProperties(List.empty)
    private def emptyPropertiesJson = emptyProperties.toJsonPretty

    private def writeJson(json: => String) =
      ZIO.attempt(os.write.over(configFilepath, json, createFolders = true))

    def readClusters: Task[ClusterProperties] =
      for {
        b <- ZIO.attempt(os.exists(configFilepath))
        _ <- writeJson(emptyPropertiesJson).unless(b)
        s <- ZIO.attempt(os.read(configFilepath))
        r <- ZIO
               .fromEither(s.fromJson[ClusterProperties])
               .catchAll { err =>
                 ZIO.logWarning(s"Parsing error: $err") *>
                   writeJson(emptyPropertiesJson).as(emptyProperties)
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
        ls <- ZIO.filterNotPar(c.clusters)(s => ZIO.attempt(s.id == clusterId))
        json = ClusterProperties(ls).toJsonPretty
        _ <- writeJson(json)
        r <- readClusters
      } yield r
  }
}
