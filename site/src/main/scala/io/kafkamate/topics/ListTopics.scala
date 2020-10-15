package io.kafkamate
package topics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Link
import slinky.web.html._

import bridges.reactrouter.ReactRouterDOM

@react object ListTopics {
  type Props = Unit

  case class TopicsState(topics: List[TopicDetails] = List.empty)

  sealed trait TopicsAction
  case class NewTopics(items: List[TopicDetails] = List.empty) extends TopicsAction

  private def topicsReducer(state: TopicsState, action: TopicsAction): TopicsState =
    action match {
      case NewTopics(topics) => state.copy(topics = topics)
    }

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  val component = FunctionalComponent[Props] { _ =>
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")

    val (topicsState, topicDispatch) = useReducer(topicsReducer, TopicsState())

    useEffect(
      () => {
        topicsGrpcClient
          .getTopics(TopicRequest(clusterId))
          .onComplete {
            case Success(v) => topicDispatch(NewTopics(v.topics.toList))
            case Failure(e) => topicDispatch(NewTopics(List(TopicDetails("Could not get topics.")))); println("Error receiving topics: " + e)
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
            topicsState.topics.zipWithIndex.map { case (topicDetails, idx) =>
              tr(key := idx.toString)(
                td(Link(to = Loc.messagesPath(clusterId, topicDetails.name))(topicDetails.name)),
                td(topicDetails.partitions.toString),
                td(topicDetails.replication.toString)
              )
            }
          )
        )
      )
    )
  }
}
