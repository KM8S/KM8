package io.kafkamate
package messages

import scala.scalajs.js
import scala.scalajs.js.Date

import io.kafkamate.bridges.reactrouter.ReactRouterDOM
import io.kafkamate.common._
import io.kafkamate.messages.MessageFormat
import org.scalajs.dom.{Event, html}
import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

@react object ListMessages {
  type Props = Unit

  case class Item(
    offset: Long,
    partition: Int,
    timestamp: Long,
    key: String,
    valueFormat: MessageFormat,
    valueSchemaId: Option[Int],
    value: String)

  case object Item {

    def fromMessage(m: LogicMessage): Item =
      Item(
        offset = m.offset,
        partition = m.partition,
        timestamp = m.timestamp,
        key = m.key.getOrElse("<empty>"),
        valueFormat = m.valueFormat,
        valueSchemaId = m.valueSchemaId,
        value = m.value.getOrElse("<empty>")
      )

  }

  case class ConsumerState(
    isStreaming: Boolean = true,
    items: List[Item] = List.empty,
    error: Option[String] = None,
    maxResults: Long = 100,
    offsetStrategy: OffsetStrategy = OffsetStrategy.LATEST,
    filterKeyword: String = "",
    messageFormat: MessageFormat = MessageFormat.AUTO)

  sealed trait ConsumerEvent
  case class SetStreamingEvent(bool: Boolean, error: Option[String] = None) extends ConsumerEvent
  case class SetMaxResultsEvent(maxResults: Long) extends ConsumerEvent
  case class SetOffsetStrategyEvent(strategy: String) extends ConsumerEvent
  case class SetMessageFormat(messageFormat: String) extends ConsumerEvent
  case class SetFilterEvent(word: String) extends ConsumerEvent
  case class AddItemEvent(item: Item) extends ConsumerEvent

  private def consumerReducer(prevState: ConsumerState, event: ConsumerEvent): ConsumerState =
    event match {
      case SetStreamingEvent(bool, err) =>
        prevState.copy(
          isStreaming = bool,
          items = if (bool) List.empty else prevState.items,
          error = err
        )
      case SetMaxResultsEvent(max) =>
        prevState.copy(maxResults = max)
      case SetOffsetStrategyEvent(v) =>
        prevState.copy(offsetStrategy = OffsetStrategy.fromName(v).getOrElse(OffsetStrategy.LATEST))
      case SetMessageFormat(v) =>
        prevState.copy(messageFormat = MessageFormat.fromName(v).getOrElse(MessageFormat.STRING))
      case SetFilterEvent(v)  => prevState.copy(filterKeyword = v)
      case AddItemEvent(item) => prevState.copy(items = (prevState.items :+ item).sortBy(-_.timestamp))
    }

  private val messagesGrpcClient =
    MessagesServiceGrpcWeb.stub(Channels.grpcwebChannel(Config.GRPCHost))

  private val consumer =
    MessagesConsumer(messagesGrpcClient)

  val component = FunctionalComponent[Props] { _ =>
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")
    val topicName = params.getOrElse(Loc.topicNameKey, "")

    val (consumerState, consumerDispatch) = useReducer(consumerReducer, ConsumerState())

    def handleMessageFormat(e: SyntheticEvent[html.Select, Event]): Unit =
      consumerDispatch(SetMessageFormat(e.target.value))
    def handleOffsetStrategy(e: SyntheticEvent[html.Select, Event]): Unit =
      consumerDispatch(SetOffsetStrategyEvent(e.target.value))
    def handleMaxResults(e: SyntheticEvent[html.Input, Event]): Unit =
      consumerDispatch(SetMaxResultsEvent(e.target.value.toLong))
    def handleFilter(e: SyntheticEvent[html.Input, Event]): Unit = consumerDispatch(SetFilterEvent(e.target.value))

    def onMessage(v: LogicMessage): Unit = consumerDispatch(AddItemEvent(Item.fromMessage(v)))
    def onError(t: Throwable): Unit =
      consumerDispatch(SetStreamingEvent(false, Some(t.getMessage)))
    val onCompleted = () => consumerDispatch(SetStreamingEvent(false))

    useEffect(
      () => {
        if (consumerState.isStreaming)
          consumer.start(
            ConsumeRequest(
              clusterId,
              topicName,
              consumerState.maxResults,
              consumerState.offsetStrategy,
              consumerState.filterKeyword,
              consumerState.messageFormat
            )
          )(onMessage, onError, onCompleted)
        else
          consumer.stop()

        () => consumer.stop()
      },
      List(consumerState.isStreaming)
    )

    div(className := "App")(
      div(className := "container", h1(topicName)),
      br(),
      div(
        className := "container table-responsive",
        div(
          className := "mb-3",
          label(className := "inline")(
            div(
              span(className := "badge badge-default")("Message Format"),
              select(
                className := "form-control",
                id := "form-message-format-label1",
                onChange := (handleMessageFormat(_))
              )(
                MessageFormat.values.map(m => option(value := m.name)(m.name))
              )
            )
          ),
          label(className := "inline")(
            div(
              className := "pl-2",
              span(className := "badge badge-default")("Offset Strategy"),
              select(
                className := "form-control",
                id := "form-cleanupPolicy-label1",
                onChange := (handleOffsetStrategy(_))
              )(
                OffsetStrategy.values.map(o => option(value := o.name)(o.name))
              )
            )
          ),
          label(className := "inline")(
            div(
              className := "pl-2",
              span(className := "badge badge-default")("Max results (0 == Inf)"),
              input(
                `type` := "number",
                className := "form-control",
                id := "max-results-input-id",
                min := "0",
                max := "5000000",
                value := consumerState.maxResults.toString,
                onChange := (handleMaxResults(_))
              )
            )
          ),
          label(className := "inline")(
            div(
              className := "pl-2",
              span(className := "badge badge-default")("Filter (empty == all)"),
              input(
                `type` := "text",
                className := "form-control",
                placeholder := "keyword",
                value := consumerState.filterKeyword,
                onChange := (handleFilter(_))
              )
            )
          ),
          label(className := "inline")(
            div(
              className := "pl-3",
              if (!consumerState.isStreaming)
                button(
                  className := "btn btn-success fa fa-play",
                  onClick := { () => consumerDispatch(SetStreamingEvent(true)) }
                )(" Read")
              else
                button(
                  className := "btn btn-danger fa fa-stop",
                  onClick := { () => consumerDispatch(SetStreamingEvent(false)) }
                )(" Stop")
            )
          ),
          a(
            `type` := "button",
            style := js.Dynamic.literal(marginTop = "29px", float = "right"),
            className := "btn btn-primary fa fa-plus",
            href := s"#${Loc.fromTopicAdd(clusterId, topicName)}",
            target := "_blank"
          )(" Add new message")
        ),
        table(
          className := "table table-hover",
          thead(
            tr(
              th("Nr."),
              th("Offset"),
              th("Partition"),
              th("Timestamp"),
              th("Value Format"),
              th("Key"),
              th("Value")
            )
          ),
          tbody(
            consumerState.items.zipWithIndex.map { case (item, idx) =>
              tr(key := idx.toString)(
                th(idx.toString),
                td(item.offset.toString),
                td(item.partition.toString),
                td(new Date(item.timestamp).toUTCString()),
                td(s"${item.valueFormat.toString}${item.valueSchemaId.map(id => s" (id $id)").getOrElse("")}"),
                td(item.key),
                td(item.value)
              )
            }
          )
        ),
        consumerState.error.zipWithIndex.map { case (msg, idx) =>
          div(key := idx.toString, className := "alert alert-danger", role := "alert", msg)
        }
      )
    )
  }
}
