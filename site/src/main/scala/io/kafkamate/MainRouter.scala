package io.kafkamate

import slinky.core.annotations.react
import slinky.core.FunctionalComponent
import slinky.core.ReactComponentClass
import slinky.reactrouter.Redirect
import slinky.reactrouter.Route
import slinky.reactrouter.Switch
import slinky.web.html._


import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@react object MainRouter {
  case class Props(appName: String)

  val component = FunctionalComponent[Props] { case Props(appName) =>
    val routerSwitch = Switch(
      Route(exact = true, path = Loc.home, component = KafkaMateApp.component),
      Route(exact = true, path = Loc.brokers, component = KafkaMateApp.component),
      Route(exact = true, path = Loc.topics, component = KafkaMateApp.component)
    )

    Layout(routerSwitch)
  }

  object Loc {
    val home           = "/"
    val brokers        = "/brokers"
    val topics         = "/topics"
  }
}
