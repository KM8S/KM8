package io.kafkamate

import scalapb.grpc.Channels
import slinky.core._
import slinky.core.annotations.react
import slinky.core.facade.Hooks._
import slinky.web.html._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

@JSImport("resources/App.css", JSImport.Default)
@js.native
object AppCSS extends js.Object

@JSImport("resources/logo.svg", JSImport.Default)
@js.native
object ReactLogo extends js.Object

@react object KafkaMateApp {
  private val css = AppCSS
//  case class Props(settings: String)
  type Props = Unit

  case class ProducerState(
    produceMessage: Option[ProduceMessage] = None,
    produced: Int = 0
  )

  case class Item(key: String, value: String)
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
  case class NewItem(key: String, value: String) extends ConsumerAction

  private def uuid: String = java.util.UUID.randomUUID.toString

  private def consumerReducer(state: ConsumerState, action: ConsumerAction): ConsumerState =
    action match {
      case StreamDataOn =>
        state.copy(
          streamData = Some(true),
          items = if (state.streamData.contains(true)) state.items else List.empty
        )
      case StreamDataOff => state.copy(streamData = Some(false))
      case NewItem(key, value) => state.copy(items = state.items :+ Item(key, value))
    }

  private def producerReducer(state: ProducerState, action: ProducerAction): ProducerState =
    action match {
      case m: ProduceMessage => state.copy(produceMessage = Some(m))
      case UpdateProduced(value) => state.copy(produced = state.produced + value, produceMessage = None)
    }

  private val mateGrpcClient =
    KafkaMateServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  private val consumer =
    Utils.KafkaMateServiceGrpcConsumer(mateGrpcClient)

  val component = FunctionalComponent[Props] { _ =>
    val (consumerState, consumerDispatch) = useReducer(consumerReducer, ConsumerState())
    val (producerState, producerDispatch) = useReducer(producerReducer, ProducerState())

    useEffect(
      () => {
        if (consumerState.streamData.contains(true))
          consumer.start(Request("test", "", ""))(v => {consumerDispatch(NewItem(v.key, v.value)); println(s"Got $v")})

        if (consumerState.streamData.contains(false))
          consumer.stop()

        /** This is an example on how to clean up the effect
         * () => consumer.stop()
         */
      },
      List(consumerState.streamData)
    )

    useEffect(
      () => {
        if (producerState.produceMessage.isDefined)
          mateGrpcClient
            .produceMessage(Request("test", producerState.produceMessage.get.key, producerState.produceMessage.get.value))
            .onComplete {
              case Success(v) => producerDispatch(UpdateProduced(1)); println("Message produced: " + v)
              case Failure(e) => producerDispatch(UpdateProduced(-1)); println("Error producing message: " + e)
            }
      },
      List(producerState.produceMessage)
    )

    div(className := "App")(
      /*header(className := "App-header")(
        img(src := ReactLogo.asInstanceOf[String], className := "App-logo", alt := "logo"),
        h1(className := "App-title")("Welcome to KafkaMate!")
      ),*/
      button(className:= "btn btn-success", onClick := { () => producerDispatch(ProduceMessage("ala", "bala")) })(s"Produce random message!"),
      p(className := "App-intro")(s"Produced ${producerState.produced} messages!"),
      br(),
      label(className := "inline")(button(className:= "btn btn-primary", onClick := { () => consumerDispatch(StreamDataOn) })(s"Stream data!")),
      label(className := "inline")(button(className:= "btn btn-danger", onClick := { () => consumerDispatch(StreamDataOff) })(s"Close stream!")),
      div(className := "container card-body table-responsive",
        table(className := "table table-hover",
          thead(
            tr(
              th("Key"),
              th("Value")
            )
          ),
          tbody(
            tr(key := "test1")(
              td("some key"),
              td("some value")
            ),
            tr(key := "test2")(
              td("some key 2"),
              td("some value 2")
            ),
            consumerState.items.zipWithIndex.map { case (item, idx) =>
              tr(key := idx.toString)(
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
