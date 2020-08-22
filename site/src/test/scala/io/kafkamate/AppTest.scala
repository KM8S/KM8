package io.kafkamate

import slinky.web.ReactDOM
import org.scalajs.dom.document

import org.scalatest.funsuite.AnyFunSuite

class AppTest extends AnyFunSuite {
  test("Renders without crashing") {
    val div = document.createElement("div")
    ReactDOM.render(App.component(App.Props("Master")), div)
    ReactDOM.unmountComponentAtNode(div)
  }
}
