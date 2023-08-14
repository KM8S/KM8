package io.kafkamate
package grpc

import scala.util.Random

import io.grpc.Status
import zio.{UIO, ZEnv, ZIO}
import zio.logging._

import config._, ClustersConfig._
import clusters._
import utils._

object ClustersService {
  type Env = ZEnv with ClustersConfigService with Logging

  object GrpcService extends ZioClusters.RClustersService[Env] {
    def genRandStr(length: Int): UIO[String] =
      UIO(Random.alphanumeric.take(length).mkString)

    def addCluster(request: ClusterDetails): ZIO[Env, Status, ClusterDetails] =
      for {
        clusterId <- genRandStr(6)
                       .map(str => s"${request.name.trim.replaceAll(" ", "-")}-$str")
        hosts          = request.kafkaHosts.split(",").toList
        schemaRegistry = Option.unless(request.schemaRegistryUrl.isEmpty)(request.schemaRegistryUrl)
        c <- ClustersConfig
               .writeClusters(ClusterSettings(clusterId, request.name, hosts, schemaRegistry))
               .tapError(e => log.throwable(s"Add cluster error: ${e.getMessage}", e))
               .mapBoth(GRPCStatus.fromThrowable, _ => request)
      } yield c

    private def toClusterResponse(r: ClusterProperties) =
      ClusterResponse(
        r.clusters.map(c => ClusterDetails(c.id, c.name, c.kafkaHosts_, c.schemaRegistryUrl.getOrElse("<empty>")))
      )

    def getClusters(request: ClusterRequest): ZIO[Env, Status, ClusterResponse] =
      log.debug("Received getClusters request") *>
        ClustersConfig.readClusters
          .tapError(e => log.throwable(s"Get clusters error: ${e.getMessage}", e))
          .mapBoth(GRPCStatus.fromThrowable, toClusterResponse)

    def deleteCluster(request: ClusterDetails): ZIO[Env, Status, ClusterResponse] =
      ClustersConfig
        .deleteCluster(request.id)
        .tapError(e => log.throwable(s"Delete cluster error: ${e.getMessage}", e))
        .mapBoth(GRPCStatus.fromThrowable, toClusterResponse)
  }
}
