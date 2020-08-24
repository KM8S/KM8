package io.kafkamate

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import slinky.core._
import slinky.core.facade.Hooks._
import slinky.web.html._

import scalapb.grpc.Channels
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

  private val client =
    KafkaMateServiceGrpcWeb.stub(Channels.grpcwebChannel("http://localhost:8081"))

  val component = FunctionalComponent[Props] { case Props(name) =>
    val (state, updateState) = useState(0)

    def produceMessageFuture() =
      client
        .produceMessage(Request("test", "key", "value"))
        .onComplete {
          case Success(v) => updateState(state + 1); println("s-a dat: " + v)
          case Failure(e) => updateState(state - 1); println("cip err: " + e)
        }

    def produceMessageZIO() =
      zio.Runtime.default.unsafeRunAsync(
        zio.ZIO.fromFuture(implicit ec => client.produceMessage(Request("test", "key", "value")))
          .fold(
            e => {updateState(state - 1); println("cip err: " + e)},
            v => {updateState(state + 1); println("s-a dat: " + v)}
          )
      )(_ => ())

    div(className := "App")(
      header(className := "App-header")(
        img(src := ReactLogo.asInstanceOf[String], className := "App-logo", alt := "logo"),
        h1(className := "App-title")("Welcome to KafkaMate!")
      ),
      br(),
      button(onClick := { () => produceMessageZIO() })(s"Click me, ZIO $name!"),
      br(),
      button(onClick := { () => produceMessageFuture() })(s"Click me, Future $name!"),
      p(className := "App-intro")(s"The button has been clicked $state times!")
    )
  }
}
