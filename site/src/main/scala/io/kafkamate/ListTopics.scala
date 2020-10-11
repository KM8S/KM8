package io.kafkamate

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import io.topics._

@react object ListTopics {
  type Props = Unit

  case class TopicsState(items: List[TopicDetails] = List.empty)

  sealed trait TopicsAction
  case class NewItems(items: List[TopicDetails] = List.empty) extends TopicsAction

  private def topicsReducer(state: TopicsState, action: TopicsAction): TopicsState =
    action match {
      case NewItems(items) => state.copy(items = items)
    }

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  val component = FunctionalComponent[Props] { _ =>
    val (listState, topicDispatch) = useReducer(topicsReducer, TopicsState())

    useEffect(
      () => {
        topicsGrpcClient
          .getTopics(TopicRequest("test"))
          .onComplete {
            case Success(v) => topicDispatch(NewItems(v.topics.toList))
            case Failure(e) => topicDispatch(NewItems(List(TopicDetails("Could not get topics.")))); println("Error receiving topics: " + e)
          }
      },
      List.empty
    )

    div(className := "App")(
      div(className := "container card-body table-responsive",
        table(className := "table table-hover",
          thead(
            tr(
              th("Name"),
              th("Partitions"),
              th("Replication")
            )
          ),
          tbody(
            listState.items.zipWithIndex.map { case (item, idx) =>
              tr(key := idx.toString)(
                td(item.name),
                td(item.partitions.toString),
                td(item.replication.toString)
              )
            }
          )
        )
      )
    )
  }
}
