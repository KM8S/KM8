package io.kafkamate

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}
import scala.scalajs.LinkingInfo
import slinky.core._
import slinky.web.ReactDOM
import slinky.hot
import org.scalajs.dom
//import zio._

@JSImport("resources/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

/**
 * Don't forget to start envoyproxy docker
 * docker run -d -v "$(pwd)"/common/src/main/resources/envoy.yaml:/etc/envoy/envoy.yaml:ro --network=host envoyproxy/envoy:v1.15.0
 */
object Main /*extends App*/ {
  private val css = IndexCSS

  /*def run(args: List[String]): URIO[ZEnv, ExitCode] =
    ZIO(main()).exitCode*/

  @JSExportTopLevel("main")
  def main(): Unit = {
    if (LinkingInfo.developmentMode) {
      hot.initialize()
    }

    val container = Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }

    ReactDOM.render(KafkaMateApp.component(KafkaMateApp.Props(name = "Master")), container)
  }
}
