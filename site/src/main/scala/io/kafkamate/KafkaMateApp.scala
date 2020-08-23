package io.kafkamate

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import slinky.core._
import slinky.core.facade.Hooks._
import slinky.web.html._

@JSImport("resources/App.css", JSImport.Default)
@js.native
object AppCSS extends js.Object

@JSImport("resources/logo.svg", JSImport.Default)
@js.native
object ReactLogo extends js.Object

object KafkaMateApp {
  private val css = AppCSS

  case class Props(name: String)

  /*private val clientLayer = io.kafkamate.ZioKafkamate.KafkaMateServiceClient.live(
    scalapb.zio_grpc.ZManagedChannel(scalapb.grpc.Channels.grpcwebChannel("http://localhost:9000"))
  )*/

//  private val cl = KafkaMateServiceGrpcWeb.stub(scalapb.grpc.Channels.grpcwebChannel("http://localhost:9000"))

  val component = FunctionalComponent[Props] { case Props(name) =>
    val (state, updateState) = useState(0)
    div(className := "App")(
      header(className := "App-header")(
        img(src := ReactLogo.asInstanceOf[String], className := "App-logo", alt := "logo"),
        h1(className := "App-title")("Welcome to KafkaMate!")
      ),
      br(),
      button(onClick := { () => updateState(state + 1) })(s"Click me, $name!"),
      p(className := "App-intro")(s"The button has been clicked $state times!")
    )
  }
}
