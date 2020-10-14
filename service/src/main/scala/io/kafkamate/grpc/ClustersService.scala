package io.kafkamate
package grpc

import io.grpc.Status
import zio.{ZEnv, ZIO}

import config._, ClustersConfig._
import clusters._

object ClustersService {
  type Env = ZEnv with ClustersConfigService

  object GrpcService extends ZioClusters.RClustersService[Env] {
    def addCluster(request: ClusterDetails): ZIO[Env, Status, ClusterDetails] =
      ClustersConfig
        .writeClusters(ClusterProperties(List(ClusterSettings("id1", request.name, List(request.address)))))
        .tapError(e => zio.UIO(println(s"---------------------- Add cluster error: ${e.getMessage}")))
        .bimap(Status.fromThrowable, _ => request)

    def getClusters(request: ClusterRequest): ZIO[Env, Status, ClusterResponse] =
      ClustersConfig
        .readClusters
        .tapError(e => zio.UIO(println(s"---------------------- Got clusters error: ${e.getMessage}")))
        .bimap(
          Status.fromThrowable,
          r => ClusterResponse(r.clusters.map(c => ClusterDetails(c.id, c.name, c.kafkaHosts.mkString(","))))
        )
  }
}
