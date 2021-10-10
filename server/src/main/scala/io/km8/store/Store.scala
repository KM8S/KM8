package io.km8.store

import zio._
import zio.json._
import zio.logging._

case class ProtoSerdeConfig(schemaRegistryUrl: String, private val _configs: Map[String, AnyRef] = Map.empty) {
  val configs: Map[String, AnyRef] =
    _configs ++ Map("schema.registry.url" -> schemaRegistryUrl)
}

case class ClusterSettings(id: String, name: String, kafkaHosts: List[String], schemaRegistryUrl: Option[String]) {
  val kafkaHosts_ : String                           = kafkaHosts.mkString(",")
  val protoSerdeSettings: Option[ProtoSerdeConfig] = schemaRegistryUrl.map(ProtoSerdeConfig(_))
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

trait Store:
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

case class StoreLive(configPath: StorePath, log: Logger[String]) extends Store:
  private val configFilepath      = configPath.path
  private def emptyProperties     = ClusterProperties(List.empty)
  private def emptyPropertiesJson = emptyProperties.toJsonPretty

  private def writeJson(json: => String) =
    Task(os.write.over(configFilepath, json, createFolders = true))

  def readClusters: Task[ClusterProperties] =
    for {
      b <- Task(os.exists(configFilepath))
      _ <- writeJson(emptyPropertiesJson).unless(b)
      s <- Task(os.read(configFilepath))
      r <- ZIO
             .fromEither(s.fromJson[ClusterProperties])
             .catchAll { err =>
               log.warn(s"Parsing error: $err") *>
                 writeJson(emptyPropertiesJson).as(emptyProperties)
             }
    } yield r

  def writeClusters(cluster: ClusterSettings): Task[Unit] =
    for {
      c   <- readClusters
      json = c.copy(clusters = c.clusters :+ cluster).toJsonPretty
      _   <- writeJson(json)
    } yield ()

  def deleteCluster(clusterId: String): Task[ClusterProperties] =
    for {
      c   <- readClusters
      ls  <- ZIO.filterNotPar(c.clusters)(s => Task(s.id == clusterId))
      json = ClusterProperties(ls).toJsonPretty
      _   <- writeJson(json)
      r   <- readClusters
    } yield r

object StoreLive:
  lazy val layer: URLayer[Has[StorePath] & Logging, Has[Store]] =
    (StoreLive(_, _)).toLayer