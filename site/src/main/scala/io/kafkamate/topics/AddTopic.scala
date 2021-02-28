package io.kafkamate
package topics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import org.scalajs.dom.{Event, html}
import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Redirect
import slinky.web.html._

import bridges.reactrouter.ReactRouterDOM
import common._

@react object AddTopic {
  type Props = Unit

  private val topicsGrpcClient =
    TopicsServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val params    = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")

    val (shouldRedirect, setRedirect)         = useState(false)
    val (shouldMakeRequest, setRequestAction) = useState(false)
    val (topicName, setTopicName)             = useState("")
    val (partitions, setPartitions)           = useState(1)
    val (replication, setReplication)         = useState(1)
    val (cleanupPolicy, setCleanupPolicy)     = useState("delete")
    val (errorMsgs, setErrorMsgs)             = useState(List.empty[String])

    def handleTopicName(e: SyntheticEvent[html.Input, Event]): Unit      = setTopicName(e.target.value)
    def handlePartitions(e: SyntheticEvent[html.Input, Event]): Unit     = setPartitions(e.target.value.toInt)
    def handleReplication(e: SyntheticEvent[html.Input, Event]): Unit    = setReplication(e.target.value.toInt)
    def handleCleanupPolicy(e: SyntheticEvent[html.Select, Event]): Unit = setCleanupPolicy(e.target.value)

    def handleSubmit(e: SyntheticEvent[html.Form, Event]) = {
      e.preventDefault()
      setRequestAction(true)
    }

    useEffect(
      () =>
        if (shouldMakeRequest)
          topicsGrpcClient
            .addTopic(AddTopicRequest(clusterId, topicName, partitions, replication, cleanupPolicy))
            .onComplete {
              case Success(_) =>
                setRedirect(true)

              case Failure(e) =>
                setRequestAction(false)
                setErrorMsgs(List(e.getMessage))
            },
      List(shouldMakeRequest)
    )

    def addTopicForm() =
      form(
        onSubmit := (handleSubmit(_)),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "name", id := "form-topicname-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            value := topicName,
            onChange := (handleTopicName(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "partitions", id := "form-partitions-label")
          ),
          input(
            `type` := "number",
            className := "form-control",
            min := "1",
            max := "40",
            value := partitions.toString,
            onChange := (handlePartitions(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "replication", id := "form-replication-label")
          ),
          input(
            `type` := "number",
            className := "form-control",
            min := "1",
            max := "5",
            value := replication.toString,
            onChange := (handleReplication(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "cleanupPolicy", id := "form-cleanupPolicy-label1")
          ),
          select(
            className := "form-control",
            id := "form-cleanupPolicy-label2",
            onChange := (handleCleanupPolicy(_))
          )(
            option(value := "delete")("delete"),
            option(value := "compact")("compact")
          )
        ),
        errorMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-secondary", "Add")
      )

    def addTopic() =
      div(
        className := "container w-50 p-4",
        div(
          className := "card",
          div(className := "card-header", "Add topic"),
          div(
            className := "card-body",
            addTopicForm()
          )
        )
      )

    if (shouldRedirect)
      Redirect(to = Loc.fromLocation(clusterId, Loc.topics))
    else
      addTopic()
  }
}
