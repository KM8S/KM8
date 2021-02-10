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
import common._

@react object ListBrokers {
  type Props = Unit

  case class BrokersState(
    refresh: Boolean = true,
    errors: Option[String] = None,
    items: List[BrokerDetails] = List.empty
  )

  sealed trait BrokersAction
  case class SetItems(items: List[BrokerDetails] = List.empty) extends BrokersAction
  case class SetError(e: String) extends BrokersAction

  private def brokersReducer(state: BrokersState, action: BrokersAction): BrokersState =
    action match {
      case SetItems(items) => state.copy(items = items, errors = None, refresh = false)
      case SetError(e) => state.copy(errors = Some(e), items = List.empty, refresh = false)
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
            case Success(v) => topicDispatch(SetItems(v.brokers.toList))
            case Failure(e) =>
              Util.logMessage("Error receiving brokers: " + e)
              topicDispatch(SetError("Could not load brokers!"))
          }
      },
      List.empty
    )

    def renderBrokers =
      div(className := "container card-body table-responsive",
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

    div(className := "App")(
      Loader.render(
        listState.refresh,
        listState.errors,
        renderBrokers
      )
    )
  }
}
