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
    val (valueDescriptor, setValueDescriptor) = useState("")
    val (successMsgs, setSuccessMsgs)         = useState(Option.empty[String])
    val (errorMsgs, setErrorMsgs)             = useState(Option.empty[String])

    val params    = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")
    val topicName = params.getOrElse(Loc.topicNameKey, "")

    def handleMessageFormat(e: SyntheticEvent[html.Select, Event]): Unit  = () //setKey(e.target.value)
    def handleSchemaId(e: SyntheticEvent[html.Select, Event]): Unit       = () //setKey(e.target.value)
    def handleKey(e: SyntheticEvent[html.Input, Event]): Unit             = setKey(e.target.value)
    def handleValue(e: SyntheticEvent[html.TextArea, Event]): Unit        = setValue(e.target.value)
    def handleValueDescriptor(e: SyntheticEvent[html.Input, Event]): Unit = () //setValueDescriptor(e.target.value)

    def handleSubmit(e: SyntheticEvent[html.Form, Event]) = {
      e.preventDefault()
      if (messageKey.nonEmpty || messageValue.nonEmpty)
        setRequestAction(true)
    }

    useEffect(
      () =>
        if (shouldMakeRequest)
          messagesGrpcClient
            .produceMessage(
              ProduceRequest(
                clusterId = clusterId,
                topicName = topicName,
                key = messageKey,
                value = messageValue,
                messageFormat = MessageFormat.PROTOBUF,
                schemaId = 7,
                valueSubject = s"$topicName-value",
                valueDescriptor = Some("Quote")
              )
            )
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
            span(className := "input-group-text", "message format", id := "form-message-format-label0")
          ),
          select(
            className := "form-control",
            id := "form-message-format-label1",
            onChange := (handleMessageFormat(_))
          )(
            option(value := MessageFormat.STRING.name)(MessageFormat.STRING.name),
            option(value := MessageFormat.PROTOBUF.name)(MessageFormat.PROTOBUF.name)
          )
        ),
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "schema", id := "form-schemaId-label0")
          ),
          select(
            className := "form-control",
            id := "form-schemaId-label1",
            onChange := (handleSchemaId(_))
          )(
            option(value := "7")("topic1-value (id 7)"),
            option(value := "8")("topic2-value (id 8)")
          )
        ),
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
        div(
          className := "input-group mb-3",
          div(
            className := "input-group-prepend",
            span(className := "input-group-text", "value descriptor", id := "form-value-descriptor-label")
          ),
          input(
            `type` := "text",
            className := "form-control",
            placeholder := "optional descriptor",
            aria - "label" := "descriptor",
            aria - "describedby" := "form-username-label",
            value := valueDescriptor,
            onChange := (handleValueDescriptor(_))
          )
        ),
        successMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-success", role := "alert", msg)
        },
        errorMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-secondary", "Publish")
      )

    div(className := "App")(
      h1(topicName),
      br(),
      div(
        className := "card",
        div(className := "card-header", "Publish data!"),
        div(
          className := "card-body",
          addMessageForm()
        )
      )
    )
  }
}
