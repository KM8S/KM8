package io.kafkamate
package messages

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import org.scalajs.dom.{Event, html}
import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import bridges.reactrouter.ReactRouterDOM
import common._

@react object ProduceMessage {
  type Props = Unit

  private val messagesGrpcClient =
    MessagesServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  val component = FunctionalComponent[Props] { _ =>
    val (shouldMakeRequest, setRequestAction) = useState(false)
    val (messageKey, setKey)                  = useState("")
    val (messageValue, setValue)              = useState("")
    val (successMsgs, setSuccessMsgs)         = useState(Option.empty[String])
    val (errorMsgs, setErrorMsgs)             = useState(Option.empty[String])

    val params    = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")
    val topicName = params.getOrElse(Loc.topicNameKey, "")

    def handleKey(e: SyntheticEvent[html.Input, Event]): Unit      = setKey(e.target.value)
    def handleValue(e: SyntheticEvent[html.TextArea, Event]): Unit = setValue(e.target.value)

    def handleSubmit(e: SyntheticEvent[html.Form, Event]) = {
      e.preventDefault()
      if (messageKey.nonEmpty || messageValue.nonEmpty)
        setRequestAction(true)
    }

    useEffect(
      () =>
        if (shouldMakeRequest)
          messagesGrpcClient
            .produceMessage(ProduceRequest(clusterId, topicName, messageKey, messageValue))
            .onComplete {
              case Success(_) =>
                Util.logMessage("Message produced")
                setRequestAction(false)
                setSuccessMsgs(Some(s"Message produced for key: $messageKey"))

              case Failure(e) =>
                Util.logMessage("Error producing message: " + e)
                setRequestAction(false)
                setErrorMsgs(Some(e.getMessage))
            },
      List(shouldMakeRequest)
    )

    def addMessageForm() =
      form(
        onSubmit := (handleSubmit(_)),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "key", id := "form-key-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "key",
            aria - "label" := "key",
            aria - "describedby" := "form-username-label",
            value := messageKey,
            onChange := (handleKey(_))
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "value", id := "form-value-label")
          ),
          textarea(
            className := "form-control",
            placeholder := "value",
            aria - "label" := "value",
            aria - "describedby" := "form-password-label",
            value := messageValue,
            onChange := (handleValue(_))
          )
        ),
        successMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-success", role := "alert", msg)
        },
        errorMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-secondary", "Add")
      )

    div(className := "App")(
      h1(topicName),
      br(),
      div(
        className := "card",
        div(className := "card-header", "Add message"),
        div(
          className := "card-body",
          addMessageForm()
        )
      )
    )
  }
}
