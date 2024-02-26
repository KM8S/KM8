package io.kafkamate
package topics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import io.kafkamate.bridges.reactrouter.ReactRouterDOM
import io.kafkamate.common._
import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Link
import slinky.web.html._

@react object ListTopics {
  type Props = Unit

  case class TopicsState(
    refresh: Boolean = true,
    topics: List[TopicDetails] = List.empty,
    listingError: Option[String] = None)

  sealed trait TopicsEvent
  case object RefreshEvent extends TopicsEvent
  case class SetTopicsEvent(items: List[TopicDetails]) extends TopicsEvent
  case class SetListingErrorEvent(err: String) extends TopicsEvent

  private def topicsReducer(state: TopicsState, action: TopicsEvent): TopicsState =
    action match {
      case RefreshEvent              => state.copy(refresh = true, listingError = None)
      case SetTopicsEvent(topics)    => state.copy(topics = topics, refresh = false)
      case SetListingErrorEvent(err) => state.copy(listingError = Some(err), refresh = false)
    }

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GrpcHost))

  val component = FunctionalComponent[Props] { _ =>
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")

    val (topicsState, dispatchEvent) = useReducer(topicsReducer, TopicsState())

    useEffect(
      () =>
        if (topicsState.refresh)
          topicsGrpcClient
            .getTopics(GetTopicsRequest(clusterId))
            .onComplete {
              case Success(v) =>
                dispatchEvent(SetTopicsEvent(v.topics.toList))
              case Failure(e) =>
                Util.logMessage("Error receiving topics: " + e)
                dispatchEvent(SetListingErrorEvent("Could not load topics!"))
            },
      List(topicsState.refresh)
    )

    def renderTable =
      div(
        className := "card-body table-responsive",
        Link(to = Loc.fromLocation(clusterId, Loc.addTopic))(div(className := "btn btn-primary mb-3")("Add topic")),
        table(
          className := "table table-hover",
          thead(
            tr(
              th("Name"),
              th("Partitions"),
              th("Replication factor"),
              th("Cleanup Policy"),
              th("Retention (ms)"),
              th("Size (bytes)"),
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
                td(topicDetails.retentionMs),
                td(topicDetails.size.toString),
                td(renderDelete(idx.toString, topicDetails))
              )
            }
          )
        )
      )

    def renderDelete(idx: String, topicDetails: TopicDetails) = {
      val body = div(
        p(s"Are you sure you want to delete ${topicDetails.name} topic?"),
        p("Keep in mind that the topic will be deleted eventually, not immediately!")
      )
      DeleteItemModal.component(
        DeleteItemModal.Props(
          idx,
          topicDetails.name,
          body,
          () => topicsGrpcClient.deleteTopic(DeleteTopicRequest(clusterId, topicDetails.name)),
          () => dispatchEvent(RefreshEvent)
        )
      )
    }

    div(className := "App")(
      Loader.render(
        topicsState.refresh,
        topicsState.listingError,
        renderTable
      )
    )
  }
}
