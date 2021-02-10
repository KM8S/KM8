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
import common._

@react object ListTopics {
  type Props = Unit

  case class DeleteTopicState(
    topicName: Option[String] = None,
    modalId: Option[String] = None,
    error: Option[String] = None
  )

  case class TopicsState(
    refresh: Boolean = true,
    topics: List[TopicDetails] = List.empty,
    listingError: Option[String] = None,
    deleteTopic: DeleteTopicState = DeleteTopicState()
  )

  sealed trait TopicsEvent
  case object RefreshEvent extends TopicsEvent
  case class SetTopicsEvent(items: List[TopicDetails] = List.empty) extends TopicsEvent
  case class SetListingErrorEvent(err: String) extends TopicsEvent
  case class SetToDeleteEvent(topicName: String, modalId: String) extends TopicsEvent
  case class CancelDeleteEvent(modalId: Option[String]) extends TopicsEvent
  case class SetDeleteErrorEvent(err: String) extends TopicsEvent

  private def topicsReducer(state: TopicsState, action: TopicsEvent): TopicsState =
    action match {
      case RefreshEvent => state.copy(refresh = true, listingError = None, deleteTopic = DeleteTopicState())
      case SetTopicsEvent(topics) => state.copy(topics = topics, refresh = false)
      case SetListingErrorEvent(err) => state.copy(listingError = Some(err), refresh = false)
      case SetToDeleteEvent(topicName, modalId) => state.copy(deleteTopic = DeleteTopicState(Some(topicName), Some(modalId)))
      case CancelDeleteEvent(maybeModalId) => state.copy(deleteTopic = DeleteTopicState(None, maybeModalId))
      case SetDeleteErrorEvent(err) => state.copy(deleteTopic = state.deleteTopic.copy(error = Some(err)))
    }

  /*private def toggleModal(modalId: String) =
    js.eval("$('" + s"#$modalId" + "').modal('toggle')")*/

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")

    val (topicsState, dispatchEvent) = useReducer(topicsReducer, TopicsState())

    useEffect(
      () => {
        if (topicsState.refresh)
          topicsGrpcClient
            .getTopics(GetTopicsRequest(clusterId))
            .onComplete {
              case Success(v) =>
                dispatchEvent(SetTopicsEvent(v.topics.toList))
              case Failure(e) =>
                Util.logMessage("Error receiving topics: " + e)
                dispatchEvent(SetListingErrorEvent("Could not load topics!"))
            }
      },
      List(topicsState.refresh)
    )

    useEffect(
      () => {
        topicsState.deleteTopic match {
          case DeleteTopicState(None, Some(modalId), _) =>
            DeleteModal.toggleModal(modalId)
            dispatchEvent(CancelDeleteEvent(None))
          case DeleteTopicState(Some(topicName), Some(modalId), None) =>
            topicsGrpcClient
              .deleteTopic(DeleteTopicRequest(clusterId, topicName))
              .onComplete {
                case Success(_) =>
                  DeleteModal.toggleModal(modalId)
                  dispatchEvent(RefreshEvent)
                case Failure(e) =>
                  Util.logMessage(s"Delete topic error: ${e.getMessage}")
                  dispatchEvent(SetDeleteErrorEvent(s"Could not delete topic $topicName"))
              }
          case _ => ()
        }
      },
      List(topicsState.deleteTopic)
    )

    def renderTable = {
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
    }

    def renderDelete(idx: String, topicDetails: TopicDetails) = {
      val body = div(
        p(s"Are you sure you want to delete ${topicDetails.name} topic?"),
        p("Keep in mind that the topic will be deleted eventually, not immediately!"),
      )
      DeleteModal.renderDeleteModal(
        idx,
        topicDetails.name,
        body,
        topicsState.deleteTopic.error,
        modalId => dispatchEvent(SetToDeleteEvent(topicDetails.name, modalId)),
        modalId => dispatchEvent(CancelDeleteEvent(Some(modalId)))
      )
    }

    /*def renderDelete(idx: String, topicDetails: TopicDetails) = {
      val modalId = s"topicModalNr$idx"
      div(
        button(
          className:= "btn btn-danger fa",
          data-"toggle" := "modal",
          data-"backdrop" := "static",
          data-"keyboard" := "false",
          data-"target" := s"#$modalId")("Delete"),
        div(className := "modal fade", id := modalId, role := "dialog",
          div(className := "modal-dialog modal-dialog-centered", role := "document",
            div(className := "modal-content",
              div(className :="modal-header",
                h5(className := "modal-title")(topicDetails.name)
              ),
              div(className := "modal-body")(
                p(s"Are you sure you want to delete ${topicDetails.name} topic?"),
                p("Keep in mind that the topic will be deleted eventually, not immediately!"),
                topicsState.deleteTopic.error.zipWithIndex.map {
                  case (msg, idx) =>
                    div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
                }
              ),
              div(className := "modal-footer")(
                button(className := "btn btn-secondary",
                  onClick := (() => dispatchEvent(CancelDeleteEvent(Some(modalId)))))("Cancel"),
                button(className := "btn btn-danger",
                  onClick := (() => dispatchEvent(SetToDeleteEvent(topicDetails.name, modalId))))("Delete")
              )
            )
          )
        )
      )
    }*/

    div(className := "App")(
      Loader.render(
        topicsState.refresh,
        topicsState.listingError,
        renderTable
      )
    )
  }
}
