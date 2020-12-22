package io.kafkamate
package topics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import scala.scalajs.js

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
    topic$Modal: (String, String) = ("", "")
  )

  sealed trait TopicsAction
  case class NewTopics(items: List[TopicDetails] = List.empty) extends TopicsAction
  case class SetToDelete(name: String, id: String) extends TopicsAction
  case object ShouldRefresh extends TopicsAction

  private def topicsReducer(state: TopicsState, action: TopicsAction): TopicsState =
    action match {
      case NewTopics(topics) => state.copy(topics = topics)
      case SetToDelete(name, id) => state.copy(topic$Modal = (name, id))
      case ShouldRefresh => state.copy(refreshPage = !state.refreshPage, topic$Modal = ("", ""))
    }

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

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
        topicsState.topic$Modal match {
          case ("", _) => ()
          case (name, id) =>
            topicsGrpcClient
              .deleteTopic(DeleteTopicRequest(clusterId, name))
              .onComplete {
                case Success(_) =>
                  js.eval("$('" + s"#$id" + "').modal('toggle')")
                  topicDispatch(ShouldRefresh)
                case Failure(_) => () //todo
              }
        }
      },
      List(topicsState.topic$Modal)
    )

    def renderDelete(idx: String, topicDetails: TopicDetails) = {
      val modalId = s"modalNr$idx"
      div(
        button(className:= "btn btn-danger fa", data-"toggle" := "modal", data-"target" := s"#$modalId")("Delete"),
        div(className := "modal fade", id := modalId, role := "dialog",
          div(className := "modal-dialog modal-dialog-centered", role := "document",
            div(className := "modal-content",
              div(className :="modal-header",
                h5(className := "modal-title")(topicDetails.name)
              ),
              div(className := "modal-body")(
                p(s"Are you sure you want to delete ${topicDetails.name} topic?"),
                p("Keep in mind that the topic will be deleted eventually, not immediately!")
              ),
              div(className := "modal-footer")(
                button(className := "btn btn-secondary", data-"dismiss" := "modal")("Cancel"),
                button(className := "btn btn-danger",
                  onClick := (() => topicDispatch(SetToDelete(topicDetails.name, modalId))))("Delete")
              )
            )
          )
        )
      )
    }

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
                td(renderDelete(idx.toString, topicDetails))
              )
            }
          )
        )
      )
    )
  }
}
