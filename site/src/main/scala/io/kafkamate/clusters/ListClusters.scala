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

@react object ListClusters {
  type Props = Unit

  case class BrokersState(items: List[ClusterDetails] = List.empty)

  sealed trait BrokersAction
  case class NewItems(items: List[ClusterDetails] = List.empty) extends BrokersAction

  private def brokersReducer(state: BrokersState, action: BrokersAction): BrokersState =
    action match {
      case NewItems(items) => if (items.isEmpty) state.copy(items = List(ClusterDetails(name = "No clusters"))) else state.copy(items = items)
    }

  private val topicsGrpcClient =
    ClustersServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  val component = FunctionalComponent[Props] { _ =>
    val (brokersState, topicDispatch) = useReducer(brokersReducer, BrokersState())

    useEffect(
      () => {
        topicsGrpcClient
          .getClusters(ClusterRequest())
          .onComplete {
            case Success(v) => topicDispatch(NewItems(v.brokers.toList))
            case Failure(e) => topicDispatch(NewItems(List(ClusterDetails("Could not get clusters.")))); println("Error receiving brokers: " + e)
          }
      },
      List.empty
    )

    div(className := "App")(
      Link(to = Loc.addCluster)(div(className:= "btn btn-primary")("Add cluster")),
      div(className := "container card-body table-responsive",
        table(className := "table table-hover",
          thead(
            tr(
              th("Id"),
              th("Name"),
              th("Address")
            )
          ),
          tbody(
            brokersState.items.zipWithIndex.map { case (cluster, idx) =>
              tr(key := idx.toString)(
                td(Link(to = Loc.fromLocation(cluster.id, Loc.topics))(cluster.id)),
                td(cluster.name),
                td(cluster.address)
              )
            }
          )
        )
      )
    )
  }
}
