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
    val (schemas, setSchemas)                 = useState(Seq.empty[SchemaSubject])
    val (schemaId, setSchemaId)               = useState(0)
    val (schemaUrl, setSchemaUrl)             = useState("")
    val (messageFormat, setMessageFormat)     = useState(MessageFormat.STRING.name)
    val (valueDescriptor, setValueDescriptor) = useState("")
    val (successMsgs, setSuccessMsgs)         = useState(Option.empty[String])
    val (errorMsgs, setErrorMsgs)             = useState(Option.empty[String])

    val params    = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")
    val topicName = params.getOrElse(Loc.topicNameKey, "")

    def handleMessageFormat(e: SyntheticEvent[html.Select, Event]): Unit = setMessageFormat(e.target.value)
    def handleSchemaId(e: SyntheticEvent[html.Select, Event]): Unit = {
      val id = e.target.value.toInt
      setSchemaId(id)
      setSchemaUrl(schemas.collectFirst { case s if s.id == id => s.url }.getOrElse(""))
    }
    def handleKey(e: SyntheticEvent[html.Input, Event]): Unit             = setKey(e.target.value)
    def handleValue(e: SyntheticEvent[html.TextArea, Event]): Unit        = setValue(e.target.value)
    def handleValueDescriptor(e: SyntheticEvent[html.Input, Event]): Unit = setValueDescriptor(e.target.value)

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
                messageFormat match {
                  case MessageFormat.PROTOBUF.name =>
                    ProduceRequest.Request.ProtoRequest(
                      ProduceProtoRequest(
                        clusterId = clusterId,
                        topicName = topicName,
                        key = messageKey,
                        value = messageValue,
                        schemaId = schemaId,
                        valueSubject = s"$topicName-value",
                        valueDescriptor = Option.when(valueDescriptor.nonEmpty)(valueDescriptor)
                      )
                    )
                  case _ =>
                    ProduceRequest.Request.StringRequest(
                      ProduceStringRequest(
                        clusterId = clusterId,
                        topicName = topicName,
                        key = messageKey,
                        value = messageValue
                      )
                    )
                }
              )
            )
            .onComplete {
              case Success(_) =>
                Util.logMessage("Message produced")
                setRequestAction(false)
                setSuccessMsgs(Some(s"Message produced for key: $messageKey"))
                setErrorMsgs(None)

              case Failure(e) =>
                Util.logMessage("Error producing message: " + e)
                setRequestAction(false)
                setSuccessMsgs(None)
                setErrorMsgs(Some(e.getMessage))
            },
      List(shouldMakeRequest)
    )

    useEffect(
      () =>
        if (messageFormat == MessageFormat.PROTOBUF.name)
          messagesGrpcClient
            .getSchemaSubject(
              GetSchemaSubjectRequest(
                clusterId = clusterId,
                topicName = topicName
              )
            )
            .onComplete {
              case Success(response) =>
                Util.logMessage("Schema retrieved")
                setSchemas(response.versions)
                response.versions.headOption.foreach { s =>
                  setSchemaId(s.id)
                  setSchemaUrl(s.url)
                }
                setErrorMsgs(None)

              case Failure(e) =>
                Util.logMessage("Error retrieving schemas: " + e)
                setSuccessMsgs(None)
                setErrorMsgs(Some(e.getMessage))
            },
      List(messageFormat)
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
        messageFormat match {
          case MessageFormat.PROTOBUF.name =>
            Some(
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
                  schemas.map(s => option(key := s"id-${s.id}", value := s.id.toString)(s"id ${s.id}"))
                ),
                a(
                  `type` := "button",
                  className := "btn btn-secondary",
                  href := s"$schemaUrl",
                  target := "_blank"
                )("View")
              )
            )
          case _ => None
        },
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
        messageFormat match {
          case MessageFormat.PROTOBUF.name =>
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
            )
          case _ => None
        },
        successMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-success", role := "alert", msg)
        },
        errorMsgs.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        },
        button(`type` := "submit", className := "btn btn-primary", "Publish")
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
