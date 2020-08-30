package io.kafkamate

import io.grpc.ManagedChannel
import io.grpc.stub.{ClientCallStreamObserver, StreamObserver}
import scalapb.grpc.Channels
import slinky.core._
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

object KafkaMateApp {
  private val css = AppCSS

  case class Props(name: String)

  private val grpcChannel = Channels.grpcwebChannel("http://localhost:8081")
  private val mateGrpcClient = KafkaMateServiceGrpcWeb.stub(grpcChannel)

  case class Consumer(channel: ManagedChannel) {
    private var stream: ClientCallStreamObserver = _

    private def responseObs: StreamObserver[Message] = new StreamObserver[Message] {
      def onNext(value: Message): Unit =
        println(s"Message consumed: $value")

      def onError(throwable: Throwable): Unit = {
        println(s"Failed consuming messages: ${throwable.getMessage}")
        stop()
      }

      def onCompleted(): Unit = {
        println("Finished consuming messages!")
        stop()
      }
    }

    def start(): Unit =
      stream =
        if (stream == null) {
          println("Starting to read the stream...")
          mateGrpcClient.consumeMessages(Request("test", "key", "value"), responseObs)
        } else {
          println("Stream already started!")
          stream
        }

    def stop(): Unit =
      if (stream == null) println("Stream already stopped!")
      else {
        stream.cancel()
        stream = null
        println("Stream canceled!")
      }
  }

  private val consumer = Consumer(grpcChannel)

  val component = FunctionalComponent[Props] { case Props(name) =>
    val (state, updateState) = useState(0)

    def produceMessage() =
      mateGrpcClient
        .produceMessage(Request("test", "key", "value"))
        .onComplete {
          case Success(v) => updateState(state + 1); println("Message produced: " + v)
          case Failure(e) => updateState(state - 1); println("Error producing message: " + e)
        }

    div(className := "App")(
      header(className := "App-header")(
        img(src := ReactLogo.asInstanceOf[String], className := "App-logo", alt := "logo"),
        h1(className := "App-title")("Welcome to KafkaMate!")
      ),
      br(),
      button(onClick := { () => produceMessage() })(s"Produce random message!"),
      p(className := "App-intro")(s"Produced $state messages!"),
      br(),
      button(onClick := { () => consumer.start() })(s"Stream data to console!"),
      button(onClick := { () => consumer.stop() })(s"Close stream!")
    )
  }
}
