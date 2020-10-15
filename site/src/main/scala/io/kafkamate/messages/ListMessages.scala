package io.kafkamate
package messages

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import bridges.reactrouter.ReactRouterDOM

@react object ListMessages {
  private val css = AppCSS
//  case class Props(settings: String)
  type Props = Unit

  case class ProducerState(
    produceMessage: Option[ProduceMessage] = None,
    produced: Int = 0
  )

  case class Item(offset: Long, partition: Int, timestamp: Long, key: String, value: String)
  case object Item {
    def fromMessage(m: Message): Item =
      Item(m.offset, m.partition, m.timestamp, m.key, m.value)
  }
  case class ConsumerState(
    streamData: Option[Boolean] = None,
    items: List[Item] = List.empty
  )

  sealed trait ProducerAction
  case class ProduceMessage(key: String, value: String) extends ProducerAction
  case class UpdateProduced(value: Int) extends ProducerAction

  sealed trait ConsumerAction
  case object StreamDataOn extends ConsumerAction
  case object StreamDataOff extends ConsumerAction
  case class NewItem(item: Item) extends ConsumerAction

  private def consumerReducer(prevState: ConsumerState, action: ConsumerAction): ConsumerState =
    action match {
      case StreamDataOn =>
        prevState.copy(
          streamData = Some(true),
          items = List.empty
        )
      case StreamDataOff => prevState.copy(streamData = Some(false))
      case NewItem(item) => prevState.copy(items = prevState.items :+ item)
    }

  private def producerReducer(state: ProducerState, action: ProducerAction): ProducerState =
    action match {
      case m: ProduceMessage => state.copy(produceMessage = Some(m))
      case UpdateProduced(value) => state.copy(produced = state.produced + value, produceMessage = None)
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
    val (producerState, producerDispatch) = useReducer(producerReducer, ProducerState())

    useEffect(
      () => {
        if (consumerState.streamData.contains(true))
          consumer.start(ConsumeRequest(clusterId, topicName))(v => consumerDispatch(NewItem(Item.fromMessage(v))))

        if (consumerState.streamData.contains(false))
          consumer.stop()

        /** This is an example on how to clean up the effect */
        () => consumer.stop()
      },
      List(consumerState.streamData)
    )

    useEffect(
      () => {
        if (producerState.produceMessage.isDefined)
          mateGrpcClient
            .produceMessage(ProduceRequest(clusterId, topicName, producerState.produceMessage.get.key, producerState.produceMessage.get.value))
            .onComplete {
              case Success(v) => producerDispatch(UpdateProduced(1)); println("Message produced: " + v)
              case Failure(e) => producerDispatch(UpdateProduced(-1)); println("Error producing message: " + e)
            }
      },
      List(producerState.produceMessage)
    )

    div(className := "App")(
      h2(s"Topic $topicName"),
      br(),
      label(className := "inline")(button(className:= "btn btn-primary", onClick := { () => consumerDispatch(StreamDataOn) })(s"Stream data!")),
      label(className := "inline")(button(className:= "btn btn-danger", onClick := { () => consumerDispatch(StreamDataOff) })(s"Close stream!")),
      div(className := "container card-body table-responsive",
        table(className := "table table-hover",
          thead(
            tr(
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
