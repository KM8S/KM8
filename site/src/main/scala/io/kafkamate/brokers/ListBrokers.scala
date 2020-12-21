package io.kafkamate
package brokers

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import bridges.reactrouter.ReactRouterDOM

@react object ListBrokers {
  type Props = Unit

  case class BrokersState(items: List[BrokerDetails] = List.empty)

  sealed trait BrokersAction
  case class NewItems(items: List[BrokerDetails] = List.empty) extends BrokersAction

  private def brokersReducer(state: BrokersState, action: BrokersAction): BrokersState =
    action match {
      case NewItems(items) => state.copy(items = items)
    }

  private val topicsGrpcClient =
    BrokersServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val (listState, topicDispatch) = useReducer(brokersReducer, BrokersState())
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")

    useEffect(
      () => {
        topicsGrpcClient
          .getBrokers(BrokerRequest(clusterId))
          .onComplete {
            case Success(v) => topicDispatch(NewItems(v.brokers.toList))
            case Failure(e) => topicDispatch(NewItems(List(BrokerDetails(-1)))); println("Error receiving brokers: " + e)
          }
      },
      List.empty
    )

    div(className := "App")(
      div(className := "container card-body table-responsive", //todo add cluster name
        table(className := "table table-hover",
          thead(
            tr(
              th("Id"),
              th("IsController")
            )
          ),
          tbody(
            listState.items.zipWithIndex.map { case (item, idx) =>
              tr(key := idx.toString)(
                td(item.id),
                td(item.isController.toString)
              )
            }
          )
        )
      )
    )
  }
}
