package io.kafkamate
package clusters

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Link
import slinky.web.html._

import common._

@react object ListClusters {
  type Props = Unit

  case class ClustersState(
    refresh: Boolean = true,
    clusters: List[ClusterDetails] = List.empty,
    listingError: Option[String] = None
  )

  sealed trait ClustersEvent
  case object RefreshEvent                                 extends ClustersEvent
  case class SetClustersEvent(items: List[ClusterDetails]) extends ClustersEvent
  case class SetListingErrorEvent(e: String)               extends ClustersEvent

  private def clustersReducer(state: ClustersState, action: ClustersEvent): ClustersState =
    action match {
      case RefreshEvent            => state.copy(refresh = true, listingError = None)
      case SetClustersEvent(items) => state.copy(clusters = items, refresh = false, listingError = None)
      case SetListingErrorEvent(e) => state.copy(clusters = List.empty, refresh = false, listingError = Some(e))
    }

  private val clustersGrpcClient =
    ClustersServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val (clustersState, clustersDispatch) = useReducer(clustersReducer, ClustersState())

    useEffect(
      () =>
        if (clustersState.refresh)
          clustersGrpcClient
            .getClusters(ClusterRequest())
            .onComplete {
              case Success(v) =>
                clustersDispatch(SetClustersEvent(v.brokers.toList))
              case Failure(e) =>
                Util.logMessage("Error receiving clusters: " + e)
                clustersDispatch(SetListingErrorEvent("Could not get clusters!"))
            },
      List(clustersState.refresh)
    )

    def renderDelete(idx: String, clusterDetails: ClusterDetails) = {
      val body = div(
        p(s"Are you sure you want to delete ${clusterDetails.name} cluster from kafkamate?")
      )
      DeleteItemModal.component(
        DeleteItemModal.Props(
          idx,
          clusterDetails.name,
          body,
          () => clustersGrpcClient.deleteCluster(ClusterDetails(clusterDetails.id)),
          () => clustersDispatch(RefreshEvent)
        )
      )
    }

    def renderClusters =
      div(
        className := "container card-body table-responsive",
        Link(to = Loc.addCluster)(div(className := "btn btn-primary mb-3")("Add cluster")),
        table(
          className := "table table-hover",
          thead(
            tr(
              th("Id"),
              th("Name"),
              th("Kafka Hosts"),
              th("Schema Registry URL"),
              th("Action")
            )
          ),
          tbody(
            clustersState.clusters.zipWithIndex.map { case (cluster, idx) =>
              tr(key := idx.toString)(
                td(Link(to = Loc.fromLocation(cluster.id, Loc.topics))(cluster.id)),
                td(cluster.name),
                td(cluster.kafkaHosts),
                td(cluster.schemaRegistryUrl),
                td(renderDelete(idx.toString, cluster))
              )
            }
          )
        )
      )

    div(className := "App")(
      Loader.render(
        clustersState.refresh,
        clustersState.listingError,
        renderClusters
      )
    )
  }
}
