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

  case class TopicsState(
    refreshPage: Boolean = false,
    topics: List[TopicDetails] = List.empty,
    deleteTopic: Option[String] = None
  )

  sealed trait TopicsAction
  case class NewTopics(items: List[TopicDetails] = List.empty) extends TopicsAction
  case class SetDeleteTopic(name: String) extends TopicsAction
  case object ShouldRefresh extends TopicsAction

  private def topicsReducer(state: TopicsState, action: TopicsAction): TopicsState =
    action match {
      case NewTopics(topics) => state.copy(topics = topics)
      case SetDeleteTopic(name) => state.copy(deleteTopic = Some(name))
      case ShouldRefresh => state.copy(refreshPage = !state.refreshPage, deleteTopic = None)
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
          .getTopics(GetTopicsRequest(clusterId))
          .onComplete {
            case Success(v) => topicDispatch(NewTopics(v.topics.toList))
            case Failure(e) => topicDispatch(NewTopics(List(TopicDetails("Could not get topics.")))); println("Error receiving topics: " + e)
          }
      },
      List(topicsState.refreshPage)
    )

    useEffect(
      () => {
        topicsState.deleteTopic match {
          case None => ()
          case Some(name) =>
            topicsGrpcClient
              .deleteTopic(DeleteTopicRequest(clusterId, name))
              .onComplete {
                case Success(_) => topicDispatch(ShouldRefresh)
                case Failure(_) => () //todo
              }
        }
      },
      List(topicsState.deleteTopic)
    )

    div(className := "App")(
      div(className := "card-body table-responsive",
        Link(to = Loc.fromLocation(clusterId, Loc.addTopic))(div(className:= "btn btn-primary mb-3")("Add topic")),
        table(className := "table table-hover",
          thead(
            tr(
              th("Name"),
              th("Partitions"),
              th("Replication factor"),
              th("Cleanup Policy"),
              th("Action")
            )
          ),
          tbody(
            topicsState.topics.zipWithIndex.map { case (topicDetails, idx) =>
              tr(key := idx.toString)(
                td(Link(to = Loc.fromTopicList(clusterId, topicDetails.name))(topicDetails.name)),
                td(topicDetails.partitions.toString),
                td(topicDetails.replication.toString),
                td(topicDetails.cleanupPolicy),
                td(button(className:= "btn btn-danger fa", onClick := { () => topicDispatch(SetDeleteTopic(topicDetails.name)) })("Delete"))
              )
            }
          )
        )
      )
    )
  }
}
