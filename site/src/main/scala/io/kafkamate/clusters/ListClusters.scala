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
    items: List[ClusterDetails] = List.empty,
    refresh: Boolean = true,
    error: Option[String] = None,
    toDelete: Option[String] = None
  )

  sealed trait BrokersAction
  case class SetItems(items: List[ClusterDetails] = List.empty) extends BrokersAction
  case class SetError(e: String) extends BrokersAction
  case class SetDeleteItem(id: String) extends BrokersAction

  private def clustersReducer(state: ClustersState, action: BrokersAction): ClustersState =
    action match {
      case SetItems(items) => state.copy(items = items, refresh = false, error = None)
      case SetError(e) => state.copy(items = List.empty, refresh = false, error = Some(e))
      case SetDeleteItem(id) => state.copy(toDelete = Some(id))
    }

  private val clustersGrpcClient =
    ClustersServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val (clustersState, clustersDispatch) = useReducer(clustersReducer, ClustersState())

    useEffect(
      () => {
        clustersGrpcClient
          .getClusters(ClusterRequest())
          .onComplete {
            case Success(v) => clustersDispatch(SetItems(v.brokers.toList))
            case Failure(e) => clustersDispatch(SetError("Could not get clusters!")); println("Error receiving clusters: " + e)
          }
      },
      List.empty
    )

    useEffect(
      () => {
        if (clustersState.toDelete.isDefined)
          clustersGrpcClient
            .deleteCluster(ClusterDetails(clustersState.toDelete.get))
            .onComplete {
              case Success(v) => clustersDispatch(SetItems(v.brokers.toList))
              case Failure(e) => println("Error deleting cluster: " + e) //todo err
            }
      },
      List(clustersState.toDelete)
    )

    def renderClusters =
      div(className := "container card-body table-responsive",
        Link(to = Loc.addCluster)(div(className:= "btn btn-primary mb-3")("Add cluster")),
        table(className := "table table-hover",
          thead(
            tr(
              th("Id"),
              th("Name"),
              th("Address"),
              th("Action")
            )
          ),
          tbody(
            clustersState.items.zipWithIndex.map { case (cluster, idx) =>
              tr(key := idx.toString)(
                td(Link(to = Loc.fromLocation(cluster.id, Loc.topics))(cluster.id)),
                td(cluster.name),
                td(cluster.address),
                td(button(className:= "btn btn-danger fa", onClick := { () => clustersDispatch(SetDeleteItem(cluster.id)) })("Delete"))
              )
            }
          )
        )
      )

    div(className := "App")(
      Loader.render(
        clustersState.refresh,
        clustersState.error,
        renderClusters
      )
    )
  }
}
