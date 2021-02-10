package io.kafkamate
package messages

import scala.scalajs.js

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Link
import slinky.web.html._
import org.scalajs.dom.{Event, html}

import bridges.reactrouter.ReactRouterDOM
import common._

@react object ListMessages {
  type Props = Unit

  case class Item(offset: Long, partition: Int, timestamp: Long, key: String, value: String)
  case object Item {
    def fromMessage(m: Message): Item =
      Item(m.offset, m.partition, m.timestamp, m.key, m.value)
  }
  case class ConsumerState(
    streamData: Boolean = false,
    items: List[Item] = List.empty,
    maxResults: Long = 0L,
    offsetStrategy: String = "earliest",
    filterKeyword: String = ""
  )

  sealed trait ConsumerAction
  case object StreamToggle extends ConsumerAction
  case class SetMaxResults(maxResults: Long) extends ConsumerAction
  case class SetOffsetStrategy(strategy: String) extends ConsumerAction
  case class SetFilter(word: String) extends ConsumerAction
  case class AddItem(item: Item) extends ConsumerAction


  private def consumerReducer(prevState: ConsumerState, action: ConsumerAction): ConsumerState =
    action match {
      case StreamToggle =>
        if (prevState.streamData)
          prevState.copy(streamData = ! prevState.streamData)
        else
          prevState.copy(
            streamData = ! prevState.streamData,
            items = List.empty
          )
      case SetMaxResults(max) => prevState.copy(maxResults = max)
      case SetOffsetStrategy(v) => prevState.copy(offsetStrategy = v)
      case SetFilter(v) => prevState.copy(filterKeyword = v)
      case AddItem(item) => prevState.copy(items = prevState.items :+ item)
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

    def handleOffsetStrategy(e: SyntheticEvent[html.Select, Event]): Unit = consumerDispatch(SetOffsetStrategy(e.target.value))
    def handleMaxResults(e: SyntheticEvent[html.Input, Event]): Unit = consumerDispatch(SetMaxResults(e.target.value.toLong))
    def handleFilter(e: SyntheticEvent[html.Input, Event]): Unit = consumerDispatch(SetFilter(e.target.value))

    def onMessage(v: Message): Unit = consumerDispatch(AddItem(Item.fromMessage(v)))
    def onError(t: Throwable): Unit = consumerDispatch(StreamToggle) //todo display an error
    val onCompleted = () => consumerDispatch(StreamToggle)

    useEffect(
      () => {
        if (consumerState.streamData)
          consumer.start(
            ConsumeRequest(
              clusterId,
              topicName,
              consumerState.maxResults,
              consumerState.offsetStrategy,
              consumerState.filterKeyword
            )
          )(onMessage, onError, onCompleted)
        else
          consumer.stop()

        /** This is an example on how to clean up the effect */
        () => consumer.stop()
      },
      List(consumerState.streamData)
    )

    div(className := "App")(
      div(className := "container", h1(topicName)),
      br(),
      div(className := "container table-responsive",
        div(className := "mb-3",
          label(className := "inline")(
            div(
              span(className := "badge badge-default")("Offset Strategy"),
              select(
                className := "form-control",
                id := "form-cleanupPolicy-label1",
                onChange := (handleOffsetStrategy(_))
              )(
                option(value := "earliest")("earliest"),
                option(value := "latest")("latest")
              )
            )
          ),
          label(className := "inline")(
            div(className := "pl-2",
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
            div(className := "pl-2",
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
            div(className := "pl-3",
              if (!consumerState.streamData)
                button(className:= "btn btn-success fa fa-play", onClick := { () => consumerDispatch(StreamToggle) })(" Read")
              else
                button(className:= "btn btn-danger fa fa-stop", onClick := { () => consumerDispatch(StreamToggle) })(" Stop")
            )
          ),
          a(`type` := "button",
            style := js.Dynamic.literal(marginTop = "29px", float = "right"),
            className := "btn btn-secondary fa fa-plus",
            href := s"#${Loc.fromTopicAdd(clusterId, topicName)}", target := "_blank")(" Add new message")
        ),
        table(className := "table table-hover",
          thead(
            tr(
              th("Nr."),
              th("Offset"),
              th("Partition"),
              th("Timestamp"),
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
                td(item.timestamp.toString), //todo display it nicer
                td(item.key),
                td(item.value)
              )
            }
          )
        )
      )
    )
  }
}
