package io.kafkamate

import scala.scalajs.js.annotation.{JSExportTopLevel, JSImport}
import scala.scalajs.{LinkingInfo, js}

import io.kafkamate.bridges.reactrouter.HashRouter
import io.kafkamate.common.Router
import org.scalajs.dom
import slinky.hot
import slinky.web.ReactDOM

@JSImport("resources/index.css", JSImport.Default)
@js.native
object IndexCSS extends js.Object

object Main {
  private val css = IndexCSS

  @JSExportTopLevel("main")
  def main(): Unit = {
    if (LinkingInfo.developmentMode) hot.initialize()

    val container = Option(dom.document.getElementById("root")).getOrElse {
      val elem = dom.document.createElement("div")
      elem.id = "root"
      dom.document.body.appendChild(elem)
      elem
    }

    ReactDOM.render(HashRouter(Router(Router.Props("KafkaMate"))), container)
  }
}
