package io.kafkamate
package messages

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.reactrouter.Link
import slinky.web.html._
import bridges.reactrouter.ReactRouterDOM
import org.scalajs.dom.{Event, html}

@react object ListMessages {
  type Props = Unit

  case class Item(offset: Long, partition: Int, timestamp: Long, key: String, value: String)
  case object Item {
    def fromMessage(m: Message): Item =
      Item(m.offset, m.partition, m.timestamp, m.key, m.value)
  }
  case class ConsumerState(
    streamData: Boolean = false,
    items: List[Item] = List.empty
  )

  sealed trait ConsumerAction
  case object StreamToggle extends ConsumerAction
  case class NewItem(item: Item) extends ConsumerAction

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
      case NewItem(item) => prevState.copy(items = prevState.items :+ item)
    }

  private val mateGrpcClient =
    MessagesServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  private val consumer =
    Utils.KafkaMateServiceGrpcConsumer(mateGrpcClient)

  val component = FunctionalComponent[Props] { _ =>
    val params = ReactRouterDOM.useParams().toMap
    val clusterId = params.getOrElse(Loc.clusterIdKey, "")
    val topicName = params.getOrElse(Loc.topicNameKey, "")

    val (consumerState, consumerDispatch) = useReducer(consumerReducer, ConsumerState())

    useEffect(
      () => {
        if (consumerState.streamData)
          consumer.start(ConsumeRequest(clusterId, topicName))(v => consumerDispatch(NewItem(Item.fromMessage(v))))

        if (! consumerState.streamData)
          consumer.stop()

        /** This is an example on how to clean up the effect */
        () => consumer.stop()
      },
      List(consumerState.streamData)
    )

    div(className := "App")(
      h2(s"Topic $topicName"),
      br(),
      a(href := s"#${Loc.fromTopicAdd(clusterId, topicName)}", target := "_blank")(div(className:= "btn btn-primary float-right")("Add new message")),
      div(className := "mb-3",
        if (!consumerState.streamData)
          button(className:= "btn btn-success fa fa-play", onClick := { () => consumerDispatch(StreamToggle) })(" Read")
        else
          button(className:= "btn btn-danger fa fa-stop", onClick := { () => consumerDispatch(StreamToggle) })(" Stop")
      ),
      div(className := "container card-body table-responsive",
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
                td(item.timestamp.toString),
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
