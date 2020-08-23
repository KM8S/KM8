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
